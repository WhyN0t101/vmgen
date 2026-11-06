# VMGen Operations Cheat Sheet

Practical commands for running and debugging the VMGen platform on the lab
infrastructure. Adjust IPs/paths if the deployment moves.

## Environment map

| Thing | Value |
|-------|-------|
| Platform VM | `192.168.1.10` (user `vmgen-platform`, repo `~/vmgen`, compose `~/vmgen/docker`) |
| Proxmox host | `192.168.1.2` (from the VM / vmbr1) · `<PROXMOX_PUBLIC_IP>` (from school net) |
| pfSense LAN | `192.168.1.254` (management network gateway/DNS) |
| pfSense BUILD | `192.168.2.254` (DNS + NTP for build VMs, since 2026-08-13) |
| Build VMs | `192.168.2.10`–`192.168.2.250` on `vmbr2` (DHCP, leases keyed on MAC) — was `vmbr1`/`192.168.1.100+` before 2026-08-13, isolation wasn't actually enforced on that layout |
| Worker SSH key | `~/.ssh/vmgen_worker` (ed25519) |
| Shared OVA export | host `/var/lib/vmgen-exports` → VM `/srv/vmgen-exports` → container `/var/lib/vmgen/exports` |

## Access

```bash
# SSH to the platform VM from a PC on the upstream network (jump through Proxmox)
ssh -J root@<PROXMOX_PUBLIC_IP> vmgen-platform@192.168.1.10

# SSH straight to the Proxmox host
ssh root@<PROXMOX_PUBLIC_IP>                 # from the upstream network
ssh vmgen@192.168.1.2                 # from the platform VM (worker user)

# SSH to a build VM (debugging) — through Proxmox as jump
ssh -J root@<PROXMOX_PUBLIC_IP> vmgen-builder@192.168.2.10

# Tunnel the platform UI to your PC (then browse http://localhost:8080)
ssh -L 8080:192.168.1.10:80 root@<PROXMOX_PUBLIC_IP>

# Tunnel the pfSense GUI (then browse https://localhost:8443)
ssh -L 8443:192.168.1.254:443 root@<PROXMOX_PUBLIC_IP>

# Copy changed files to the VM without re-cloning (run from PC PowerShell)
scp -J root@<PROXMOX_PUBLIC_IP> "<local-file>" vmgen-platform@192.168.1.10:/home/vmgen-platform/vmgen/<dest>
```

## Docker / platform stack (on the platform VM, in `~/vmgen/docker`)

```bash
# Bring up core only (no Proxmox needed)
docker compose up -d --build postgres vmgen-api vmgen-frontend

# Rebuild + restart a single service after a code change
docker compose up -d --build vmgen-worker
docker compose up -d --force-recreate vmgen-api vmgen-worker   # no rebuild, just env/volume changes

# Logs
docker compose logs -f vmgen-worker                 # follow
docker compose logs --tail=200 vmgen-worker         # last N
docker compose logs vmgen-worker | grep -iE 'ERROR|Traceback|session not active'

# Status / health
docker compose ps
docker stats --no-stream                            # per-container CPU/RAM

# Verify the rendered config picks up .env (e.g. shared export mode)
docker compose config | grep -B1 -A4 'vmgen/exports'

# Exec into a container
docker compose exec vmgen-worker sh
docker compose exec vmgen-api sh -c 'ls -la /var/lib/vmgen/exports'

# First-admin bootstrap (registration defaults to USER; promote in DB then re-login)
docker compose exec postgres psql -U vmgen -d vmgen \
  -c "UPDATE users SET role='ADMIN' WHERE email='you@example.com';"
```

## Database (postgres container)

```bash
# psql shell
docker compose exec postgres psql -U vmgen -d vmgen

# Inspect build states / storage paths (e.g. after a retention/export change)
docker compose exec postgres psql -U vmgen -d vmgen -c \
  "SELECT LEFT(id::text,8) AS job, state, storage_path IS NOT NULL AS has_file, created_at \
   FROM vm_requests ORDER BY created_at DESC LIMIT 10;"
```

## Proxmox (on the host, `192.168.1.2`)

```bash
# List / manage build VMs (clones are named vmgen-build-<job8>)
sudo qm list | grep vmgen-build
sudo qm config <vmid>
sudo qm stop <vmid> && sudo qm destroy <vmid>     # clean up an orphan

# Confirm worker token / sudoers works (run as the worker user)
sudo -u vmgen ssh ... 'sudo qm config 101'        # should be passwordless

# Which physical disk backs a path? ROTA 0 = SSD, 1 = HDD (data1 is dying)
lsblk -o NAME,SIZE,TYPE,MOUNTPOINT,ROTA,MODEL
df -h /var/lib/vmgen-exports                       # must show pve-* (SSD), not data1
smartctl -a /dev/sdb | grep -iE 'Reallocated|Pending|Model'   # data1 health
```

## Shared OVA export (NFS, on the SSD thin LV)

```bash
# Host side: export dir must be vmgen-owned so the per-build mkdir (no sudo) works
chown vmgen:vmgen /var/lib/vmgen-exports && chmod 0775 /var/lib/vmgen-exports
exportfs -v                                        # confirm NFS export to .10
df -h /var/lib/vmgen-exports                        # confirm it's the SSD thin LV

# VM side: confirm the NFS mount is live
mount | grep vmgen-exports
docker compose exec vmgen-worker sh -c 'mount | grep exports; ls -la /var/lib/vmgen/exports'

# Round-trip test (host write → container read)
sudo -u vmgen touch /var/lib/vmgen-exports/_x      # on host
docker compose exec vmgen-worker sh -c 'ls -l /var/lib/vmgen/exports/_x'   # on VM
```

## OVA retention / cleanup

```bash
# App soft-archives after 7d (state ARCHIVED, download 410) but does NOT delete the file.
# Physical purge = cron on the storage host (already installed):
sudo crontab -l
# 30 3 * * * find /var/lib/vmgen-exports -mindepth 1 -maxdepth 1 -type d \
#   -not -name 'lost+found' -mtime +8 -exec rm -rf {} + >> /var/log/vmgen-ova-purge.log 2>&1

# Dry-run what the cron WOULD delete (read-only)
find /var/lib/vmgen-exports -mindepth 1 -maxdepth 1 -type d -not -name 'lost+found' -mtime +8 -print
```

## Concurrency notes

- `WORKER_MAX_CONCURRENT_BUILDS=2` is validated. Build VMs run on the **Proxmox
  host**, not inside the platform VM — watch the *host's* RAM during parallel builds.
- Concurrent builds need a unique network identity per clone: the worker assigns a
  deterministic per-VMID MAC, and pfSense must have **Services → DHCP Server → LAN →
  "Ignore client identifiers" = ON** (keys leases on MAC, not the shared machine-id).
- Memory bar on the Proxmox/Platform gauge looking ~94% after a build/download is
  page cache — check `free -h` `available`, not `used`.
