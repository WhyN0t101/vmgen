from proxmoxer import ProxmoxAPI
import hashlib
import paramiko
import time
import logging

log = logging.getLogger(__name__)

class ProxmoxClient:
    def __init__(self, config):
        self._config = config         
        user, token_name = config.token_id.split('!')
        self._px = ProxmoxAPI(
            config.host,
            user=user,
            token_name=token_name,
            token_value=config.token_secret,
            verify_ssl=config.verify_ssl,
            backend='https',
        )
        self._node = config.node

    def _node_api(self, node: str | None = None):
        """Shortcut so we don't repeat proxmox.nodes(node) everywhere."""
        return self._px.nodes(node or self._node)

    def _wait_for_task(self, task_id: str, node: str | None = None, timeout: int = 300):
        """
        Proxmox operations are async — they return a task ID immediately.
        We poll until the task finishes or we hit the timeout.
        """
        deadline = time.time() + timeout  # absolute timestamp, not a countdown

        while time.time() < deadline:
            status = self._px.nodes(node or self._node).tasks(task_id).status.get()
            if status.get('status') == 'stopped':
                if status.get('exitstatus') == 'OK':
                    return   # success
                raise RuntimeError(f"Task {task_id} failed: {status.get('exitstatus')}")
            time.sleep(2)

        raise TimeoutError(f"Task {task_id} timed out after {timeout}s")
    
    def vm_exists(self, vmid: int, node: str | None = None) -> bool:
        try:
            self._node_api(node).qemu(vmid).status.current.get()
            return True
        except Exception:
            return False
        
    def clone_vm(self, vmid: int, new_vmid: int, name: str, node: str | None = None):
        """Full clone (not linked) — independent copy so we can modify without touching template."""
        log.info(f"Cloning VM {vmid} → {new_vmid} (name: {name})...")
        task = self._node_api(node).qemu(vmid).clone.post(
            newid=new_vmid,
            name=name,
            full=1,     # full=1 means full clone, full=0 would be linked (shares disk)
        )
        self._wait_for_task(task, node, timeout=600)  # cloning a full disk takes time
        log.info(f"VM {new_vmid} cloned successfully")

    def configure_network(self, vmid: int, node: str | None = None):
        """Attach VM to the isolated build bridge (from config)."""
        log.info(f"Attaching VM {vmid} to isolated bridge {self._config.isolated_bridge}")
        self._node_api(node).qemu(vmid).config.put(
            net0=f"virtio,bridge={self._config.isolated_bridge}"
        )
    def verify_network_isolation(self, bridge: str) -> bool:
        """
        Static check — does this bridge exist and have NO gateway?
        An isolated bridge must have no route to the outside world.
        Called at worker startup, not per-job.
        """
        try:
            networks = self._px.nodes(self._node).network.get()
            # networks is a list of dicts, one per interface
            bridge_config = next(
                (n for n in networks if n.get('iface') == bridge),
                None   # default if not found
            )
            if bridge_config is None:
                log.error(f"Bridge {bridge} not found on node {self._node}")
                return False
            has_gateway = bool(bridge_config.get('gateway'))
            if has_gateway:
                log.error(f"Bridge {bridge} has a gateway — not isolated!")
            return not has_gateway
        except Exception as e:
            log.error(f"Failed to verify bridge {bridge}: {e}")
            return False

    def inject_cloudinit_key(self, vmid: int, ssh_user: str, public_key: str, node: str | None = None):
        log.info(f"Injecting ephemeral cloud-init SSH key for user '{ssh_user}' on VM {vmid}")
        """
        Inject an ephemeral SSH public key via the Proxmox cloud-init API.
        Must be called BEFORE start_vm — cloud-init only runs on first boot.
        public_key = full authorized_keys line, e.g. "ssh-ed25519 AAAA... comment"
        """
        import urllib.parse
        self._node_api(node).qemu(vmid).config.put(
            ciuser=ssh_user,
            sshkeys=urllib.parse.quote(public_key, safe=''),
            ipconfig0='ip=dhcp',
        )

    def get_vm_ip(self, vmid: int, node: str | None = None, timeout: int = 120) -> str:
        """
        Poll the QEMU guest agent until we get a non-loopback IPv4 address.
        Requires qemu-guest-agent installed and running inside the VM.
        """
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                result = self._node_api(node).qemu(vmid).agent('network-get-interfaces').get()
                for iface in result.get('result', []):
                    for addr in iface.get('ip-addresses', []):
                        if (addr.get('ip-address-type') == 'ipv4'
                                and not addr['ip-address'].startswith('127.')):
                            return addr['ip-address']
            except Exception:
                pass  # guest agent not ready yet — keep polling
            time.sleep(5)
        raise TimeoutError(f"Could not get IP for VM {vmid} via guest agent within {timeout}s")

    def get_vm_config(self, vmid: int, node: str | None = None) -> dict:
        return self._node_api(node).qemu(vmid).config.get()

    def start_vm(self, vmid: int, node: str | None = None):
        log.info(f"Starting VM {vmid}...")
        task = self._node_api(node).qemu(vmid).status.start.post()
        self._wait_for_task(task, node)
        log.info(f"VM {vmid} started")

    def stop_vm(self, vmid: int, node: str | None = None):
        log.info(f"Stopping VM {vmid}...")
        task = self._node_api(node).qemu(vmid).status.stop.post()
        self._wait_for_task(task, node, timeout=120)
        log.info(f"VM {vmid} stopped")

    def remove_network(self, vmid: int, node: str | None = None):
        """
        Remove ALL network interfaces before OVA export.
        Critical security step — exported VM must have no network config.
        """
        config = self.get_vm_config(vmid, node)

        # Find every key that starts with 'net' (net0, net1, net2...)
        net_devices = [k for k in config if k.startswith('net')]
        if not net_devices:
            log.info(f"VM {vmid} has no network interfaces to remove")
            return

        # Proxmox 'delete' param takes a comma-separated list of config keys to remove
        self._node_api(node).qemu(vmid).config.put(
            delete=','.join(net_devices)
        )

        # Verify — never trust that it worked
        final_config = self.get_vm_config(vmid, node)
        remaining = [k for k in final_config if k.startswith('net')]
        if remaining:
            raise RuntimeError(f"Failed to remove network interfaces: {remaining}")

        log.info(f"Removed {net_devices} from VM {vmid}")

    def delete_vm(self, vmid: int, node: str | None = None):
        """Delete VM — used in stage 16 (cleanup) and on failure."""
        log.info(f"Deleting VM {vmid}...")
        task = self._node_api(node).qemu(vmid).delete()
        self._wait_for_task(task, node)
        log.info(f"VM {vmid} deleted")

    def export_ova(self, vmid: int, dest_path: str, storage: str) -> str:
        """
        SSH into the Proxmox host, run qm exportovf to a temp dir there,
        then SFTP the resulting OVA to dest_path on the worker machine.
        Finally removes the temp file from Proxmox.

        dest_path is a LOCAL path on the worker (e.g. /var/lib/vmgen/exports/<job>/<name>.ova).
        Network must already be removed (stage 12) before calling this.
        """
        import os

        proxmox_tmp = f"/tmp/vmgen-export-{vmid}"
        proxmox_ova = f"{proxmox_tmp}/vm.ova"

        log.info(f"Exporting VM {vmid} on Proxmox host to {proxmox_ova}...")

        client = paramiko.SSHClient()  # paramiko imported at module level
        client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        try:
            client.connect(
                self._config.host,
                username=self._config.ssh_user,
                key_filename=self._config.ssh_key_path,
            )

            # Step 1: Create temp dir on Proxmox and run export
            self._ssh_run(client, f"mkdir -p {proxmox_tmp}")
            self._ssh_run(
                client,
                f"sudo qm exportovf {vmid} {proxmox_ova} --storage {storage}",
                timeout=1800,  # large disks can take a while
            )

            # Step 2: SFTP the OVA from Proxmox to the worker's local dest_path
            os.makedirs(os.path.dirname(dest_path), exist_ok=True)
            log.info(f"Transferring OVA from Proxmox to {dest_path}...")
            with client.open_sftp() as sftp:
                sftp.get(proxmox_ova, dest_path)
            log.info("OVA transfer complete")

        finally:
            # Step 3: Clean up temp dir on Proxmox
            self._ssh_run(client, f"rm -rf {proxmox_tmp}")
            client.close()

        return dest_path

    def _ssh_run(self, client, cmd: str, timeout: int = 300) -> str:
        """Run a command over an open SSH client, raise on non-zero exit. Returns stdout."""
        _, stdout, stderr = client.exec_command(cmd, timeout=timeout)
        output = stdout.read().decode()   # blocks until EOF; must be read before exit status
        exit_code = stdout.channel.recv_exit_status()
        if exit_code != 0:
            error = stderr.read().decode().strip()
            raise RuntimeError(f"SSH command failed (exit {exit_code}): {cmd!r} — {error}")
        return output

    def get_template_config_hash(self, vmid: int, node: str | None = None) -> str:
        """
        Compute SHA-256 of the template VM config via SSH.
        Uses 'qm config' output as a stable fingerprint of the template state.
        """
        client = paramiko.SSHClient()
        client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        try:
            client.connect(
                self._config.host,
                username=self._config.ssh_user,
                key_filename=self._config.ssh_key_path,
            )
            config_output = self._ssh_run(client, f"sudo qm config {vmid}")
            return hashlib.sha256(config_output.encode('utf-8')).hexdigest()
        finally:
            client.close()