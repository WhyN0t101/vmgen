# Flag System — `FlagService`

## What it does

A user exploits their VM, finds the flag string (`FLAG{ABCDEFG...}`), and submits it.
The service validates it and tells them if it is correct.

---

## What is never stored

The raw flag string is never stored in the database. Only its SHA-256 hash is.
Even if the database is fully compromised, an attacker cannot read flags out of it.

When the user submits a flag, the service hashes their input and compares hashes.
Plaintext never touches the database on either side.

---

## The constants

```java
private static final int MAX_ATTEMPTS_PER_BUILD   = 5;
private static final int LOCKOUT_BUILD_THRESHOLD  = 3;
private static final Duration LOCKOUT_DURATION    = Duration.ofHours(1);
```

- 5 attempts per build before that build is locked
- Exhaust 5 attempts on 3 builds → account locked for 1 hour

---

## Step 1 — lockout check

```java
if (user.getLockedUntil() != null && Instant.now().isBefore(user.getLockedUntil())) {
    throw new AccountLockedException(secondsRemaining);
}
```

Checked first — if locked, nothing else runs. The exception includes seconds remaining
so the frontend can show a countdown.

---

## Step 2 — build ownership and state

```java
if (!build.getUser().getId().equals(user.getId())) {
    throw new ForbiddenException("Build does not belong to current user");
}
if (build.getState() != VmRequestState.READY) {
    throw new ValidationException("Flag submission requires build to be in READY state");
}
```

**Why 403 instead of 404 on ownership failure:**
You know the build exists because you just looked it up. 404 would be lying.
403 is honest — the resource exists, you just cannot access it.

Compare to `getBuild()` in VmRequestService which returns 404 on ownership failure —
there the goal is to not reveal whether other users' builds exist at all.

---

## Step 3 — per-build attempt limit

```java
if (build.getFailedFlagAttempts() >= MAX_ATTEMPTS_PER_BUILD) {
    throw new ValidationException("Maximum flag attempts reached for this build");
}
```

5 attempts per build, separate from account lockout. Even if not locked out, you cannot
brute-force a single build indefinitely.

---

## Step 4 — the comparison

```java
String submittedHash = hashFlag(request.getFlagValue().trim().toUpperCase());

for (Flag flag : flags) {
    if (MessageDigest.isEqual(
            submittedHash.getBytes(StandardCharsets.UTF_8),
            flag.getFlagValueHash().getBytes(StandardCharsets.UTF_8))) {
        matchedScriptId = flag.getScript().getId();
        break;
    }
}
```

### `.trim().toUpperCase()`
Normalises input before hashing. Base32 flags are uppercase by definition.
A user copying `flag{abcdef...}` from a terminal in lowercase would fail without this.

### Hash then compare
The submitted value is hashed. The stored value is already a hash.
Comparing `SHA256(submitted)` against `SHA256(stored_flag)` — plaintext never appears
on the comparison side.

### `MessageDigest.isEqual()`
Constant-time byte comparison. Standard `equals()` short-circuits on the first mismatched
byte, leaking timing information. With 5 attempts per build, an attacker could use timing
to narrow down the correct hash byte by byte. `isEqual` makes every comparison take the
same time regardless of how many bytes match.

---

## Step 5 — wrong flag and lockout trigger

```java
vmRequestRepository.incrementFlagFailures(buildId);
int newFailedAttempts = build.getFailedFlagAttempts() + 1;

if (newFailedAttempts >= MAX_ATTEMPTS_PER_BUILD) {
    long exhaustedBuilds = vmRequestRepository.countExhaustedBuilds(user.getId(), MAX_ATTEMPTS_PER_BUILD);
    if (exhaustedBuilds >= LOCKOUT_BUILD_THRESHOLD) {  // 3
        user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));  // 1 hour
    }
}
```

### Two-tier lockout design

**Per-build:** 5 failed attempts on one build → that build is locked, account is not.

**Account:** Exhaust 5 attempts on 3 different builds → account locked for 1 hour.

**Why two tiers:**
A single wrong attempt on one build could be a typo. Exhausting all attempts on 3 separate
builds is a pattern — either brute-forcing or seriously misusing the system.

**Why count exhausted builds instead of total attempts:**
Total attempts across all builds would punish legitimate users making honest mistakes on
multiple builds. Counting fully-exhausted builds targets the brute-force pattern specifically.

---

## Why flags are never stored in plaintext — thesis point

**Hash-only flag storage** serves two purposes:

1. **Database compromise** — an attacker dumping the database gets hashes, not flags.
   SHA-256 of a 28-character Base32 string is not practically reversible.

2. **Backend operator trust** — even the person running the backend cannot read flags.
   The only way to know a flag is to actually exploit the VM and find it.
   This is the correct property for a CTF/education platform.

---

## Full flag submission flow

```
User finds FLAG{ABCDEFG...} on their VM
    → POST /api/builds/{id}/flag { flagValue: "FLAG{...}" }

FlagService:
    → account locked?               → 423
    → build exists and owned?       → 403/404
    → build READY?                  → 409
    → attempts remaining?           → 422
    → hash submitted value (SHA-256, trim, uppercase)
    → constant-time compare against stored hash

    → match:
        → audit log (correct)
        → return { correct: true, attemptsRemaining: 5 }

    → no match:
        → increment build failed attempts
        → if build now exhausted (5 fails):
            → count exhausted builds for this user
            → if 3+ exhausted → lock account 1 hour
        → audit log (incorrect)
        → return { correct: false, attemptsRemaining: N }
```
