# Build Lifecycle — `VmRequestService` + `WorkerService`

## Two phases

- **Submission** — `VmRequestService.createBuild()` — user submits, backend validates and queues
- **Execution** — `WorkerService` — worker claims, runs, and completes the job

---

## Phase 1 — Build Submission (`VmRequestService.createBuild`)

One long validation chain before anything gets saved. Each step guards against a specific
class of bad input.

**Why so many validation steps:**
The worker runs unattended — it clones a VM, executes scripts, exports an OVA.
If invalid data reaches the worker, the build fails after 20+ minutes of wasted compute.
All validation happens upfront so the worker only ever receives well-formed jobs.

---

### Step 0 — reject duplicate script IDs

```java
if (uniqueIds.size() != requestedIds.size()) {
    throw new ValidationException("Duplicate script IDs in request");
}
```

Duplicate IDs would cause confusing downstream errors. Catching it first gives a clear message.

---

### Step 1 — template must exist and be ACTIVE

```java
if (template.getStatus() != TemplateStatus.ACTIVE) {
    throw new ValidationException("Template is not active");
}
```

A deprecated template still exists in the database but cannot be used for new builds.
Soft deletion lets old builds reference their original template without breaking history.

---

### Step 2 — all scripts must exist and be active

One query fetches all requested scripts. If the count doesn't match, some IDs were invalid
or deprecated — the error tells you exactly which ones.

---

### Step 3 — scripts must be compatible with the template

**Design choice — optional compatibility:**
If a template has no compatibility entries, any script can run on it.
If it has entries, only those scripts are allowed.
This lets you restrict certain templates to specific vulnerability sets.

---

### Step 4 — resolve dependencies and detect conflicts

```java
ScriptSelectionResult resolved = scriptCompatibilityService.resolveScriptSelection(request.getVulnerabilityIds());
```

Scripts can declare:
- **Dependencies** — "I require script X to run before me"
- **Conflicts** — "I cannot run alongside script Y"

Kahn's algorithm (topological sort) produces an execution order satisfying all dependencies
and rejects conflicting combinations. Auto-added dependencies are flagged.

**Why auto-add dependencies instead of rejecting:**
Better UX. If script A requires script B, forcing the user to know and add B manually is
friction. The system adds it automatically and reports what was added.

---

### Step 5b — constraint validation

Three constraint types:
- **Difficulty** — a "hard" script cannot run on an "easy" build
- **OS family** — a Linux script cannot run on a Windows template
- **Capabilities** — a script requiring `docker` cannot run on a template without it

Scripts declare what they need; templates declare what they provide.
Validation is a set intersection — if requirements are not a subset of capabilities, it fails.

---

### Step 6 — create the VmRequest

```java
vmRequest.setBuildSeed(generateBuildSeed());
```

```java
byte[] bytes = new byte[32];
SECURE_RANDOM.nextBytes(bytes);
return HexFormat.of().formatHex(bytes);
```

32 cryptographically random bytes → 64 hex characters.
This seed is the root of flag derivation.

**This is what makes the system deterministic:** same seed + same inputs = same flag.
Not that the OVA is bit-for-bit identical, but that the same configuration produces
the same learning outcome.

`SecureRandom` instead of `Random` because `Random` is predictable — given enough outputs,
you can compute future values. `SecureRandom` is cryptographically unpredictable.

---

### Step 7 — persist scripts in execution order

Each script gets an `executionOrder` number and a flag marking whether it was user-requested
or auto-added as a dependency.

---

### Step 7b — process parameters

Parameters (like `PORT`, `USERNAME`) can be user-provided or auto-generated.
`usedAutoValues` prevents two scripts from getting the same auto-generated value for the
same parameter name — two scripts both needing `PORT` should not both get `8080`.

---

## Phase 2 — Worker Execution (`WorkerService`)

### Job claiming — `claimNextJob()`

```sql
UPDATE vm_requests
SET state = 'BUILDING', claimed_by_worker_id = :workerId
WHERE id = (
    SELECT id FROM vm_requests
    WHERE state = 'QUEUED'
    ORDER BY submitted_at ASC
    FOR UPDATE SKIP LOCKED
    LIMIT 1
)
```

