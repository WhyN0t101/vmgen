# Worker Pipeline — `main.py` + `pipeline.py` + `backend/client.py`

## What the worker is

A Python process that runs independently from the backend. No web server, no user interface.
It is a poll loop that claims build jobs and executes them autonomously.

Three files drive everything:
- **`main.py`** — poll loop, concurrency, startup checks, shutdown
- **`pipeline.py`** — the 16 stages that build a VM
- **`backend/client.py`** — all HTTP communication back to the Spring backend

---

## `main.py` — the poll loop

### Startup checks

```python
if not proxmox.verify_network_isolation(config.proxmox.isolated_bridge):
    logger.error("Bridge failed isolation check.")
    return 1
```

Verifies that `vmbr2` (the isolated Linux bridge on the Proxmox host, `PROXMOX_ISOLATED_BRIDGE`)
has no gateway configured at the host level. This check is host-config-only — it does not
and cannot verify guest-level isolation between build VMs and the platform (see below).

**What this means:**
`vmbr2` is a bridge that connects build VMs to each other and to `192.168.2.2`
(the Proxmox host's own interface on that bridge). If the *host's* interfaces file had a
gateway on this bridge, build VMs could bypass pfSense entirely and reach the internet
without any firewall rules applying — that's what this specific check guards against.

**What pfSense does separately:**
pfSense is a VM on `vmbr0` (school network), `vmbr1` (management), and `vmbr2` (BUILD).
Build VMs get `192.168.2.254` as their gateway via DHCP — a real gateway this time, since
build VMs live on their own subnet (`192.168.2.0/24`), distinct from the platform VM's
(`192.168.1.0/24`). All traffic through pfSense is filtered; build VMs can install packages
but cannot reach the worker/platform VM, exfiltrate data, or reach pfSense management.
This *requires* the subnet separation to be real: until 2026-08-13, build VMs and the
platform VM shared one flat subnet, so pfSense — sitting as a peer VM on that bridge, not
inline — never actually saw build→platform traffic to filter it. Fixed by giving BUILD its
own bridge/subnet so that traffic is genuinely routed through pfSense's gateway.

**Why refuse to start instead of warn:**
If the bridge has a gateway, build network isolation is broken. Running builds in that
state produces VMs with unknown network exposure. Refusing to start makes this a hard
invariant, not a soft warning.

---

### Concurrency with `ThreadPoolExecutor`

```python
executor = ThreadPoolExecutor(max_workers=config.worker.max_concurrent_builds)
active: dict = {}  # future -> job

while not shutdown_requested:
    if len(active) < config.worker.max_concurrent_builds:
        job = backend.claim_job()
        if job:
            pipeline = BuildPipeline(config, proxmox, backend)
            active[executor.submit(pipeline.run, job)] = job
        else:
            time.sleep(config.worker.poll_interval_seconds)
    else:
        time.sleep(5)
```

Each build runs in a separate thread. `executor.submit()` starts the pipeline on a thread
and returns a `Future` — a handle to the running task.

**Why threads and not processes:**
Builds are mostly I/O-bound — waiting for SSH commands, waiting for Proxmox API responses.
Threads are fine for I/O-bound work and share memory cheaply.

**Why track the `active` dict:**
Completed futures are removed each loop iteration. This is how the worker knows it has
capacity to claim another job.

---

### Graceful shutdown

```python
signal.signal(signal.SIGTERM, _handle_shutdown)
signal.signal(signal.SIGINT, _handle_shutdown)

GRACE_SECONDS = 8
_, still_running = concurrent.futures.wait(list(active.keys()), timeout=GRACE_SECONDS)
for future in still_running:
    backend.fail_job(job_id, "worker_shutdown", "Build did not finish within grace period")
```

On SIGTERM or Ctrl+C, the poll loop exits. Active builds get 8 seconds to finish.
Any that don't finish are reported as FAILED to the backend.

**Why 8 seconds:**
Docker sends SIGTERM then SIGKILL after 10 seconds. 8 seconds gives builds time to finish
and leaves 2 seconds to report failures before the hard kill.

**Why report failures on shutdown:**
Without this, in-progress builds would be stuck in BUILDING forever. The backend's stale
detection would eventually catch them (after 10 minutes), but reporting immediately is cleaner.

---

## `pipeline.py` — the 16 stages

### Error handling — the outer try/except

```python
def run(self, job: dict) -> None:
    try:
        self._run_stages(job, build_started_at)
    except Exception as e:
        self._cleanup_vm()
        self._cleanup_export_artifacts(job_id)
        self._backend.fail_job(job_id, error_type, str(e))
```

Any exception from any stage is caught here. Three things always happen on failure:
1. Delete the build VM (if it was created)
2. Delete any partial OVA files
3. Report FAILED to the backend

`self._build_vmid` is set to `None` after successful cleanup to prevent double-delete
if cleanup itself is called again.

---

### Stage 1 — VERIFY_TEMPLATE

```python
template_sha256 = self._proxmox.get_template_config_hash(template_vmid, proxmox_node)
_verify_hash_match("Template", template_sha256_known, template_sha256)
```

Computes SHA-256 of the Proxmox VM config file and compares to the stored expected hash.

**What can be modified in a template without booting it:**
Proxmox templates cannot be booted, but their configuration can still be changed:
- Memory size, CPU count
- Network adapter type and bridge assignment
- Disk configuration
- Cloud-init settings (default user, SSH keys)

Any of these changes would silently affect builds while the provenance record still claims
the original template version. The hash check catches this and fails the build.

**What the hash does NOT cover:**
Disk contents — files inside the VM image. Config-level integrity only, not disk-level.
This is the correct scope for detecting infrastructure drift and accidental reconfiguration.

If the stored hash is `"PENDING"` (first build on this template), the worker reports
the computed hash to the backend which stores it for future builds.

---

### Stage 2 — CLONE_TEMPLATE

```python
build_vmid = int(self._proxmox._px.cluster.nextid.get())
self._build_vmid = build_vmid   # stored immediately for cleanup on failure
self._proxmox.clone_vm(template_vmid, build_vmid, vm_name, proxmox_node)
```

Clones the template into a new VM. The template stays pristine and untouched —
it is available for the next build immediately. Only the clone is booted and modified.

`self._build_vmid` is stored right after creation so any failure from this point onwards
triggers VM deletion in the error handler.

---

### Stage 3 — CONFIGURE_NETWORK

Attaches the VM to the isolated bridge (`vmbr2`, via `PROXMOX_ISOLATED_BRIDGE`).

**Resource sizing:**
```python
required_ram  = max((s.get("minRamMb")  or 0 for s in scripts), default=0) or None
required_disk = max((s.get("minDiskGb") or 0 for s in scripts), default=0) or None
```

Takes the maximum requirement across all selected scripts. A script that compiles Apache
needs more RAM than one that edits a config file. The VM satisfies the most demanding script.

**Ephemeral SSH key (cloud-init auth method):**
```python
ephemeral_pkey = paramiko.RSAKey.generate(4096)
self._proxmox.inject_cloudinit_key(build_vmid, ssh_user, public_line, proxmox_node)
```

Fresh 4096-bit RSA key pair generated per build. Public key injected before VM starts.
Removed during sanitization — never exists in the final OVA.

**Why ephemeral per build:**
If a fixed key was used, compromising one build gives SSH access to every other build VM.
A fresh key per build makes each build's SSH access independent.

---

### Stages 4 & 5 — START_VM + WAIT_FOR_SSH

```python
vm_ip = self._proxmox.get_vm_ip(build_vmid, proxmox_node, timeout=120)
ssh.wait_for_ssh(timeout=300)
```

Waits up to 2 minutes for the guest agent to report an IP, then up to 5 minutes for SSH.

**Jump host pattern:**
```python
jump_kwargs = dict(
    jump_host=self._proxmox._config.host,   # <PROXMOX_PUBLIC_IP>
    jump_username=self._proxmox._config.ssh_user,
    jump_key_path=self._proxmox._config.ssh_key_path,
)
```

Build VMs are on the isolated internal network (192.168.1.x). The worker tunnels SSH
through the Proxmox host (<PROXMOX_PUBLIC_IP>) which has a leg on both networks.

---

### Stage 6 — EXECUTE_SCRIPTS

Three things per script:

**1 — Hash verification:**
```python
_verify_content_hash(f"Script '{script['identifier']}'", script.get("sha256"), script["scriptContent"])
```
SHA-256 of script content checked before execution. Prevents a tampered script from running
even if modified in transit or in the database.

**2 — Parameter substitution:**
```python
for k, v in param_values.items():
    content = content.replace("{{" + k + "}}", str(v))
exports = "\n".join(f"export {k}={shlex.quote(str(v))}" for k, v in param_values.items())
content = exports + "\n" + content
```

Parameter values come from the backend — resolved and validated at build submission time,
stored in `vm_request_script_parameters`, sent in the job spec.

The full resolution flow:
- User provides values in the UI → backend validates + auto-generates missing ones
- Backend stores resolved values in DB
- Worker receives them in `ClaimJobResponse.parameterValues`
- Worker substitutes them into script content

`shlex.quote()` safely escapes values for shell export. Without it, a value containing
a quote or semicolon could break out of the export statement and inject arbitrary shell commands.

**3 — Heartbeat during execution:**
```python
exit_code, _, stderr = self._with_heartbeat(job_id, lambda c=content: ssh.execute_content(c))
```

Runs the script while a background thread pings the backend every 60 seconds.
Uses `ping_job()` not `update_progress()` — `lastProgressAt` is NOT reset.
The backend's 10-minute stale detection still fires if the script hangs indefinitely.

---

### Stage 7 — GENERATE_FLAGS

```python
secrets = self._backend.derive_build_secrets(job_id, flag_derivation_timestamp)
flags_raw = {entry["scriptId"]: entry["rawFlag"] for entry in secrets["flags"]}
vm_password = secrets["vmLoginPassword"]
```

**The worker does not derive flags itself.** It asks the backend to derive them.

**Why:** The `PLATFORM_SECRET` — the HMAC key used in flag derivation — never leaves
the backend. If the worker derived flags locally, the secret would need to exist on the
worker machine, in config files, potentially in logs.

Centralising derivation on the backend means:
- One place holds the secret
- One place verifies flags (at completion)
- The worker is a less sensitive attack surface

---

### Stage 8 — PLACE_FLAGS

```python
ssh.write_remote_file(raw_flag, placement, mode=flag_mode, ownership=flag_owner)

exit_code, readback, _ = ssh.execute(f"sudo cat {shlex.quote(placement)}")
if exit_code != 0 or readback.strip() != raw_flag.strip():
    raise RuntimeError(f"Flag verification failed")
```

Writes each flag to its declared location then reads it back to verify.

**Why verify after writing:**
A failed write (permission error, full disk) might not raise an exception but produces
a VM with a missing flag. Reading back is the only confirmation the flag is actually there.

---

### Stage 9 — SANITIZE_VM

```python
ssh.execute("history -c && > /root/.bash_history")
ssh.execute("rm -f /root/.ssh/authorized_keys")
ssh.execute("rm -f /etc/ssh/ssh_host_*")
ssh.execute("cloud-init clean --logs")
ssh.execute("sudo touch /etc/cloud/cloud-init.disabled")
ssh.execute(r"find /var/log -type f -exec truncate -s 0 {} \;")
ssh.execute(f"echo '{ssh_user}:{vm_password}' | sudo chpasswd")
ssh.execute("sync")
```

Removes all traces of the build process:
- Shell history — commands run during script execution
- Authorized keys — the ephemeral worker SSH key
- SSH host keys — regenerated fresh when student boots the OVA
- Cloud-init logs and state — prevents re-run on student's hypervisor
- All log files — truncated

Resets the VM login password to the backend-derived `vmLoginPassword`.

**Why `sync` last:**
`chpasswd` writes to `/etc/shadow`. Without `sync`, the write may still be in the kernel's
page cache when the VM is stopped, resulting in the exported OVA containing the old
template password.

**Why sanitize:**
The OVA is given to a student. It must contain no build artifacts, no worker SSH keys,
no logs revealing how the vulnerability was installed.

---

### Stage 11 — REMOVE_NETWORK (critical)

```python
self._proxmox.remove_network(build_vmid, proxmox_node)
```

Removes the network interface from the VM configuration before export.

**Why critical:**
If exported with a NIC configured for `vmbr2`, the student's hypervisor would see an
adapter referencing a non-existent bridge. Removing it before export means the student's
hypervisor assigns a clean fresh adapter with working defaults.

---

### Stage 12 — EXPORTING

```python
self._progress(job_id, "EXPORTING", 77)
self._proxmox.export_ova(build_vmid, ova_path, storage=disk_storage)
```

The stage name `"EXPORTING"` triggers the `BUILDING → EXPORTING` state transition in the
backend — the backend reads this stage name and transitions state.

Runs `qemu-img` on the Proxmox host via SSH. Can take 20–30 minutes for large VMs —
this is why the exporting timeout is longer than the building timeout.

---

### Stage 13 — COMPUTE_HASHES

```python
ova_checksum = compute_sha256(ova_path, allowed_base=self._config.export_path)
```

SHA-256 of the exported OVA. Sent to the backend and shown to the user on the download
page — the user can verify the file was not corrupted in transit.

`allowed_base` is a path traversal guard — ensures the OVA path is inside the export
directory, preventing a malicious job from pointing to arbitrary filesystem paths.

---

### Stage 14 — ASSEMBLE_PROVENANCE

Assembles the full audit record:
- Template identifier, version, SHA-256
- Script identifiers, versions, SHA-256 hashes, exit codes, durations
- Parameter values used
- Build timestamps
- Worker ID

Stored in the backend as an append-only record. If a script is later updated or deleted,
the provenance still accurately describes what ran.

---

### Stage 15 — REGISTER_COMPLETION

```python
flags=[{
    "scriptId": ...,
    "flagValueHash": hash_flag(flags_raw[str(s["scriptId"])]),
}]
```

**Raw flags are never sent.** Only `SHA-256(raw_flag)` is sent.

The backend independently re-derives the same hash and verifies they match.
If they do not match, completion is rejected.

`complete_job` has a 3-attempt retry loop with 5-second delay — this is the most critical
call. If it fails, the backend never knows the build succeeded and the user never gets their OVA.

---

### Stage 16 — CLEANUP

```python
self._proxmox.delete_vm(build_vmid, proxmox_node)
self._build_vmid = None
```

Deletes the build VM. The OVA is already exported — the VM is no longer needed.
`self._build_vmid = None` signals to the error handler that there is nothing to clean up.

---

## `backend/client.py` — communication design

Every method sends `X-Worker-Api-Key` automatically. Every call has a 30-second timeout —
the backend being unreachable fails fast rather than hanging indefinitely.

**Non-fatal calls** (catch exceptions, log warning, never abort the build):
- `append_log` — a logging failure must not abort a build
- `heartbeat` — a missed heartbeat is not critical
- `ping_job` — liveness signal only
- `update_template_hash` — informational, idempotent

**Fatal calls** (raise on failure):
- `claim_job` — must know if a job was claimed
- `derive_build_secrets` — flags cannot be placed without this
- `complete_job` — 3-attempt retry, most critical call
- `fail_job` — must report failure so the backend can transition state

---

## Full worker flow

```
Worker starts
    → config validated (fail fast on missing secrets/SSH keys)
    → network isolation check (fail if vmbr2 has gateway at host level)
    → poll loop starts

Every loop iteration:
    → send heartbeat every 60s (CPU%, memory%, active job IDs)
    → if capacity available: POST /api/worker/jobs/claim
        → 204: no jobs, sleep and retry
        → 200: job claimed, start pipeline in new thread

Pipeline (16 stages):
    1.  VERIFY_TEMPLATE     — config hash check
    2.  CLONE_TEMPLATE      — template untouched, clone is the build target
    3.  CONFIGURE_NETWORK   — isolated bridge, resource sizing, ephemeral SSH key
    4.  START_VM            — boot the clone
    5.  WAIT_FOR_SSH        — discover IP, connect via jump host
    6.  EXECUTE_SCRIPTS     — hash verify → param substitute → run (with ping heartbeat)
    7.  GENERATE_FLAGS      — backend derives flags (PLATFORM_SECRET stays on backend)
    8.  PLACE_FLAGS         — write to VM, read back to verify
    9.  SANITIZE_VM         — remove history/keys/logs, reset password, sync
    10. SHUTDOWN_VM         — graceful stop
    11. REMOVE_NETWORK      — detach NIC (critical — must happen before export)
    12. EXPORTING           — qemu-img OVA export (triggers BUILDING→EXPORTING on backend)
    13. COMPUTE_HASHES      — SHA-256 of OVA file
    14. ASSEMBLE_PROVENANCE — snapshot of everything that ran
    15. REGISTER_COMPLETION — send flag hashes only, backend verifies
    16. CLEANUP             — delete build VM

On any failure:
    → delete build VM
    → delete partial OVA
    → report FAILED to backend with error type
```
