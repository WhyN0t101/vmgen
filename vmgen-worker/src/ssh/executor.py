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
        jump_host: str | None = None,
        jump_username: str | None = None,
        jump_key_path: str | None = None,
    ):
        self.host = host
        self.username = username
        self.password = password
        self.key_path = key_path
        self.pkey = pkey           # in-memory key object (used for ephemeral cloudinit keys)
        self.jump_host = jump_host
        self.jump_username = jump_username
        self.jump_key_path = jump_key_path
        self._client: paramiko.SSHClient | None = None
        self._jump_client: paramiko.SSHClient | None = None

    def _open_jump_channel(self):
        """Open a TCP channel through the jump host to self.host:22."""
        self._jump_client = paramiko.SSHClient()
        self._jump_client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        self._jump_client.connect(
            self.jump_host,
            username=self.jump_username,
            key_filename=self.jump_key_path,
        )
        return self._jump_client.get_transport().open_channel(
            "direct-tcpip", (self.host, 22), ("", 0)
        )

    def connect(self):
        sock = self._open_jump_channel() if self.jump_host else None
        self._client = paramiko.SSHClient()
        self._client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        self._client.connect(
            self.host,
            username=self.username,
            password=self.password,
            key_filename=self.key_path,
            pkey=self.pkey,
            sock=sock,
        )

    def disconnect(self):
        if self._client:
            self._client.close()
            self._client = None
        if self._jump_client:
            self._jump_client.close()
            self._jump_client = None

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
    
    def write_remote_file(self, content: str, remote_path: str, mode: str = "644") -> None:
        """Write content to a remote file by piping via stdin — value never appears in the command string."""
        log.debug(f"Writing {len(content)} bytes to {self.host}:{remote_path}")
        stdin, stdout, stderr = self._client.exec_command(
            f"sudo tee {remote_path} > /dev/null && sudo chmod {mode} {remote_path}"
        )
        stdin.write(content.encode())
        stdin.channel.shutdown_write()
        exit_code = stdout.channel.recv_exit_status()
        if exit_code != 0:
            error = stderr.read().decode().strip()
            raise RuntimeError(f"Failed to write {remote_path}: {error}")

    def execute_content(self, script_content: str) -> tuple[int, str, str]:
        """Execute script content by piping it to bash stdin — no local file or SFTP needed."""
        log.debug(f"Executing script content on {self.host} via bash stdin ({len(script_content)} bytes)")
        stdin, stdout, stderr = self._client.exec_command("bash -s")
        stdin.write(script_content.encode())
        stdin.channel.shutdown_write()
        exit_code = stdout.channel.recv_exit_status()
        log.debug(f"Script exited with code {exit_code} on {self.host}")
        return exit_code, stdout.read().decode(), stderr.read().decode()
