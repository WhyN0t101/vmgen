import io
import hashlib
import logging
import os
import shlex
import threading
import time
from datetime import datetime, timezone

import paramiko # type: ignore
from proxmox.client import ProxmoxClient
from ssh.executor import SSHExecutor
from worker.backend.client import BackendClient
from build.flags import hash_flag
from build.provenance import compute_sha256, assemble_provenance
from utils.path_safe import resolve_under_base, validate_guest_path, cleanup_export_dir

log = logging.getLogger(__name__)

_HASH_LABEL_PENDING = "PENDING"


def _verify_hash_match(label: str, expected: str, actual: str) -> None:
    if not expected or expected.upper() == _HASH_LABEL_PENDING:
        return
    if actual.lower() != expected.lower():
        raise RuntimeError(f"{label} SHA-256 mismatch: expected {expected}, got {actual}")


def _verify_content_hash(label: str, expected: str, content: str) -> None:
    if not expected or expected.upper() == _HASH_LABEL_PENDING:
        return
    actual = hashlib.sha256(content.encode()).hexdigest()
    _verify_hash_match(label, expected, actual)


class BuildPipeline:
    def __init__(self, config, proxmox: ProxmoxClient, backend: BackendClient):
        self._config = config
        self._proxmox = proxmox
        self._backend = backend
        self._build_vmid: int | None = None    # tracked so we can delete it on failure
        self._build_node: str | None = None    # tracked alongside vmid — needed for multi-node cleanup

    def run(self, job: dict) -> None:
        job_id = str(job["requestId"])
        build_started_at = datetime.now(timezone.utc).isoformat()

        try:
            self._run_stages(job, build_started_at)
        except Exception as e:
            log.error(f"Build {job_id} failed: {e}", exc_info=True)
            self._backend.append_log(job_id, "ERROR", f"Build failed: {e}")
            self._cleanup_vm()
            self._cleanup_export_artifacts(job_id)
            error_type = "proxmox_error"
            if "script" in str(e).lower():
                error_type = "script_failed"
            elif "timeout" in type(e).__name__.lower():
                error_type = "timeout"
            try:
                self._backend.fail_job(job_id, error_type, str(e))
            except Exception as report_err:
                log.error(f"[{job_id}] Failed to report failure to backend: {report_err}")

    def _run_stages(self, job: dict, build_started_at: str) -> None:
        job_id = str(job["requestId"])   # requestId IS the job ID
        template_vmid = job["proxmoxVmid"]
        template_id = job.get("templateId")
        template_sha256_known = job.get("templateSha256", "")
        proxmox_node = job.get("proxmoxNode")
        scripts = job["scripts"]         # [{scriptId, identifier, scriptContent, flagLocation}, ...]
        seed_hex = job["buildSeed"]
        ssh_auth = job["sshAuthMethod"]  # "cloudinit" | "password_reset" | "static_key"
        ssh_user = job["expectedSshUser"]
        disk_storage = job["diskStorage"]

        # Stage 1: VERIFY_TEMPLATE
        self._progress(job_id, "VERIFY_TEMPLATE", 5)
        if not self._proxmox.vm_exists(template_vmid, proxmox_node):
            raise RuntimeError(f"Template VM {template_vmid} not found")
        template_sha256 = self._proxmox.get_template_config_hash(template_vmid, proxmox_node)
        log.info(f"[{job_id}] Template config SHA-256: {template_sha256}")
        _verify_hash_match("Template", template_sha256_known, template_sha256)
        self._log(job_id, "INFO", f"Template VMID {template_vmid} verified (config SHA-256: {template_sha256})", "VERIFY_TEMPLATE")
        if template_id and template_sha256_known == "PENDING":
            self._backend.update_template_hash(template_id, template_sha256)

        # Stage 2: CLONE_TEMPLATE
        self._progress(job_id, "CLONE_TEMPLATE", 10)
        build_vmid = int(self._proxmox._px.cluster.nextid.get())
        self._build_vmid = build_vmid
        self._build_node = proxmox_node
        vm_name = f"vmgen-build-{job_id[:8]}"
        self._proxmox.clone_vm(template_vmid, build_vmid, vm_name, proxmox_node)
        self._log(job_id, "INFO", f"Cloned template {template_vmid} → build VM {build_vmid} ({vm_name})", "CLONE_TEMPLATE")

        # Stage 3: CONFIGURE_NETWORK
        self._progress(job_id, "CONFIGURE_NETWORK", 18)
        self._proxmox.configure_network(build_vmid, proxmox_node)

        # Adjust RAM / disk to satisfy the highest requirement across all selected scripts.
        # Scripts that don't specify a requirement contribute 0 and are ignored.
        required_ram  = max((s.get("minRamMb")  or 0 for s in scripts), default=0) or None
        required_disk = max((s.get("minDiskGb") or 0 for s in scripts), default=0) or None
        if required_ram is not None or required_disk is not None:
            self._proxmox.configure_resources(build_vmid, required_ram, required_disk, proxmox_node)
            self._log(job_id, "INFO",
                      f"VM resources adjusted — RAM: {required_ram or 'unchanged'} MB, "
                      f"disk: {required_disk or 'unchanged'} GB",
                      "CONFIGURE_NETWORK")

        # For cloud-init: inject ephemeral key BEFORE starting the VM
        ephemeral_pkey = None
        if ssh_auth == "cloudinit":
            ephemeral_pkey = paramiko.RSAKey.generate(4096)
            public_line = f"{ephemeral_pkey.get_name()} {ephemeral_pkey.get_base64()} worker-ephemeral"
            self._proxmox.inject_cloudinit_key(build_vmid, ssh_user, public_line, proxmox_node)

        # Stage 4: START_VM
        self._progress(job_id, "START_VM", 22)
        self._proxmox.start_vm(build_vmid, proxmox_node)

        # Stage 5: WAIT_FOR_SSH — discover IP via guest agent, then wait for SSH port
        self._progress(job_id, "WAIT_FOR_SSH", 27)
        vm_ip = self._proxmox.get_vm_ip(build_vmid, proxmox_node, timeout=120)
        log.info(f"VM {build_vmid} got IP: {vm_ip}")
        self._log(job_id, "INFO", f"VM {build_vmid} reachable at {vm_ip}", "WAIT_FOR_SSH")

        jump_kwargs = dict(
            jump_host=self._proxmox._config.host,
            jump_username=self._proxmox._config.ssh_user,
            jump_key_path=self._proxmox._config.ssh_key_path,
            jump_known_hosts_path=self._proxmox._config.known_hosts_path,
        )

        if ssh_auth == "cloudinit":
            ssh = SSHExecutor(host=vm_ip, username=ssh_user, pkey=ephemeral_pkey, **jump_kwargs)
        elif ssh_auth == "static_key":
            pkey = paramiko.PKey.from_private_key(io.StringIO(job["sshPrivateKey"]))
            ssh = SSHExecutor(host=vm_ip, username=ssh_user, pkey=pkey, **jump_kwargs)
        else:  # password_reset
            ssh = SSHExecutor(host=vm_ip, username=ssh_user,
                              password=job["sshPassword"], **jump_kwargs)

        ssh.wait_for_ssh(timeout=300)

        exit_code, stdout, _ = ssh.execute(f"id {ssh_user}")
        if exit_code != 0:
            raise RuntimeError(
                f"Expected SSH user '{ssh_user}' does not exist in the guest OS. "
                f"Ensure the template has this user or update the template's expectedSshUser field."
            )
        log.info(f"[{job_id}] Verified guest user '{ssh_user}' exists")

        # Stages 6-9 run over SSH — try/finally guarantees disconnect
        try:
            # Stage 6: EXECUTE_SCRIPTS
            self._progress(job_id, "EXECUTE_SCRIPTS", 45)
            scripts_executed = []

            total_scripts = len(scripts)
            for i, script in enumerate(scripts, start=1):
                script_id = str(script["scriptId"])
                log.info(f"[{job_id}] Executing script {i}/{total_scripts}: '{script['identifier']}' ({script_id})")
                self._log(job_id, "INFO", f"Executing script {i}/{total_scripts}: '{script['identifier']}' ({script_id})", "EXECUTE_SCRIPTS")

                _verify_content_hash(
                    f"Script '{script['identifier']}'",
                    script.get("sha256", ""),
                    script["scriptContent"],
                )

                # Substitute {{PARAM_NAME}} placeholders with resolved values, then
                # also export them as shell variables so $PARAM_NAME works too.
                param_values: dict = script.get("parameterValues") or {}
                content = script['scriptContent']
                if param_values:
                    for k, v in param_values.items():
                        content = content.replace("{{" + k + "}}", str(v))
                    exports = "\n".join(
                        f"export {k}={shlex.quote(str(v))}"
                        for k, v in param_values.items()
                    )
                    content = exports + "\n" + content

                t_start = time.time()
                exit_code, _, stderr = self._with_heartbeat(
                    job_id,
                    lambda c=content: ssh.execute_content(c),
                )
                duration = int(time.time() - t_start)

                if exit_code != 0:
                    self._log(job_id, "ERROR",
                              f"Script '{script['identifier']}' failed (exit {exit_code}): {stderr}",
                              "EXECUTE_SCRIPTS")
                    raise RuntimeError(
                        f"Script '{script['identifier']}' failed (exit {exit_code}): {stderr}"
                    )
                log.info(f"[{job_id}] Script '{script['identifier']}' completed in {duration}s")
                self._log(job_id, "INFO", f"Script '{script['identifier']}' completed in {duration}s", "EXECUTE_SCRIPTS")

                # Reset lastProgressAt after each script so the 10-minute silence timer
                # only fires if THIS script hangs, not the entire EXECUTE_SCRIPTS batch.
                script_done_pct = 45 + int(15 * i / total_scripts)
                self._progress(job_id, "EXECUTE_SCRIPTS", script_done_pct)

                scripts_executed.append({
                    "scriptId": script_id,
                    "scriptIdentifier": script["identifier"],
                    "exitCode": exit_code,
                    "durationSeconds": duration,
                })

            # Stage 7: GENERATE_FLAGS — derived server-side so PLATFORM_SECRET stays on the backend
            self._progress(job_id, "GENERATE_FLAGS", 60)
            flag_derivation_timestamp = datetime.now(timezone.utc).isoformat()

            secrets = self._backend.derive_build_secrets(job_id, flag_derivation_timestamp)
            flags_raw = {entry["scriptId"]: entry["rawFlag"] for entry in secrets["flags"]}
            vm_password = secrets["vmLoginPassword"]

            # Stage 8: PLACE_FLAGS
            self._progress(job_id, "PLACE_FLAGS", 65)
            for script in scripts:
                script_id = str(script["scriptId"])
                raw_flag = flags_raw[script_id]
                placement = validate_guest_path(script["flagLocation"], field_name="flagLocation")
                flag_mode = script.get("flagPermissions") or "644"
                flag_owner = script.get("flagOwnership")
                log.info(f"[{job_id}] Placing flag for '{script['identifier']}' at {placement}")
                ssh.write_remote_file(raw_flag, placement, mode=flag_mode, ownership=flag_owner)

                # Verify the flag actually landed — read it back and compare
                exit_code, readback, _ = ssh.execute(f"sudo cat {shlex.quote(placement)}")
                if exit_code != 0 or readback.strip() != raw_flag.strip():
                    raise RuntimeError(
                        f"Flag verification failed for '{script['identifier']}' at {placement}: "
                        f"file {'missing' if exit_code != 0 else 'content mismatch'}"
                    )
                log.info(f"[{job_id}] Flag verified at {placement}")

            # Stage 8.5: INSTALL_TELEMETRY — only when the build requested it
            if job.get("telemetryEnabled"):
                self._progress(job_id, "INSTALL_TELEMETRY", 67)
                from build.telemetry_script import TELEMETRY_COLLECTOR_SCRIPT
                ssh.write_remote_file(TELEMETRY_COLLECTOR_SCRIPT, "/usr/local/bin/collect-telemetry", mode="755")
                self._log(job_id, "INFO",
                          "Telemetry collector installed at /usr/local/bin/collect-telemetry",
                          "INSTALL_TELEMETRY")

            # Stage 9: SANITIZE_VM
            self._progress(job_id, "SANITIZE_VM", 70)
            ssh.execute("history -c && > /root/.bash_history")
            ssh.execute("rm -f /root/.ssh/authorized_keys")
            ssh.execute("rm -f /etc/ssh/ssh_host_*")
            ssh.execute("cloud-init clean --logs")
            # Prevent cloud-init from re-running when the OVA boots on the student's
            # hypervisor — without a Proxmox metadata source it would lock or reset
            # the vmgen-builder password that chpasswd just set above.
            ssh.execute("sudo touch /etc/cloud/cloud-init.disabled")
            ssh.execute(r"find /var/log -type f -exec truncate -s 0 {} \;")
            # Cap the network-wait timeout so boot doesn't hang indefinitely when
            # DHCP is slow (e.g. hypervisor NAT not ready at POST), while still
            # giving services like Apache time to wait for a real interface.
            ssh.execute(
                "sudo mkdir -p /etc/systemd/system/systemd-networkd-wait-online.service.d && "
                "printf '[Service]\\nTimeoutStartSec=15s\\n' | "
                "sudo tee /etc/systemd/system/systemd-networkd-wait-online.service.d/timeout.conf > /dev/null"
            )

            # Replace Proxmox-specific Netplan config (ens18/virtio) with a portable
            # wildcard config so DHCP works regardless of NIC driver after OVA import.
            portable_netplan = (
                "network:\\n"
                "  version: 2\\n"
                "  ethernets:\\n"
                "    id0:\\n"
                "      match:\\n"
                "        name: en*\\n"
                "      dhcp4: true"
            )
            ssh.execute("sudo rm -f /etc/netplan/*.yaml")
            ssh.execute(f"printf '{portable_netplan}' | sudo tee /etc/netplan/50-vmgen.yaml > /dev/null")

            exit_code, _, stderr = ssh.execute(f"echo '{ssh_user}:{vm_password}' | sudo chpasswd")
            if exit_code != 0:
                raise RuntimeError(f"chpasswd failed for user '{ssh_user}': {stderr}")

            exit_code, stdout, _ = ssh.execute(f"sudo passwd -S {ssh_user}")
            if exit_code == 0 and stdout.split()[1:2] != ["P"]:
                raise RuntimeError(
                    f"Password not set for '{ssh_user}' after chpasswd — "
                    f"passwd -S reports: {stdout.strip()}"
                )
            log.info(f"[{job_id}] VM login credential reset for user '{ssh_user}'")

            # Flush all pending writes to disk before we stop the VM.
            # chpasswd writes to /etc/shadow; without sync the write may still
            # be in the guest kernel's page cache when QEMU is killed, causing
            # the exported OVA to contain the old (template) password.
            ssh.execute("sync")

        finally:
            ssh.disconnect()

        # Stage 10: SHUTDOWN_VM
        self._progress(job_id, "SHUTDOWN_VM", 75)
        self._proxmox.stop_vm(build_vmid, proxmox_node)

        # Stage 11: REMOVE_NETWORK — critical, must happen before export
        self._progress(job_id, "REMOVE_NETWORK", 80)
        self._proxmox.remove_network(build_vmid, proxmox_node)

        # Stage 12: EXPORTING — this stage name triggers the BUILDING→EXPORTING state transition in the backend
        self._progress(job_id, "EXPORTING", 87)
        ova_path = str(resolve_under_base(self._config.export_path, job_id, f"{vm_name}.ova"))
        self._proxmox.export_ova(build_vmid, ova_path, storage=disk_storage)

        # Stage 13: COMPUTE_HASHES
        # NOTE: ova_path must be accessible from the worker machine (e.g. via NFS mount)
        self._progress(job_id, "COMPUTE_HASHES", 91)
        ova_checksum = compute_sha256(ova_path, allowed_base=self._config.export_path)
        ova_size = os.path.getsize(ova_path)
        self._log(job_id, "INFO",
                  f"OVA exported: {ova_path} ({ova_size // (1024 * 1024)} MB, SHA-256: {ova_checksum})",
                  "COMPUTE_HASHES")

        # Stage 14: ASSEMBLE_PROVENANCE
        self._progress(job_id, "ASSEMBLE_PROVENANCE", 94)
        build_finished_at = datetime.now(timezone.utc).isoformat()
        provenance = assemble_provenance(
            job=job,
            worker_id=self._config.worker.id,
            build_started_at=build_started_at,
            build_finished_at=build_finished_at,
            scripts_executed=scripts_executed,
            flag_derivation_timestamp=flag_derivation_timestamp,
            template_sha256=template_sha256,
        )

        # Stage 15: REGISTER_COMPLETION
        # Raw flags never leave the worker — only SHA-256(flag) is sent
        self._progress(job_id, "REGISTER_COMPLETION", 97)
        self._backend.complete_job(
            job_id=job_id,
            ova_path=ova_path,
            ova_checksum=ova_checksum,
            ova_size_bytes=ova_size,
            provenance=provenance,
            vm_login_password=vm_password,
            flags=[
                {
                    "scriptId": str(s["scriptId"]),
                    "flagValueHash": hash_flag(flags_raw[str(s["scriptId"])]),
                    "placementLocation": s["flagLocation"],
                }
                for s in scripts
            ],
        )

        # Stage 16: CLEANUP — no progress update here; job is already READY after complete_job
        self._proxmox.delete_vm(build_vmid, proxmox_node)
        self._build_vmid = None   # cleared — no longer needs error cleanup
        self._build_node = None
        self._log(job_id, "INFO", "Build complete — build VM deleted, all stages finished")
        log.info(f"Build {job_id} complete")

    def _with_heartbeat(self, job_id: str, fn):
        """Run fn() while pinging the backend every 60s for worker liveness.
        Uses ping_job() (not update_progress()) so lastProgressAt is NOT reset —
        the backend's 10-minute script-silence timer therefore fires correctly if a
        script hangs. lastProgressAt is only updated via _progress() on real stage
        transitions (i.e. after each script completes successfully)."""
        stop = threading.Event()

        def _beat():
            while not stop.wait(60):
                log.info(f"[{job_id}] Heartbeat — still executing script")
                self._backend.ping_job(job_id)

        t = threading.Thread(target=_beat, daemon=True)
        t.start()
        try:
            return fn()
        finally:
            stop.set()
            t.join()

    def _progress(self, job_id: str, stage: str, percent: int):
        log.info(f"[{percent}%] {stage}")
        self._backend.update_progress(job_id, stage, percent)

    def _log(self, job_id: str, level: str, message: str, stage: str | None = None):
        """Fire-and-forget build log — never raises, never blocks the build."""
        self._backend.append_log(job_id, level, message, stage)

    def _cleanup_vm(self):
        if self._build_vmid is not None:
            try:
                self._proxmox.delete_vm(self._build_vmid, self._build_node)
                log.info(f"Cleaned up VM {self._build_vmid}")
            except Exception as e:
                log.error(f"Cleanup failed for VM {self._build_vmid}: {e}")
            self._build_vmid = None
            self._build_node = None

    def _cleanup_export_artifacts(self, job_id: str) -> None:
        """Remove partial or failed export files from the worker export directory (NFR7)."""
        cleanup_export_dir(self._config.export_path, job_id)
