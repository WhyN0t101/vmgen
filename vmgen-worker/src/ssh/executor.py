import logging
import time
import paramiko

log = logging.getLogger(__name__)

class SSHExecutor:
    def __init__(
        self,
        host: str,
        username: str,
        password: str | None = None,
        key_path: str | None = None,
        pkey: paramiko.PKey | None = None,
    ):
        self.host = host
        self.username = username
        self.password = password
        self.key_path = key_path
        self.pkey = pkey           # in-memory key object (used for ephemeral cloudinit keys)
        self._client: paramiko.SSHClient | None = None

    def connect(self):
        self._client = paramiko.SSHClient()
        self._client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        self._client.connect(
            self.host,
            username=self.username,
            password=self.password,
            key_filename=self.key_path,
            pkey=self.pkey,
        )

    def disconnect(self):
        if self._client:
            self._client.close()
            self._client = None

    def __enter__(self):
        self.connect()
        return self

    def __exit__(self, *args):
        self.disconnect()

    def wait_for_ssh(self, timeout: int = 300, retry_interval: int = 10):
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                self.connect()
                log.info(f"SSH ready on {self.host}")
                return
            except Exception:
                log.debug(f"SSH not ready yet on {self.host}, retrying in {retry_interval}s")
                time.sleep(retry_interval)
        raise TimeoutError(f"SSH on {self.host} did not come up within {timeout}s")
    
    def execute(self, command: str) -> tuple[int, str, str]:
        _, stdout, stderr = self._client.exec_command(command)
        exit_code = stdout.channel.recv_exit_status()
        return exit_code, stdout.read().decode(), stderr.read().decode()
    
    def upload_file(self, local_path: str, remote_path: str):
        sftp = self._client.open_sftp()
        try:
            sftp.put(local_path, remote_path)
        finally:
            sftp.close()

    def execute_script(self, local_path: str, remote_path: str = "/tmp/vmgen_script.sh") -> tuple[int, str, str]:
        self.upload_file(local_path, remote_path)
        self.execute(f"chmod +x {remote_path}")
        return self.execute(remote_path)
