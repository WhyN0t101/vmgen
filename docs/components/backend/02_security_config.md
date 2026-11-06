# Security Config — `SecurityConfig.java` + `JwtAuthenticationFilter.java` + `WorkerApiKeyFilter.java`

## What Spring Security is

Every HTTP request passes through a series of filters before reaching a controller.
Each filter can inspect the request, block it, or pass it along.
Spring Security is the framework that manages this filter chain.

**Why filters instead of logic in controllers:**
If authentication lived in controllers, every new endpoint would need to call
`authService.authenticate(request)` at the top. One forgotten call = unauthenticated access.
A filter runs unconditionally on every request — you can't forget it.

---

## `SecurityConfig.java` — the rulebook

### CSRF disabled

```java
.csrf(AbstractHttpConfigurer::disable)
```

CSRF protects against attacks where a malicious site tricks the browser into sending
requests using credentials the browser stores automatically — like cookies.

**Why disabled:** This API uses JWTs in the `Authorization` header. The browser never sends
headers automatically — only cookies and form data. CSRF attacks are structurally impossible.
Keeping CSRF enabled would add complexity with no security benefit.
If this API used cookie sessions, CSRF would need to be enabled.

---

### Stateless sessions

```java
.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

Tells Spring Security to never create an HTTP session on the server.

**Why:** Server-side sessions don't scale — a session on server A doesn't exist on server B.
JWTs carry all identity information inside the token, so any server instance can validate
any request independently. No shared state needed.

The `sessions` table in the database is not a Spring session — it is an application-level
record used to support logout and revocation, which JWTs alone cannot do.

---

### Authorization rules

```java
.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
.requestMatchers("/api/worker/**").permitAll()
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.anyRequest().authenticated()
```

First match wins — evaluated top to bottom.

**Why URL rules AND `@PreAuthorize`:**
URL rules are a coarse first gate. `@PreAuthorize` on individual methods is fine-grained —
it can check things URL patterns cannot, like "is this user operating on their own resource?"
Two layers means a misconfigured URL rule does not automatically expose everything.

**Why `/api/worker/**` is `permitAll()`:**
The worker has its own authentication mechanism — `WorkerApiKeyFilter`. If Spring Security's
URL rules blocked it, the filter would never run. `permitAll()` lets it through and the filter
handles the actual check.

---

### `@EnableMethodSecurity`

Enables `@PreAuthorize` annotations on controller and service methods.
Without this annotation, `@PreAuthorize` silently does nothing — a dangerous misconfiguration.

---

### Security headers

```java
.frameOptions(fo -> fo.deny())         // X-Frame-Options: DENY — prevents clickjacking
.contentTypeOptions(Customizer.withDefaults()) // X-Content-Type-Options: nosniff
```

- **Clickjacking:** attacker embeds your page in an invisible iframe, tricks clicks
- **MIME sniffing:** browser executes a file as JavaScript even if served as text/plain

Both headers cost nothing and close attack vectors automatically on every response.

---

### Filter order

```java
.addFilterBefore(workerApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

Worker filter runs first — worker requests are handled and returned before the JWT filter
wastes time looking for a Bearer token that will never be there.

---

## `JwtAuthenticationFilter.java`

Runs on every request. If there is a JWT in the header, validate it and tell Spring Security
who the user is.

### Step 1 — no token, pass through

```java
if (authHeader == null || !authHeader.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);
    return;
}
```

No token = pass through. Public endpoints (login, register) legitimately have no token.
The URL rules will block it later if the endpoint needed auth.

### Step 2 — parse and validate the JWT

```java
JwtClaimsDto claims = jwtService.parseAndValidate(rawToken);
```

Checks the cryptographic signature and expiry. Tampered or expired tokens throw immediately.

**Why cryptographic signatures:**
Without a signature anyone could forge a JWT claiming to be an admin. The signature is
produced with a server-side secret — only the server can produce a valid token.

### Step 3 — session check in the database

```java
Optional<Session> session = sessionService.getActiveSession(sessionId, now);
if (session.isEmpty()) throw new SessionRevokedException();

String computedHash = TokenHashUtil.sha256HexLower(rawToken);
if (!TokenHashUtil.constantTimeEquals(session.get().getTokenHash(), computedHash)) {
    throw new SessionRevokedException();
}
```

**This is the most important design choice in the auth system.**

A valid JWT signature is not enough — the session must exist in the database.
On logout, the session row is deleted. The next request with that JWT fails here
even though the signature is still mathematically valid.

**Why this matters:** JWTs are self-contained — once issued, they are valid until expiry.
Without this check, a stolen token would be usable until it expires even after logout.
The session record makes JWTs revocable.

The token hash check is a second verification — the stored hash must match a hash of the
incoming token. Constant-time comparison prevents timing attacks.

### Step 4 — populate the security context

```java
SecurityContextHolder.getContext().setAuthentication(authentication);
```

Stores the authenticated identity in a thread-local context. `@PreAuthorize` and
`@AuthenticationPrincipal` in controllers read from this context.

**Why thread-local:** Spring processes each request on one thread. Thread-local means
any code on that thread can read who the caller is without it being passed as a parameter
everywhere. Cleaned up automatically when the request thread finishes.

---

## `WorkerApiKeyFilter.java`

Workers are machines, not humans. They authenticate with a shared API key, not a JWT.

```java
String providedKey = request.getHeader("X-Worker-Api-Key");
if (!TokenHashUtil.constantTimeEquals(workerApiKey, providedKey)) {
    response.setStatus(401);
    return;
}
```

**Why constant-time comparison:**
`String.equals()` short-circuits on the first mismatched character. An attacker measuring
response times can determine the key character by character. Constant-time comparison always
takes the same time regardless of where the mismatch is.

**Why fail at startup if key is missing (`@PostConstruct`):**
If the key is missing and you only check at request time, the app starts and silently
accepts all worker requests unauthenticated. Failing at startup makes misconfiguration
immediately visible rather than a silent production security hole.

---

## The two authentication systems side by side

| | Users / Admins | Worker |
|---|---|---|
| **Mechanism** | JWT in `Authorization: Bearer` header | API key in `X-Worker-Api-Key` header |
| **Filter** | `JwtAuthenticationFilter` | `WorkerApiKeyFilter` |
| **Validated against** | Database session record | Configured secret |
| **Revocable** | Yes — delete the session row | Only by changing the key |
| **Why this design** | Humans need logout + expiry + roles | Machines need simplicity, no session overhead |

---

## Full request flow with security

```
Request arrives
    → WorkerApiKeyFilter: /api/worker/**?
        → yes: check X-Worker-Api-Key → allow or 401
        → no: pass through

    → JwtAuthenticationFilter: Authorization: Bearer header?
        → no: pass through
        → yes:
            → parse + validate JWT signature/expiry
            → check session exists in DB and is not revoked
            → check token hash matches stored hash
            → populate SecurityContextHolder with userId + role

    → Spring Security URL rules:
        → /api/admin/** requires ROLE_ADMIN
        → everything else requires authenticated

    → @PreAuthorize on the method (second, fine-grained check)

    → Controller runs
```
