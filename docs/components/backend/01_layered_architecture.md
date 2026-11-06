# Layered Architecture

## The problem without layers

One class handling HTTP, business logic, and database access becomes unmaintainable fast.
Changing how data is stored breaks HTTP handling. Changing a business rule means digging
through database code. Layers solve this by giving each class one job.

---

## The three layers

```
HTTP Request
     ↓
[ Controller ]   — receives the request, validates input, returns response
     ↓
[ Service ]      — runs the business logic
     ↓
[ Repository ]   — talks to the database
     ↓
  PostgreSQL
```

Each layer only talks to the layer directly below it.
A controller never touches a repository. A repository never contains business logic.

---

## Controller — the HTTP boundary

**Job:** Receive the HTTP request, extract data, call the right service, return a response.
Nothing else. No business logic, no database queries.

```java
@PostMapping
public ResponseEntity<BuildResponse> createBuild(
    @RequestBody CreateBuildRequest request,
    @AuthenticationPrincipal UserDetails userDetails) {

    BuildResponse build = vmRequestService.createBuild(request, userDetails.getUsername());
    return ResponseEntity.status(201).body(build);
}
```

Four lines. Receives request, calls service, returns response.
The controller does not know how a build is created — that is the service's problem.

**Why:** If a controller contains business logic, adding a second way to trigger that logic
(e.g. a CLI, a scheduled task) means duplicating the logic. Keeping business logic in
services means any entry point can call the same service.

---

## Service — the business logic

**Job:** Make decisions. Enforce rules. Coordinate between repositories.

The service does not know about HTTP. It does not know status codes, request URLs, nothing.
It receives plain Java objects and returns plain Java objects.

```java
@Service
@Transactional
public class VmRequestService {
    public BuildResponse createBuild(CreateBuildRequest request, String username) {
        // validate the template exists
        // check the user hasn't exceeded build limits
        // resolve script dependencies
        // create the VmRequest entity
        // save it
        // return a DTO
    }
}
```

`@Transactional` means the entire method runs inside a database transaction.
If anything throws, all database changes are rolled back automatically.

**Why `@Transactional` on service, not controller:**
Controllers are the HTTP layer — they shouldn't know about transaction boundaries.
Services are where the business operation is defined — so the transaction matches
the operation boundary exactly.

---

## Repository — the database layer

**Job:** Speak to PostgreSQL. Nothing else.

Spring Data JPA generates most implementations from the method name:

```java
public interface VmRequestRepository extends JpaRepository<VmRequest, UUID> {
    List<VmRequest> findByUser_IdOrderBySubmittedAtDesc(UUID userId);
}
```

Spring generates the SQL from `findByUser_IdOrderBySubmittedAtDesc` automatically.
Custom SQL is only written when the query is too complex for method name generation.

**Why:** Keeping queries in one place means changing the database schema requires
touching repository files only — not services or controllers.

---

## Models — database tables as Java objects

Models (entities) are Java classes that map directly to database tables.

```java
@Entity
@Table(name = "vm_requests")
public class VmRequest {
    @Id UUID id;
    @Enumerated(EnumType.STRING) VmRequestState state;
    @ManyToOne User user;
}
```

- `@Entity` — this class is a database table
- `@Id` — primary key
- `@ManyToOne` — foreign key relationship

Models are internal. They are never sent directly to the frontend.

---

## DTOs — the API boundary

DTO = Data Transfer Object. Plain classes representing what goes into and out of the API.

**Why not return models directly:**
- Models may have fields never meant to be exposed (password hash, internal flags)
- Frontend may need data shaped differently from how it is stored
- JPA entities have lazy-loaded relationships that cause problems when serialised to JSON

The service converts between models and DTOs:

```
Frontend sends:    CreateBuildRequest DTO  →  service creates VmRequest entity
Frontend receives: BuildResponse DTO       ←  service reads from VmRequest entity
```

The frontend never sees internal fields.

---

## Exceptions — controlled failure

Custom exception classes for every error condition. Thrown anywhere:

```java
throw new ResourceNotFoundException("Template not found: " + id);
throw new AccountLockedException("Too many failed attempts");
```

`GlobalExceptionHandler` catches all of these centrally and converts to HTTP:

```
ResourceNotFoundException  →  404
AccountLockedException     →  423
RateLimitExceededException →  429
ForbiddenException         →  403
```

**Why:** Error handling in one place, not scattered across every service and controller.
Adding a new exception type means adding one mapping in one file.

---

## Tracing one request end to end

`POST /api/builds` (create a build):

```
1. HTTP POST → BuildController.createBuild()
   → extracts CreateBuildRequest from body
   → extracts username from JWT via @AuthenticationPrincipal

2. Controller calls vmRequestService.createBuild(request, username)

3. VmRequestService:
   → looks up user via UserRepository
   → validates template via TemplateRepository
   → resolves dependencies via ScriptCompatibilityService
   → creates VmRequest entity, state = QUEUED
   → saves via VmRequestRepository
   → converts to BuildResponse DTO

4. Controller receives BuildResponse DTO
   → returns HTTP 201 with DTO as JSON

5. If anything throws in step 3:
   → @Transactional rolls back all DB changes
   → GlobalExceptionHandler converts exception to the right HTTP error
```

The controller never touches the database.
The service never touches HTTP.
The repository never makes decisions.
