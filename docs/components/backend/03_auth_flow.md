# Auth Flow — `AuthController` + `AuthService` + `JwtService`

## Three operations: register, login, logout

---

## Register

### Controller
```java
@PostMapping("/register")
public ResponseEntity<StatusResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
    rateLimitService.checkRegisterRateLimit(getClientIp(http));
    authService.register(req);
    return ResponseEntity.ok(new StatusResponse("ok"));
}
```

`@Valid` triggers Bean Validation — Spring checks DTO fields (e.g. `@NotBlank`, `@Email`)
before the method runs. Invalid input is rejected before touching the service.

Rate limit check first — if the IP exceeded the limit, throws immediately.

**Why rate limit registration:**
Without it, an attacker could enumerate valid usernames/emails at speed by flooding the
register endpoint and observing "username taken" vs "ok" responses.

---

### Service — `register()`

```java
PasswordPolicyValidator.validate(req.password());

User user = new User();
user.setPasswordHash(passwordEncoder.encode(req.password()));
user.setEmail(req.email().toLowerCase(Locale.ROOT));
user.setRole(UserRole.USER);
userRepository.save(user);
```

`passwordEncoder.encode()` is Argon2id. The plain password is never stored.

**Why Argon2id over bcrypt:**
bcrypt is CPU-bound. Argon2id is also memory-bound — it requires a configurable amount
of RAM per hash attempt. GPUs can parallelise CPU work cheaply but memory is harder to
parallelise, making brute-force attacks significantly more expensive.

Email is normalised to lowercase — `USER@EXAMPLE.COM` and `user@example.com` are the same account.

**The generic error message:**
```java
throw new ValidationException("Validation failed",
    List.of("An account with this username or email is already registered"));
```

Does not say which field is taken. If it said "username already taken", an attacker learns
that username exists — useful for targeted attacks. One generic message leaks nothing.

---

## Login

### Service — `login()` — the most security-dense method in the codebase

**Step 1 — look up the user**
```java
User user = userRepository.findByEmailIgnoreCase(identifier)
    .or(() -> userRepository.findByUsernameIgnoreCase(identifier))
    .orElse(null);
```
Returns `null` if not found — deliberately not throwing yet.

**Step 2 — constant-time password verification**
```java
String passwordHash = user != null ? user.getPasswordHash() : dummyPasswordHash;
boolean passwordMatches = passwordEncoder.matches(req.password(), passwordHash);
```

If user doesn't exist, still run `passwordEncoder.matches()` against a dummy hash.

**Why:** If you returned immediately on "user not found", that path would be faster than
a path that runs the hash. An attacker measuring response times could distinguish
"user exists" (slow) from "user doesn't exist" (fast). This is user enumeration via timing.

The dummy hash is pre-computed at startup (`@PostConstruct`) using the same encoder
so timing is identical whether the user exists or not.

**Step 3 — reject on wrong credentials**
```java
if (user == null || !passwordMatches) {
    auditService.logLoginFailed(identifier, ctx);
    throw new AuthenticationFailedException();
}
```
Both "user not found" and "wrong password" produce the same exception with the same message.
An attacker cannot distinguish between them.

**Step 4 — lockout check AFTER credential check**
```java
if (user.getLockedUntil() != null && now.isBefore(user.getLockedUntil())) {
    throw new AccountLockedException(retryAfterSeconds);
}
```

**Why check lockout after password:**
If you checked lockout first and returned a different error ("account locked" vs "wrong password"),
an attacker could determine which accounts exist and are locked — user enumeration again.
Only someone who knows the correct password learns the account is locked.

**Step 5 — revoke all existing sessions**
```java
sessionService.revokeAllSessionsForUser(user.getId());
```

On new login, all previous sessions are invalidated. If your account was compromised and
someone else is logged in, logging in again kicks them out.

**Step 6 — issue the token**
```java
long ttlSeconds = user.getRole() == UserRole.ADMIN
    ? jwtProperties.getAdminTtlSeconds()   // 1800s = 30 minutes
    : jwtProperties.getUserTtlSeconds();   // 3600s = 1 hour

String token = jwtService.generateToken(userId, role, sessionId, now, exp);
String tokenHash = TokenHashUtil.sha256HexLower(token);
sessionService.createSession(user, sessionId, tokenHash, exp);
```

**Admin tokens are shorter-lived (30 min vs 60 min).**
Admins have more destructive capabilities — a stolen admin token has a smaller window.

The token hash is stored in the session record — the JWT filter checks this on every request.

**Step 7 — return the token**
```java
return new AuthResponse(token, exp.toString(), user.getRole().name(), user.getId());
```
The raw JWT goes back to the frontend, which stores it in memory (Pinia).
The backend never sees it stored — only receives it on subsequent requests.

---

## JwtService — what's inside a JWT

### `generateToken()`

```java
return JWT.create()
    .withIssuer("vmgen-api")
    .withIssuedAt(issuedAt)
    .withExpiresAt(expiresAt)
    .withSubject(userId.toString())   // who this token belongs to
    .withClaim("role", role)          // USER or ADMIN
    .withClaim("sid", sessionId)      // links to the session record
    .sign(algorithm);                 // HMAC-SHA256
```

A JWT has three parts: `header.payload.signature`. The payload is base64 encoded, not
encrypted — anyone can decode it. The signature is what makes it tamper-proof.

**Why HMAC-SHA256:**
Symmetric signing — same secret used to sign and verify. Simpler than RSA and sufficient
when the same server both issues and verifies tokens.

**Why include `sid` (session ID):**
The JWT alone cannot be revoked. The `sid` links it to a database record that can be deleted.
This bridges the gap between "stateless JWT" and "revocable session."

### `parseAndValidate()`

```java
jwt = verifier.verify(rawToken);
```

Verifier checks three things automatically:
- Signature is valid (not tampered)
- Token is not expired
- Issuer matches `"vmgen-api"` (rejects tokens issued by other systems)

Then extracts and validates claims — userId and sessionId must be well-formed UUIDs.
A JWT missing required claims is rejected.

---

## Logout

```java
@PreAuthorize("isAuthenticated()")
@PostMapping("/logout")
public ResponseEntity<StatusResponse> logout(Authentication authentication, ...) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    authService.logout(principal.sessionId(), principal.userId(), ctx);
}
```

Session ID comes from the `Authentication` object the JWT filter populated —
not from the request body. You can only log out your own current session.

```java
public void logout(UUID sessionId, UUID userId, AuditContext ctx) {
    sessionService.logout(sessionId, userId);  // deletes the session row
    auditService.logLogout(user, sessionId, ctx);
}
```

Session row deleted. Next request with the old JWT hits the filter's session check,
finds no row, gets 401 — even though the token signature is still valid.

---

## Full login flow

```
POST /api/auth/login { identifier, password }
    → rate limit check (IP)
    → look up user by email or username
    → run Argon2id hash regardless (timing protection)
    → wrong credentials → 401, audit logged
    → correct but locked → 423, audit logged
    → correct and unlocked:
        → revoke all existing sessions
        → generate session ID
        → sign JWT (userId + role + sessionId + expiry)
        → hash the JWT, store in sessions table
        → audit logged
        → return { token, expiresAt, role, userId }

Every subsequent request:
    → JWT filter extracts token
    → verify signature + expiry
    → look up session by sid claim
    → hash incoming token, compare to stored hash
    → populate SecurityContextHolder
    → controller runs
```