**Why `FOR UPDATE SKIP LOCKED`:**
If two workers poll simultaneously, `FOR UPDATE` locks the row being claimed.
`SKIP LOCKED` means the second worker skips any locked row and finds the next available one
instead of waiting. Two workers never claim the same job and neither blocks the other.
This is a PostgreSQL-native atomic claim — no application-level locking needed.

---

### Progress updates — `updateProgress()`

```java
request.setCurrentWorkerStage(update.getStage());
if ("exporting".equalsIgnoreCase(update.getStage()) && request.getState() == VmRequestState.BUILDING) {
    request.setState(VmRequestState.EXPORTING);
}
```

Two things:
- `currentWorkerStage` is updated — the frontend shows this to the user
- If stage is "exporting", state transitions `BUILDING → EXPORTING`

---

### The state machine

```
QUEUED → BUILDING → EXPORTING → READY → ARCHIVED
  ↓          ↓           ↓
FAILED     FAILED      FAILED
```

`VmRequestState` is an enum with `canTransitionTo()` encoding the valid graph.

```java
if (!request.getState().canTransitionTo(VmRequestState.READY)) {
    throw new ConflictException("Invalid state transition");
}
```

**Why a state machine:**
Without it, a race condition could allow a timed-out job (already marked FAILED) to later
be marked READY when a slow worker finally finishes. The state machine makes that impossible —
FAILED cannot transition to READY.

---

### Job completion — `completeJob()`

Three things happen atomically:

**1 — Update VmRequest to READY**
```java
request.setState(VmRequestState.READY);
request.setStoragePath(completion.getOvaPath());
request.setOvaSha256(completion.getOvaChecksum());
```

**2 — Persist the provenance record**
Snapshots everything about the build — template version, script versions and SHA-256 hashes,
parameters used, execution times, worker ID. Append-only — never updated after creation.

**3 — Verify and persist flag hashes**
```java
String expectedHash = deriveFlagHash(request.getBuildSeed(), requestId, fi.getScriptId(), tsUsed);
if (!MessageDigest.isEqual(expectedHash.getBytes(), fi.getFlagValueHash().getBytes())) {
    throw new ConflictException("Flag hash verification failed");
}
```

**This is the trust boundary.**
The worker sends flag hashes. The backend independently re-derives the same hashes.
If they do not match, the worker's output is rejected.

**Why verify instead of trust:**
The worker is a separate process that could be compromised or bugged. The backend never
trusts worker flag output — it always recomputes and verifies. The platform secret never
leaves the backend, so the worker cannot forge flags.

---

### Stale job detection

```java
List<VmRequest> stale = vmRequestRepository.findStaleJobs(
    List.of(VmRequestState.BUILDING), now.minus(buildingTimeout));
```

A scheduled task runs periodically. If a BUILDING job has not sent a progress update in
10 minutes it is marked FAILED. EXPORTING gets 30 minutes — OVA export can genuinely
take that long without emitting progress.

**Ping vs progress distinction:**
`pingJob()` updates `workerLastHeartbeatAt` but deliberately does NOT update `lastProgressAt`.
A worker can signal "I'm alive" during long script execution without resetting stale detection.
Stale detection is based on progress, not just liveness.

---

## Full lifecycle

```
User submits build
    → 7 validation steps
    → VmRequest saved: state=QUEUED, buildSeed=<random 32 bytes>

Worker polls
    → FOR UPDATE SKIP LOCKED → atomic claim
    → state: QUEUED → BUILDING
    → worker receives: template, scripts, parameters, buildSeed

Worker runs 16 stages, reporting progress
    → currentWorkerStage updated each stage
    → state: BUILDING → EXPORTING (when exporting starts)

Worker completes
    → state machine: EXPORTING → READY
    → OVA path, SHA-256, size stored
    → provenance record created (append-only)
    → backend re-derives flag hashes, verifies worker output
    → flag hashes persisted

User polls build status (every 2s via useBuildStatus composable)
    → sees state=READY → download button appears
```
