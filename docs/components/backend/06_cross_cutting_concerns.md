# Cross-Cutting Concerns — `AuditService` + `RateLimitService` + `GlobalExceptionHandler`

These three services touch every request but live outside any single request's business logic.
They are infrastructure that runs around your application, not inside it.

---

## `AuditService`

### What it is

Every security-relevant action writes a structured record to the `audit_logs` table.
Not application logs — a queryable database table recording who did what, when, from where.

**Why a dedicated service:**
Every service that needs to record an event calls `auditService.logSomething()`.
The audit service owns the format, storage, and error handling in one place.
If you logged directly from each service, a format change would touch 10 files.

---

### `Propagation.REQUIRES_NEW` — the most important design choice

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logLogin(User user, UUID sessionId, AuditContext ctx) { ... }
```

Every audit method runs in its own **separate transaction**.

**Why:** Consider login. `AuthService.login()` runs in a transaction. If `logLogin()` ran
inside the same transaction and anything later caused a rollback — say, session creation
fails — the audit log would also be rolled back and the login event would vanish.

`REQUIRES_NEW` suspends the caller's transaction and opens a new independent one.
The audit write commits immediately. Even if the calling transaction rolls back,
the audit record survives.

**This is especially important for failed events.** `logLoginFailed()` must persist even
when the login throws an exception. Shared transaction = the exception rolls back the audit too.

---

### Swallowed exceptions in `write()`

```java
private void write(...) {
    try {
        auditLogRepository.save(auditLog);
    } catch (Exception e) {
        log.error("Failed to write audit log: ...");
        // does NOT rethrow
    }
}
```

An audit failure never propagates to the caller.

**Why:** Audit logging is observability infrastructure. If it fails, it should not break
the operation being logged. A user should still be able to log in even if the audit database
is temporarily unavailable. The failure is recorded in application logs for investigation.

**The tradeoff:** In rare cases an audit event might not be recorded. For a thesis prototype
this is acceptable. A financial system would queue audit events durably instead.

---

### `AuditContext`

```java
record AuditContext(String ipAddress, String userAgent, Map<String, Object> metadata) {}
```

Built in the controller from the HTTP request, passed down to the service.
The service itself never touches `HttpServletRequest` — that belongs in the controller layer.
The context carries request metadata down without coupling the service to HTTP.

---

### What gets audited

Auth events, build lifecycle, worker events, flag events, template admin, script admin,
telemetry deletion. Everything that matters for security or accountability has a named event.

Not audited: read operations without security implications (listing scripts, viewing templates).
Those would create noise without value.

---

## `RateLimitService`

### What it does

Limits how many login and register requests a single IP can make within a time window.
Configured to 5 requests per 300 seconds.

```java
this.loginRateLimiter    = new RateLimiter(maxRequests, windowSeconds);
this.registerRateLimiter = new RateLimiter(maxRequests, windowSeconds);
```

**Why two independent limiters:**
Login and register have separate buckets per IP. Exhausting login attempts does not
affect register and vice versa.

Without this, an attacker brute-forcing login could accidentally block a legitimate user
from registering on the same IP (shared network, VPN, NAT).

### How it works

`RateLimiter` keeps a map of `IP → (request count, window start time)`.
Each `allow(ip)` call either increments the count (if within window and under limit) or
rejects (if over limit). `getResetSeconds(ip)` returns how long until the window resets —
this goes back to the client as `retryAfter`.

### Cleanup

```java
@Scheduled(fixedRate = 600_000)  // every 10 minutes
public void cleanup() {
    loginRateLimiter.cleanup();
    registerRateLimiter.cleanup();
}
```

Removes entries whose windows have expired. Without this, the IP map grows indefinitely
and eventually consumes significant memory.

### In-memory limitation

Rate limiter state lives in memory — resets on restart, does not work across multiple
backend instances. For a single-instance thesis prototype this is fine.
A production multi-instance deployment would use Redis for shared state.
The design is correct — the limitation is infrastructure, not logic.

---

## `GlobalExceptionHandler`

### What it is

`@RestControllerAdvice` intercepts exceptions thrown from any controller and converts them
into HTTP responses. Without it, Spring would return a default error page or raw stack trace.

**Why centralised:**
Every exception has exactly one place where it becomes an HTTP response.
No controller has try/catch blocks for business exceptions — they just throw.
Adding a new exception type means adding one mapping in one file.

---

### The mapping

```
AuthenticationFailedException  → 401 Unauthorized
AccountLockedException         → 423 Locked          (+ retryAfter seconds)
RateLimitExceededException     → 429 Too Many Requests (+ retryAfter seconds)
ForbiddenException             → 403 Forbidden
ResourceNotFoundException      → 404 Not Found
ConflictException              → 409 Conflict
GoneException                  → 410 Gone             (archived OVA)
ValidationException            → 400 Bad Request      (+ details array)
ProxmoxConnectionException     → 503 Service Unavailable
Exception (catch-all)          → 500 Internal Server Error
```

Each exception maps to a semantically correct HTTP status. This matters because the
frontend's `apiRequest()` reads the status code to decide what to do — 401 triggers logout,
403 shows an error, 429 shows a rate limit message. If everything returned 400 the frontend
could not distinguish them.

---

### `getPublicMessage()` — information control

Every handler calls `ex.getPublicMessage()`, not `ex.getMessage()`.

The exception itself may contain internal details useful for debugging — a database error,
an internal ID. `getPublicMessage()` returns a safe user-facing string.
Internal details stay in application logs, never in the HTTP response.

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
    log.error("Unhandled exception: {}", ex.getMessage(), ex);  // full detail in logs
    return ResponseEntity.status(500)
        .body(Map.of("message", "Internal server error"));       // generic to client
}
```

The catch-all handler is the clearest example — full stack trace in logs, one generic
sentence to the client. An attacker cannot learn about internal architecture from error messages.

---

## The three concerns together

```
Every request:
    → RateLimitService: is this IP allowed to proceed?

Every security event:
    → AuditService: record who did what, from where
      (in its own transaction — survives caller rollback)

Every exception:
    → GlobalExceptionHandler: convert to the right HTTP status
      with public-safe message, log the internal detail
```

None of these live inside a single business operation.
They are the safety net, the paper trail, and the error boundary of the entire system.
