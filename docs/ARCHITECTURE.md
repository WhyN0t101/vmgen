# VMGen System Architecture

**Version**: 2.4
**Last Updated**: July 2026
**Status**: Core Infrastructure Complete, Worker Fully Implemented, Flag System Complete, Platform Features Complete, Docker Deployment Complete (~99% overall)

> **Note**: This document describes the architecture specified in the thesis "Architecture for Deterministic Generation of Vulnerable Virtual Machines". Implementation status is tracked throughout using ✅ (complete), 🚧 (in progress), and ❌ (not started) markers.

---

## Table of Contents

1. [Introduction](#introduction)
2. [Architectural Principles](#architectural-principles)
3. [System Overview](#system-overview)
4. [Component Architecture](#component-architecture)
5. [Platform Features](#platform-features)
6. [Data Architecture](#data-architecture)
7. [Security Architecture](#security-architecture)
8. [Workflow Specifications](#workflow-specifications)
9. [Implementation Status](#implementation-status)
10. [Deployment Architecture](#deployment-architecture)

---

## Introduction

### Purpose and Scope

This platform addresses a fundamental gap in cybersecurity education: existing solutions provide either static, non-parametric artifacts with no construction traceability, or managed cyber-range infrastructures that orchestrate pre-built machines rather than generate them deterministically.

**The platform delivers four core capabilities**:

1. **Controlled Reproducibility**: Generate functionally equivalent VMs from identical inputs (template version, script versions, parameters, seed)
2. **Comprehensive Provenance**: Capture complete, immutable records of every build input and transformation
3. **Cryptographic Validation**: Generate unique HMAC-SHA256 derived flags for each vulnerability in each build
4. **Portable Distribution**: Export OVA files that execute independently on user-controlled infrastructure

### Key Architectural Constraint

**Determinism means functional equivalence, not bit-for-bit replication.** Given identical inputs, the platform produces VMs that:

- ✅ Expose identical vulnerabilities
- ✅ Require identical exploitation techniques
- ✅ Teach the same concepts
- ❌ Do NOT have identical flags (flags incorporate timestamps for uniqueness)
- ❌ Do NOT have identical timestamps, MAC addresses, UUIDs (cosmetic differences)

This design enables **solution-sharing prevention** while maintaining **pedagogical equivalence**.

---

## Architectural Principles

### 1. Controlled Reproducibility (FR4, NFR1)

**Definition**: Two VMs V₁ and V₂ are functionally equivalent if:

1. They expose identical vulnerability attack surfaces
2. They exhibit equivalent exploitation behavior
3. They contain unique validation artifacts (flags)
4. They permit controlled non-determinism in non-security elements

**Implementation Status**:

- ✅ **Thesis Design**: Complete specification with seed-based randomness
- ✅ **VmRequest Entity**: Stores `build_seed` for deterministic builds
- ✅ **Build Execution**: Worker executes full 16-stage pipeline (SSH provisioning, script execution, flag placement, OVA export)

### 2. Comprehensive Provenance Capture (FR8, NFR2)

**Provenance Record Contains**:

- Template identifier, version, and SHA-256 hash
- Script identifiers, versions, and SHA-256 hashes (all applied scripts)
- Generation parameters (difficulty, options, telemetry enabled)
- Random seed value (256-bit, hex-encoded)
- Build timestamp and completion timestamp
- OVA SHA-256 hash (computed post-export)

**Storage Model**:

- **Authoritative copy**: Backend database (immutable, append-only)
- **Exported copy**: JSON file alongside OVA (for offline verification)
- **Retention**: Provenance outlives artefacts (even after OVA deleted)

**Implementation Status**:

- ✅ **Database Schema**: `provenance_records` table with related `prov_scripts`, `prov_parameters`, `prov_dependencies` tables
- ✅ **JPA Entity**: `ProvenanceRecord` mapped to `VmRequest` (1:1)
- ✅ **Provenance Assembly**: Worker assembles provenance in `build/provenance.py`; backend stores on `completeJob`
- ✅ **OVA SHA-256**: Worker computes via `compute_sha256()` and sends with completion payload
- ✅ **JSON Export**: `GET /api/builds/{id}/provenance` returns full provenance; frontend download button serialises to JSON
- ✅ **Template SHA-256**: Stored as `"PENDING"` at registration time (backend cannot SSH into Proxmox independently). On the first completed build against a template, the worker computes the real hash via `get_template_config_hash()` and the backend updates the record via `WorkerService.persistProvenance()`. All subsequent builds use the real hash.

### 3. Build-Time Isolation (NFR4, Security)

**Network Isolation Requirements**:

- Build VMs execute on **isolated, non-routed network segments**
- **Default-deny egress policy**: No outbound connections from build VMs
- **Ingress restricted**: Only SSH from Automation Worker
- **No routing to**: Backend API, database, hypervisor management, external networks
- **Monitoring**: Any unexpected network activity triggers build failure

**Implementation Approaches**:

- **Option A**: Dedicated VLANs with no upstream routing
- **Option B**: Firewall-based isolation (pfSense, iptables, OPNsense) ← Reference implementation uses this
- **Option C**: Virtual network bridges with no gateway

**Implementation Status**:

- ✅ **Build Network**: `vmbr2` (BUILD interface, `192.168.2.0/24`) — dedicated bridge/subnet, separate from the `vmbr1` management network; genuinely routed through pfSense, not a shared L2 segment. Changed 2026-08-13 — the old flat `vmbr1`-for-everything layout let build↔platform traffic bypass pfSense via same-subnet ARP/switching, since pfSense sat as a peer VM rather than an inline router.
- ✅ **Egress Filtering**: pfSense VM with default-deny egress on the BUILD interface; explicit allow for Worker SSH (from LAN), DNS/NTP (to `192.168.2.254`), and HTTP/HTTPS (package installation)
- ⚠️ **Worker Isolation Check**: `verify_network_isolation()` in `proxmox/client.py` checks the **Proxmox host's own** interface config for a gateway entry — i.e. whether the hypervisor itself routes through that bridge — NOT whether guest DHCP hands build VMs a gateway. It is a host-misconfiguration sanity check, not a verification of guest-level isolation; the real guarantee is entirely the pfSense ruleset.
- ✅ **Stage 11 (REMOVE_NETWORK)**: All VM network interfaces removed before OVA export, verified post-removal
- ⚠️ **Internet access during provisioning**: Build VMs can reach HTTP/HTTPS for package installation during script execution. This is a known limitation documented in NFR4 — internet access is not Worker-controlled in the reference implementation
- ❌ **Network Monitoring**: Not implemented (unexpected traffic does not trigger automatic build failure)

### 4. Trust Boundaries

```
┌─────────────────────────────────────────────────────────────┐
│               TRUSTED COMPUTING BASE (During Build)         │
│                                                             │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌─────────┐│
│  │ Frontend │──▶│ Backend  │◀──│  Worker  │──▶│Proxmox  ││
│  └──────────┘   └────┬─────┘   └────┬─────┘   └────┬────┘│
│                      │                │              │     │
│                      └────────▶┌──────▼──────┐◀──────┘     │
│                                │  Database   │              │
│                                └─────────────┘              │
│                                                             │
│  Security-critical operations:                             │
│  • Template verification                                   │
│  • Vulnerability injection                                 │
│  • Flag generation (HMAC-SHA256)                          │
│  • Provenance capture                                      │
│  • Network isolation enforcement                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                          │
                          │ OVA Export
                          ▼
          ═════════════════════════════════════════
                    TRUST BOUNDARY
          ═════════════════════════════════════════
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│          UNTRUSTED (After Export - User Control)            │
│                                                             │
│  • Downloaded VMs run on user infrastructure               │
│  • Platform CANNOT enforce runtime integrity               │
│  • Guest telemetry is self-reported and untrusted          │
│  • Users have full administrative control                  │
│  • Flags can be extracted by any means (filesystem, etc.)  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Key Point**: The platform provides **build-time guarantees**, not runtime guarantees. Once exported, VMs are portable and independent.

---

## System Overview

### High-Level Architecture

```
┌─────────────────┐
│  User/Admin     │
│   (Browser)     │
└────────┬────────┘
         │ HTTPS
         ▼
┌─────────────────┐
│  Web Frontend   │  Vue 3 + TypeScript + Tailwind
│  (Port 5173)    │  • Scenario configuration UI
└────────┬────────┘  • Build monitoring
         │ REST API  • Template catalog
         ▼           • Admin panel
┌─────────────────┐  • Dashboard
│  Backend API    │  Spring Boot 4.0 + Java 21
│  (Port 8080)    │  • Authentication (JWT + sessions)
└────────┬────────┘  • Input validation
         │           • Job queue management
         │           • Worker coordination
         ├──────────────────┐
         │                  │
         ▼                  ▼
┌─────────────────┐   ┌─────────────────┐
│   PostgreSQL    │   │ Automation      │  Python 3.11+
│   Database      │   │    Worker       │  • Polls backend API
│                 │◀──│                 │  • Clones templates
│ • Users         │   │                 │  • Executes scripts via SSH
│ • Sessions      │   │ (Fully         │  • Requests flags from backend
│ • Templates     │   │  Implemented)  │  • Exports OVA
│ • Scripts       │   └────────┬────────┘  • Computes hashes
│ • VM requests   │            │
│ • Provenance    │            │ Proxmox API
│ • Flags         │            ▼
│ • Audit log     │   ┌─────────────────┐
│ • Build logs    │   │  Proxmox VE     │  Virtualization Platform
└─────────────────┘   │                 │  • Template storage
                      │ Build Network:  │  • Build VM execution
                      │ (Isolated)      │  • OVA export
                      └─────────────────┘
```

### Communication Flow

**User VM Generation Request**:

1. User authenticates via Frontend → Backend (JWT issued) ✅
2. User configures scenario (template, scripts, parameters) → Frontend validates ✅ (`GeneratePage.vue`: required-param/difficulty/conflict checks gate submission; `ScriptParameterInputs.vue`: per-field type/format validation)
3. Frontend submits request → Backend validates compatibility ✅ (`VmRequestService.createBuild()`: duplicate/active checks, template–script compatibility, `ScriptCompatibilityService` dependency resolution + Kahn's-algorithm cycle detection, OS/capability/difficulty constraints)
4. Backend creates `vm_requests` record (status='QUEUED'), generates seed ✅
5. **Worker polls backend API** for pending jobs ✅
6. Worker claims job atomically via `POST /api/worker/jobs/claim` ✅
7. Worker executes full 16-stage build pipeline (clone → SSH provision → scripts → flags → OVA export → provenance) ✅
8. Worker registers OVA + provenance + flag hashes via `POST /api/worker/jobs/{id}/complete` ✅
9. Backend updates status='READY' → User downloads OVA via `GET /api/builds/{id}/download` ✅
10. User exploits VM locally, submits flags → Backend validates ✅ `POST /api/builds/{id}/flag` (FlagService)

---

## Component Architecture

### 1. Web Frontend ✅ IMPLEMENTED

**Technology**: Vue 3 (Composition API) + TypeScript + Vite + Tailwind CSS + Pinia + TanStack Query

**Responsibilities**:

- ✅ User authentication (login, register, logout)
- ✅ Scenario configuration UI (template selection, vulnerability selection, parameters)
- ✅ Build monitoring (polling-based, via `useBuildStatus` composable)
- ✅ Template catalog browsing (public and admin views)
- ✅ Dashboard with build history
- ✅ Flag submission interface (UI wired, `POST /api/builds/{id}/flag` endpoint live)
- ✅ Admin panel (template management, user management, session management, service health metrics)
- ✅ Build log viewer (collapsible, lazy-loaded, colour-coded by level)
- ✅ Telemetry delete (user-initiated soft-delete from build status page)
- ✅ Trust model page (`/trust`) — documents platform trust boundaries, telemetry limitations, provenance verification, flag validation semantics, and reproducibility scope
- ✅ Trust boundary notices inline on build status page (download and telemetry sections)

**Directory Structure**:

```
vmgen-frontend/src/
├── api/
│   ├── client.ts                    # HTTP client with JWT injection
│   ├── endpoints.ts                 # API endpoint definitions
│   └── mockData.ts                  # Development mock data
├── components/
│   ├── layout/
│   │   └── AppNavbar.vue            # Navigation, auth status, role display
│   ├── ui/
│   │   ├── BaseButton.vue           # Reusable button component
│   │   ├── BaseCard.vue             # Card container
│   │   ├── BaseModal.vue            # Modal dialog
│   │   ├── BaseBadge.vue            # Status badges
│   │   └── BaseProgress.vue         # Progress bar
│   ├── templates/
│   │   ├── TemplateCard.vue         # Template summary card
│   │   ├── TemplateSelector.vue     # Selectable template list
│   │   ├── TemplateStatusBadge.vue  # DRAFT/ACTIVE/DEPRECATED badge
│   │   └── TemplateCapabilityBadge.vue
│   ├── vulnerabilities/
│   │   ├── VulnerabilityCard.vue    # Vulnerability display
│   │   └── VulnerabilitySelector.vue
│   ├── generate/
│   │   └── ConfigurationPanel.vue   # Build parameter configuration
│   └── admin/
│       ├── UserManagement.vue       # User role management
│       └── SessionList.vue          # Active session monitoring
├── composables/
│   ├── useAuthSync.ts               # Multi-tab auth synchronization
│   ├── useBuilds.ts                 # Build history & request management
│   ├── useBuildStatus.ts            # Build progress polling (TanStack Query)
│   ├── useTemplates.ts              # Template catalog queries/mutations
│   ├── useVulnerabilities.ts        # Vulnerability script queries
│   ├── useSessionMonitor.ts         # Session expiry monitoring
│   └── useAdmin.ts                  # Admin-specific operations
├── stores/
│   └── auth.ts                      # Pinia store: JWT + role held in memory only (never localStorage/sessionStorage)
├── router/
│   └── index.ts                     # Route definitions with auth/admin guards
├── views/
│   ├── HomePage.vue                 # Landing page
│   ├── LoginPage.vue                # Login form
│   ├── RegisterPage.vue             # Registration form
│   ├── DashboardPage.vue            # User dashboard with build history
│   ├── GeneratePage.vue             # VM generation configuration
│   ├── BuildStatusPage.vue          # Real-time build progress, logs viewer, telemetry delete, trust notices
│   ├── AdminPanelPage.vue           # Admin dashboard hub (incl. service health: DB status, storage)
│   ├── TrustModelPage.vue           # Static trust model documentation page (/trust)
│   ├── templates/
│   │   └── TemplateCatalog.vue      # Public template browser
│   └── admin/templates/
│       ├── TemplateList.vue         # Admin template management
│       ├── TemplateRegister.vue     # Register template from Proxmox
│       ├── TemplateDetail.vue       # Template detail view
│       └── TemplateEdit.vue         # Edit template metadata
└── styles/                          # Tailwind CSS configuration
```

**Routing**:

| Path | Component | Guard |
|------|-----------|-------|
| `/` | HomePage | Public |
| `/login` | LoginPage | Public |
| `/register` | RegisterPage | Public |
| `/dashboard` | DashboardPage | Auth |
| `/generate` | GeneratePage | Auth |
| `/builds/:requestId` | BuildStatusPage | Auth |
| `/templates` | TemplateCatalog | Auth |
| `/trust` | TrustModelPage | Auth |
| `/admin` | AdminPanelPage | Admin |
| `/admin/templates` | TemplateList | Admin |
| `/admin/templates/new` | TemplateRegister | Admin |
| `/admin/templates/:id` | TemplateDetail | Admin |
| `/admin/templates/:id/edit` | TemplateEdit | Admin |

**Communication**: REST API calls to Backend via `src/api/client.ts` (Fetch API with Bearer JWT injection).

**Current caveat**: The frontend uses the real backend for builds, templates, scripts, and admin flows. A mock data option may exist in `endpoints.ts` for development; production flows call the API.

---

### 2. Backend API ✅ FULLY IMPLEMENTED

**Technology**: Spring Boot 4.0 + Java 21 + Spring Security 6 + Flyway + JPA/Hibernate

**Directory Structure**:

```
vmgen-api/src/main/java/io/vmgen/api/
├── config/
│   ├── SecurityConfig.java              ✅ JWT filter chain, authorization rules
│   ├── JwtAuthenticationFilter.java     ✅ Bearer token extraction & validation
│   ├── WorkerApiKeyFilter.java          ✅ X-Worker-Api-Key header validation
│   ├── JwtProperties.java              ✅ JWT config (secret, issuer, TTLs)
│   ├── CorsProperties.java             ✅ CORS configuration
│   ├── ProxmoxApiConfig.java           ✅ Proxmox connection credentials
│   ├── TemplateDefaults.java           ✅ Immutable template defaults
│   ├── RateLimitProperties.java        ✅ Login rate-limiting config
│   ├── SecurityBeansConfig.java        ✅ Argon2 password encoder
│   └── JacksonConfig.java              ✅ JSON serialization config
├── controller/
│   ├── AuthController.java             ✅ /api/auth/* (register, login, logout)
│   ├── TemplateController.java         ✅ /api/templates (public catalog)
│   ├── BuildController.java            ✅ /api/builds (create, list, get by id)
│   ├── ScriptController.java           ✅ /api/scripts/{id}/parameters (user-facing script parameters)
│   ├── AdminTemplateController.java    ✅ /api/admin/templates (CRUD + validate)
│   ├── AdminProxmoxController.java     ✅ /api/admin/proxmox (VM listing)
│   ├── AdminScriptController.java      ✅ /api/admin/scripts (CRUD + compatibility + parameters)
│   ├── AdminCategoryController.java    ✅ /api/admin/categories (CRUD + reorder)
│   ├── UserAdminController.java        ✅ /api/admin/users (role management)
│   ├── SessionAdminController.java     ✅ /api/admin/sessions (list, revoke)
│   ├── AdminBuildController.java       ✅ /api/admin/builds (list all builds, get VM credentials)
│   ├── AdminMetricsController.java     ✅ /api/admin/metrics (queue depth, avg wait, 24h completions, worker status)
│   └── WorkerController.java           ✅ /api/worker/* (claim, progress, complete, fail, logs, derive-flags)
├── service/
│   ├── AuthService.java                 ✅ Registration, login, password validation
│   ├── JwtService.java                  ✅ HS256 token generation/validation
│   ├── SessionService.java              ✅ Session lifecycle, cleanup
│   ├── TemplateService.java             ✅ Template CRUD, validation, deprecation, compatible scripts
│   ├── VmRequestService.java            ✅ Build creation, validation, list/get (template–script compatibility enforced)
│   ├── ProxmoxService.java              ✅ Proxmox API client (VM verification)
│   ├── WorkerService.java               ✅ Job claiming, progress, completion, failure
│   ├── VulnerabilityCategoryService.java ✅ Category CRUD + ordering
│   ├── UserService.java                 ✅ User management, role changes
│   ├── AuditService.java                ✅ Audit logging
│   ├── RateLimitService.java            ✅ Login rate limiting
│   ├── AdminMetricsService.java         ✅ Queue depth, avg wait time, 24h completions, worker status, DB health (SELECT 1), OVA storage capacity
│   ├── TelemetryService.java           ✅ Telemetry upload (upsert), admin view, user-initiated soft-delete (deletedAt)
│   ├── FlagService.java                ✅ Flag validation (constant-time), attempt counting, 1h account lockout
│   └── ScheduledTasksService.java      ✅ Session cleanup, rate limit cleanup, stale build detection
├── service/script/
│   ├── ScriptService.java              ✅ Script CRUD + SHA-256 content hashing
│   ├── ScriptCompatibilityService.java  ✅ Script dependency/conflict management, enriched script lists
│   └── ScriptParameterService.java     ✅ Script parameter definitions (typed + validation)
├── models/
│   ├── user/                            ✅ User, Session, UserRole
│   ├── vm/                              ✅ VmRequest, VmRequestState, VmRequestScript, VmRequestScriptParameter, *Id
│   ├── template/                        ✅ Template, TemplateStatus, TemplateCapability, ScriptCompatibility, *Id
│   ├── script/                          ✅ Script, ScriptConflict, ScriptDependency, ScriptParameter, ScriptParameterChoice,
│   │                                     ParameterType, ValueSource, RandomConfig, ValidationRules
│   ├── vulnerability/                   ✅ VulnerabilityCategory
│   ├── flag/                            ✅ Flag
│   ├── provenance/                      ✅ ProvenanceRecord
│   └── build/                           ✅ BuildLog
├── repository/
│   ├── UserRepository.java              ✅
│   ├── SessionRepository.java           ✅
│   ├── VmRequestRepository.java         ✅ claimNextJobUpdate (atomic), findStaleJobs, findByUser_IdOrderBySubmittedAtDesc
│   ├── VmRequestScriptRepository.java   ✅
│   ├── VmRequestScriptParameterRepository.java ✅
│   ├── VulnerabilityCategoryRepository.java ✅
│   └── AuditLogRepository.java         ✅
├── repository/script/
│   ├── ScriptRepository.java            ✅
│   ├── ScriptConflictRepository.java    ✅
│   ├── ScriptDependencyRepository.java  ✅
│   ├── ScriptCompatibilityRepository.java ✅
│   ├── ScriptParameterRepository.java   ✅
│   └── ScriptParameterChoiceRepository.java ✅
├── repository/template/
│   ├── TemplateRepository.java         ✅
│   └── TemplateCapabilityRepository.java ✅
├── mapper/
│   ├── ScriptListDtoMapper.java         ✅ Script → ScriptListDto (base + enriched)
│   └── ScriptSummaryDtoMapper.java     ✅ Script → script/category summary DTOs
├── dto/
│   ├── auth/                           ✅ AuthResponse, LoginRequest, RegisterRequest, JwtClaimsDto
│   ├── admin/                          ✅ RoleUpdateRequest, ActiveSessionDto, RevokeSessionRequest, RevokeAllSessionsRequest, WorkerStatusDTO, AdminMetricsResponse (incl. dbStatus, storageTotalBytes, storageUsableBytes)
│   ├── audit/                          ✅ AuditContext
│   ├── build/                          ✅ BuildLogDto (id, logLevel, stage, message, loggedAt)
│   ├── common/                         ✅ StatusResponse, PagedResponse
│   ├── category/                       ✅ CreateCategoryRequest, UpdateCategoryRequest, CategoryDetailDto, CategoryListDto, CategoryReorderRequest (+ category/ScriptSummaryDto)
│   ├── script/                         ✅ CreateScriptRequest, UpdateScriptRequest, ScriptDetailDto, ScriptListDto, ScriptSummaryDto,
│   │                                     ScriptCompatibilityInfo/ViewDto, ScriptSelectionResult,
│   │                                     AddScriptConflictRequest, AddScriptDependencyRequest,
│   │                                     Create/UpdateScriptParameterRequest, ScriptParameterDto, ParameterChoiceDto, ScriptParameterChoiceDefinition
│   ├── template/                       ✅ RegisterTemplateRequest, UpdateTemplateMetadataRequest, TemplateResponse, TemplateListResponse
│   ├── proxmox/                        ✅ ProxmoxVmSummary, ProxmoxVmDetails, ProxmoxVmConfig, ProxmoxVmListResponse, ProxmoxApiResponse
│   └── worker/                         ✅ ClaimJobRequest/Response (incl. templateId), ProgressUpdateRequest, JobCompleteRequest, JobFailRequest, WorkerAckResponse, WorkerRegisteredResponse, DeriveFlagsRequest/Response, UpdateTemplateHashRequest
├── exceptions/
│   ├── GlobalExceptionHandler.java     ✅ Maps exceptions to HTTP responses
│   ├── VmgenApiException.java          ✅ Base exception
│   ├── AuthenticationFailedException   ✅ 401
│   ├── InvalidTokenException           ✅ 401
│   ├── SessionExpiredException         ✅ 401
│   ├── SessionRevokedException         ✅ 401
│   ├── ForbiddenException              ✅ 403
│   ├── ResourceNotFoundException       ✅ 404
│   ├── TemplateNotFoundException       ✅ 404
│   ├── ProxmoxVmNotFoundException      ✅ 404
│   ├── ConflictException               ✅ 409
│   ├── DuplicateTemplateException      ✅ 409
│   ├── TemplateValidationException     ✅ 422
│   ├── ImmutableFieldUpdateException   ✅ 422
│   ├── ValidationException             ✅ 400
│   ├── RateLimitExceededException      ✅ 429
│   └── ProxmoxConnectionException      ✅ 502
├── utils/
│   ├── PasswordPolicyValidator.java    ✅ 12+ chars, complexity rules
│   ├── TokenHashUtil.java              ✅ SHA256 hashing, constant-time comparison
│   └── RateLimiter.java                ✅ Sliding window rate limiter
└── annotations/                        ✅ Custom annotations
```

**API Endpoints**:

| Method | Path | Controller | Auth | Status |
|--------|------|------------|------|--------|
| POST | `/api/auth/register` | AuthController | Public | ✅ |
| POST | `/api/auth/login` | AuthController | Public | ✅ |
| POST | `/api/auth/logout` | AuthController | JWT | ✅ |
| GET | `/api/templates` | TemplateController | JWT | ✅ |
| GET | `/api/templates/{id}` | TemplateController | JWT | ✅ |
| POST | `/api/admin/templates` | AdminTemplateController | Admin | ✅ |
| GET | `/api/admin/templates` | AdminTemplateController | Admin | ✅ |
| GET | `/api/admin/templates/{id}` | AdminTemplateController | Admin | ✅ |
| POST | `/api/admin/templates/{id}/validate` | AdminTemplateController | Admin | ✅ |
| PATCH | `/api/admin/templates/{id}/deprecate` | AdminTemplateController | Admin | ✅ |
| PATCH | `/api/admin/templates/{id}/metadata` | AdminTemplateController | Admin | ✅ |
| GET | `/api/admin/proxmox/vms` | AdminProxmoxController | Admin | ✅ |
| GET | `/api/admin/proxmox/vm-storage` | AdminProxmoxController | Admin | ✅ |
| POST | `/api/admin/users/{userId}/role` | UserAdminController | Admin | ✅ |
| GET | `/api/admin/sessions` | SessionAdminController | Admin | ✅ |
| POST | `/api/admin/sessions/revoke` | SessionAdminController | Admin | ✅ |
| POST | `/api/admin/sessions/revoke-all` | SessionAdminController | Admin | ✅ |
| GET | `/api/admin/scripts` | AdminScriptController | Admin | ✅ |
| GET | `/api/admin/scripts/{id}` | AdminScriptController | Admin | ✅ |
| POST | `/api/admin/scripts` | AdminScriptController | Admin | ✅ |
| PUT | `/api/admin/scripts/{id}` | AdminScriptController | Admin | ✅ |
| POST | `/api/admin/scripts/{id}/deprecate` | AdminScriptController | Admin | ✅ |
| GET | `/api/admin/scripts/{id}/compatibility` | AdminScriptController | Admin | ✅ |
| POST | `/api/admin/scripts/{id}/conflicts` | AdminScriptController | Admin | ✅ |
| DELETE | `/api/admin/scripts/{id}/conflicts/{conflictId}` | AdminScriptController | Admin | ✅ |
| POST | `/api/admin/scripts/{id}/dependencies` | AdminScriptController | Admin | ✅ |
| DELETE | `/api/admin/scripts/{id}/dependencies/{dependencyId}` | AdminScriptController | Admin | ✅ |
| GET | `/api/admin/scripts/{id}/parameters` | AdminScriptController | Admin | ✅ |
| POST | `/api/admin/scripts/{id}/parameters` | AdminScriptController | Admin | ✅ |
| PUT | `/api/admin/scripts/{scriptId}/parameters/{parameterId}` | AdminScriptController | Admin | ✅ |
| DELETE | `/api/admin/scripts/{scriptId}/parameters/{parameterId}` | AdminScriptController | Admin | ✅ |
| GET | `/api/admin/categories` | AdminCategoryController | Admin | ✅ |
| GET | `/api/admin/categories/{id}` | AdminCategoryController | Admin | ✅ |
| POST | `/api/admin/categories` | AdminCategoryController | Admin | ✅ |
| PUT | `/api/admin/categories/{id}` | AdminCategoryController | Admin | ✅ |
| POST | `/api/admin/categories/{id}/deprecate` | AdminCategoryController | Admin | ✅ |
| POST | `/api/admin/categories/reorder` | AdminCategoryController | Admin | ✅ |
| POST | `/api/worker/heartbeat` | WorkerController | API Key | ✅ |
| POST | `/api/worker/jobs/claim` | WorkerController | API Key | ✅ |
| PATCH | `/api/worker/jobs/{id}/progress` | WorkerController | API Key | ✅ |
| POST | `/api/worker/jobs/{id}/complete` | WorkerController | API Key | ✅ |
| POST | `/api/worker/jobs/{id}/fail` | WorkerController | API Key | ✅ |
| POST | `/api/worker/jobs/{id}/logs` | WorkerController | API Key | ✅ |
| POST | `/api/worker/jobs/{id}/derive-flags` | WorkerController | API Key | ✅ Derives raw flags + VM password server-side; PLATFORM_SECRET never leaves backend |
| PATCH | `/api/worker/templates/{id}/hash` | WorkerController | API Key | ✅ Atomically sets template SHA-256 only when current value is `'PENDING'` |
| GET | `/api/admin/metrics` | AdminMetricsController | Admin | ✅ Queue depth, avg wait, 24h completions, per-worker cpu/mem/jobs, DB health, OVA storage capacity |
| GET | `/api/admin/builds` | AdminBuildController | Admin | ✅ Paginated list of all builds across all users |
| GET | `/api/admin/builds/{id}/credentials` | AdminBuildController | Admin | ✅ Returns VM login username + password |
| GET | `/api/admin/builds/{id}/telemetry` | AdminBuildController | Admin | ✅ Returns raw telemetry JSON for a build |
| GET | `/actuator/health` | Spring Actuator | Public | ✅ Returns `{"status":"UP"}` — no internal details exposed |
| POST | `/api/builds` | BuildController | JWT | ✅ |
| GET | `/api/builds` | BuildController | JWT | ✅ |
| GET | `/api/builds/{id}` | BuildController | JWT | ✅ |
| GET | `/api/builds/{id}/download` | BuildController | JWT | ✅ Full file (200) or partial content (206) with `Range` header support (RFC 7233) |
| GET | `/api/builds/{id}/provenance` | BuildController | JWT | ✅ |
| GET | `/api/builds/{id}/logs` | BuildController | JWT | ✅ Returns ordered build log entries for the requesting user's build |
| POST | `/api/builds/{id}/clone` | BuildController | JWT | ✅ Clones a FAILED build into a new QUEUED build; re-randomises auto-generated parameters |
| POST | `/api/builds/{id}/telemetry` | BuildController → TelemetryService | JWT | ✅ Upserts telemetry JSON (max 1 MB); only for READY builds with telemetry enabled |
| DELETE | `/api/builds/{id}/telemetry` | BuildController → TelemetryService | JWT | ✅ Soft-deletes telemetry (sets deletedAt); audited |
| GET | `/api/scripts/{id}/parameters` | ScriptController | JWT | ✅ USER_INPUT / USER_INPUT_OR_RANDOM only |
| POST | `/api/builds/{id}/flag` | BuildController → FlagService | JWT | ✅ Body: `{flagValue}` · Response: `{correct, attemptsRemaining}` |

**Scheduled Tasks** (`ScheduledTasksService`):

| Task | Interval | Purpose |
|------|----------|---------|
| `cleanupSessions` | 5 min | Remove expired and idle sessions (>24h) |
| `cleanupRateLimits` | 5 min | Clear expired rate limit entries |
| `detectStaleBuilds` | 1 min | Fail builds with no progress update >10 min |
| `detectLongRunningBuilds` | 5 min | Fail builds exceeding 3h wall-clock duration |
| `detectStaleWorkers` | 3 min | Log warning for workers with no heartbeat >180s |

---

### 3. Automation Worker ✅ FULLY IMPLEMENTED

**Technology**: Python 3.11+ with `paramiko` (SSH), `proxmoxer` (Proxmox API), `cryptography` (transitive via paramiko), `requests` (HTTP), `psutil` (metrics)

**Directory Structure**:

```
vmgen-worker/
├── src/
│   ├── worker/
│   │   ├── __init__.py
│   │   ├── main.py             ✅ Entry point: poll loop, ThreadPoolExecutor, SIGTERM/SIGINT handler, heartbeat
│   │   ├── config.py           ✅ Config loading from .env, validation (SSH key file existence + permissions)
│   │   └── backend/
│   │       └── client.py       ✅ claim_job, update_progress, complete_job (3-retry), fail_job, append_log, heartbeat, derive_build_secrets, update_template_hash (non-fatal)
│   ├── build/
│   │   ├── pipeline.py         ✅ Full 16-stage build pipeline
│   │   ├── flags.py            ✅ SHA-256 flag hashing (derivation moved to backend)
│   │   └── provenance.py       ✅ OVA SHA-256 hash + provenance assembly
│   ├── proxmox/
│   │   ├── client.py           ✅ Full Proxmox API + SSH-based OVA export
│   │   └── ovf.py              ✅ OVF 1.0 descriptor generation
│   └── ssh/
│       └── executor.py         ✅ SSH with jump-host support, script piping, flag placement
├── pyproject.toml              ✅ Package metadata
├── requirements.txt            ✅ Dependencies pinned
└── config.yaml.example         ✅ Configuration template
```

**Fully Implemented** ✅:

- Configuration management (`Config.load()` from `.env`) with validation including SSH key file existence and permission check (`0600`)
- CLI argument parsing (`--env`, `--validate`, `--worker-id`)
- Structured logging with worker ID prefix
- Main polling loop with `ThreadPoolExecutor` (configurable concurrency, default 3)
- SIGTERM/SIGINT graceful shutdown: 8-second grace period within Docker's 10s SIGKILL window; unfinished builds reported as `failed`
- Periodic heartbeat to backend every 60 seconds (`POST /api/worker/heartbeat`)
- Backend API client: `claim_job`, `update_progress`, `complete_job` (3 retries, 5s delay), `fail_job`, `append_log` (fire-and-forget), `heartbeat`, `derive_build_secrets`, `update_template_hash` (non-fatal, only when backend still holds `'PENDING'`)
- Network isolation check at startup — worker refuses to start if the configured `PROXMOX_ISOLATED_BRIDGE` (`vmbr2`) has a gateway on the Proxmox **host's own** interface config (host-misconfig sanity check, not a guest-isolation guarantee — see Network Isolation section above)
- Full 16-stage build pipeline (`build/pipeline.py`)
- Proxmox API: `vm_exists`, `clone_vm` (full clone; optional target storage via `PROXMOX_BUILD_STORAGE`, e.g. clone onto SSD while templates live elsewhere), `configure_network`, `inject_cloudinit_key`, `get_vm_ip` (guest agent), `start_vm`, `stop_vm`, `remove_network` (with post-removal verification), `delete_vm`, `get_template_config_hash`
- Cloud-init wait: after SSH is up, before scripts run, the worker waits for `cloud-init status --wait` so first-boot apt activity (cloud-init / unattended-upgrades) doesn't hold the apt lock when scripts call apt
- OVA export via SSH: `qm config` → `pvesm path` → `qemu-img convert` (stream-optimised VMDK) → OVF 1.0 generation → `tar` → delivery to worker. Delivery is `EXPORT_TRANSFER_MODE`: `sftp` (default, SFTP pull — works when worker and Proxmox are separate hosts) or `shared` (tar straight onto storage shared between Proxmox host and worker via `PROXMOX_SHARED_EXPORT_DIR` — no copy, for a co-located worker)
- Three SSH auth methods: `cloudinit` (ephemeral 4096-bit RSA key, in-memory only), `static_key`, `password_reset`
- SSH via jump host (Proxmox as jump host for build VMs on isolated bridge)
- Script execution: content piped to `bash -s` via stdin — no temp file, no path in command string
- Flag and VM password derivation: worker requests from backend via `POST /api/worker/jobs/{id}/derive-flags` — PLATFORM_SECRET never leaves the backend
- Flag placement: piped via stdin to `sudo tee` — flag value never in command string
- VM sanitization: bash history, authorized_keys, SSH host keys, cloud-init logs, all log files
- OVA SHA-256: chunked 64KB reads
- Provenance assembly: template identifier/version/sha256, worker ID, timestamps, per-script execution records (merged from job spec + runtime)

**Configuration** (`.env`):

```properties
DATABASE_HOST=localhost
DATABASE_PORT=5432
DATABASE_NAME=vmgen
DATABASE_USER=vmgen
DATABASE_PASSWORD=<required>

PROXMOX_HOST=<required>
PROXMOX_TOKEN_ID=worker@pve!automation
PROXMOX_TOKEN_SECRET=<required>
PROXMOX_VERIFY_SSL=false
PROXMOX_NODE=pve

BACKEND_URL=http://localhost:8080
BACKEND_API_KEY=<required>

WORKER_ID=worker-01
LOG_LEVEL=INFO
```

**Worker Config Defaults**:

- Poll interval: 30 seconds
- Max concurrent builds: 3

**Build Pipeline** (From Thesis — All Steps ✅ Implemented in `build/pipeline.py`):

```
1. CLAIM JOB
   └─ POST /api/worker/jobs/claim {workerId}
      Backend: SELECT FOR UPDATE SKIP LOCKED → atomic claim

2. CLONE TEMPLATE
   ├─ Verify template SHA-256 hash
   ├─ Call Proxmox API: POST /api2/json/nodes/{node}/qemu/{vmid}/clone
   └─ Attach to isolated build network

3. BOOT VM
   ├─ Start VM via Proxmox API
   ├─ Wait for SSH availability (timeout: 5 min)
   └─ Generate ephemeral SSH key pair

4. EXECUTE SCRIPTS (Sequential)
   ├─ For each script in vm_request:
   │   ├─ Retrieve script content from database
   │   ├─ SCP script to guest
   │   ├─ SSH execute: bash /tmp/script.sh
   │   ├─ Verify exit code == 0
   │   └─ Update progress (N/M scripts complete)
   └─ Log all command output

5. GENERATE FLAGS (server-side derivation)
   ├─ Worker calls POST /api/worker/jobs/{id}/derive-flags
   │     {workerId, flagDerivationTimestamp}
   ├─ Backend derives flags using PLATFORM_SECRET (never leaves API server):
   │   ├─ flag_raw = HMAC-SHA256(
   │   │     key = PLATFORM_SECRET,
   │   │     message = seed_hex || request_id || script_id || timestamp_iso8601
   │   │   )
   │   ├─ truncate to 130 bits (17 bytes, zero last 6 bits of byte[16] & 0xC0)
   │   ├─ flag_encoded = RFC 4648 Base32 of 17 bytes (28 chars, no padding, always ends "AA")
   │   └─ flag_final = "FLAG{" + flag_encoded + "}"
   ├─ Backend also derives VM login password: HMAC-SHA256("vm-login:<seed>:<jobId>") → first 16 hex chars
   └─ Backend returns {flags: [{scriptId, rawFlag}], vmLoginPassword} to worker over authenticated API channel

6. PLACE FLAGS
   ├─ For each script's flag placement metadata:
   │   ├─ Write raw flag via SSH stdin pipe to tee (flag value never appears in command string)
   │   └─ Placement path comes from script's flagLocation field

7. POST-BUILD SANITIZATION
   ├─ Remove SSH authorized_keys
   ├─ Clear cloud-init configs
   ├─ Regenerate SSH host keys on next boot
   ├─ Remove build logs (keep intentional vuln logs)
   └─ Clear temporary files

8. SHUTDOWN
   └─ Proxmox API: POST /nodes/{node}/qemu/{vmid}/status/shutdown

9. EXPORT OVA
   ├─ Proxmox API: GET /nodes/{node}/qemu/{vmid}/config
   ├─ Extract disk paths
   ├─ Convert QCOW2 to VMDK (if needed)
   ├─ Generate OVF descriptor
   └─ Package as .ova (tar archive)

10. COMPUTE HASHES
    ├─ template_hash = SHA-256(template disk image)
    ├─ ova_hash = SHA-256(exported .ova file)
    └─ script_hashes = [SHA-256(script_content) for each script]

11. ASSEMBLE PROVENANCE
    └─ ProvenanceRecord {
          request_id, template_id, template_version, template_hash,
          scripts: [{id, version, hash}], parameters, seed,
          build_timestamp, completion_timestamp, ova_hash
        }

12. REGISTER WITH BACKEND
    ├─ POST /api/worker/jobs/{id}/complete
    │   Body: { workerId, ovaPath, ovaChecksum, ovaSizeBytes }
    └─ Backend verifies state, stores provenance, updates status='READY'

13. CLEANUP
    ├─ Delete ephemeral SSH keys
    ├─ Delete build VM from Proxmox
    └─ Delete temporary files
```

**Error Handling**:

- SSH readiness timeout (5 min / 300s in `wait_for_ssh`): Mark build as failed
- Script non-zero exit: Mark build as failed, preserve logs
- No-progress silence timeout (server-side): 10 min in `BUILDING`, 35 min in `EXPORTING` — `markStaleJobsAsFailed` transitions to `FAILED`
- Absolute max build time (3 h, `markLongRunningJobsAsFailed`): mark failed regardless of heartbeat activity
- Hypervisor error: build fails and the cloned VM is cleaned up (no automatic build-level retry — see LIMITATIONS.md)

---

### 4. Database (PostgreSQL 16) ✅ SCHEMA COMPLETE

**Migration Files**:

- `V1__initial_schema.sql` ✅ Consolidated baseline schema (all tables from original V1–V10). Legacy migration files are archived under `db/migration_legacy/`.

**Tables and Usage Status**:

| Table | Status | Purpose |
|-------|--------|---------|
| `users` | ✅ Used | User accounts with Argon2 hashed passwords |
| `sessions` | ✅ Used | Active JWT sessions (SHA256 token hash) |
| `templates` | ✅ Used | VM template metadata with Proxmox integration |
| `template_capabilities` | ✅ Used | Template capability tags (many-to-many) |
| `audit_log` | ✅ Used | Security audit trail (login/logout events) |
| `vm_requests` | ✅ Used | Build submission (VmRequestService), worker coordination |
| `vulnerability_categories` | ✅ Used | Admin-managed vulnerability categories for scripts |
| `scripts` | ✅ Used | Vulnerability injection scripts (admin CRUD implemented) |
| `script_conflicts` | ✅ Used | Script mutual-exclusion rules |
| `script_dependencies` | ✅ Used | Script dependency rules |
| `script_parameters` | ✅ Used | Parameter definitions for scripts |
| `script_parameter_choices` | ✅ Used | Choices for `choice`-type parameters |
| `vm_request_script_parameters` | ✅ Used | Per-request script parameter values (persisted on build create) |
| `vm_request_scripts` | ✅ Used | Script-to-request ordered mapping (persisted on build create) |
| `flags` | ✅ Used | Flag hashes stored by WorkerService.completeJob (hash-only, never plaintext) |
| `provenance_records` | ✅ Used | Populated by WorkerService.completeJob on build completion |
| `prov_scripts` | ✅ Used | Script provenance detail |
| `prov_parameters` | 🚧 Schema exists | Parameter provenance (not yet populated) |
| `prov_dependencies` | 🚧 Schema exists | Dependency provenance (not yet populated) |
| `build_logs` | ✅ Used | Build execution logs written by worker via `POST /api/worker/jobs/{id}/logs` |
| `submission_attempts` | ❌ Unused | Legacy table — attempt tracking is done via `vm_requests.failed_flag_attempts` (defined in V1 initial schema) |
| `telemetry` | ❌ Unused | Optional guest telemetry uploads |
| `worker_heartbeats` | ❌ Unused | Worker liveness is tracked in-memory (`ConcurrentHashMap` in `WorkerService`); not persisted to DB |

**Key Schema: vm_requests (with V4 improvements)**:

```sql
CREATE TABLE vm_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    template_id UUID NOT NULL REFERENCES templates(id),

    -- Build configuration
    build_seed VARCHAR(64) NOT NULL,
    difficulty_override VARCHAR(50),
    telemetry_enabled BOOLEAN NOT NULL DEFAULT FALSE,

    -- State machine
    state VARCHAR(50) NOT NULL,         -- QUEUED|BUILDING|EXPORTING|READY|FAILED|ARCHIVED

    -- Worker tracking (V4)
    claimed_by_worker_id VARCHAR(255),
    current_worker_stage VARCHAR(100),
    worker_last_heartbeat_at TIMESTAMP,

    -- Progress
    last_progress_at TIMESTAMP,

    -- Artifact storage
    ova_filename TEXT,
    ova_sha256 VARCHAR(64),
    ova_size_bytes BIGINT,
    storage_path TEXT,
    storage_deleted_at TIMESTAMP,

    -- Timestamps
    submitted_at TIMESTAMP NOT NULL,
    claimed_at TIMESTAMP,
    building_started_at TIMESTAMP,
    exporting_started_at TIMESTAMP,
    completed_at TIMESTAMP,
    failed_at TIMESTAMP,
    failure_reason TEXT,
    archived_at TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_worker_claim CHECK (
        (state IN ('BUILDING', 'EXPORTING') AND claimed_by_worker_id IS NOT NULL)
        OR (state NOT IN ('BUILDING', 'EXPORTING'))
    )
);

-- Indexes for worker operations
CREATE INDEX idx_vm_requests_worker ON vm_requests(claimed_by_worker_id);
CREATE INDEX idx_vm_requests_timeout ON vm_requests(last_progress_at)
    WHERE state IN ('BUILDING', 'EXPORTING');
```

**VmRequest State Machine**:

```
QUEUED ──→ BUILDING ──→ EXPORTING ──→ READY ──→ ARCHIVED
  │           │             │
  └───────────┴─────────────┘
              ↓
           FAILED ──→ ARCHIVED
```

Transitions enforced by `VmRequestState.canTransitionTo()`:

- QUEUED → BUILDING, FAILED
- BUILDING → EXPORTING, FAILED
- EXPORTING → READY, FAILED
- READY → ARCHIVED
- FAILED → ARCHIVED
- ARCHIVED → (terminal)

---

### 5. Proxmox VE Infrastructure ✅ INTEGRATED (Read-Only)

**Current Integration** ✅:

- List VMs on a node (`GET /api/admin/proxmox/vms?node=pve`)
- Get VM storage configuration (`GET /api/admin/proxmox/vm-storage?vmid=100&node=pve`)
- Detect Proxmox templates (template=1 flag)
- Verify VM existence during template registration

**Authentication**: Token-based (`PVEAPIToken=user@realm!tokenname=secret`)

**Configuration** (application.properties):

```properties
proxmox.api.url=${PROXMOX_API_URL:https://proxmox.local:8006/api2/json}
proxmox.api.token-id=${PROXMOX_TOKEN_ID:worker@pve!automation}
proxmox.api.token-secret=${PROXMOX_TOKEN_SECRET}
proxmox.api.verify-ssl=${PROXMOX_VERIFY_SSL:false}
proxmox.api.node=${PROXMOX_NODE:pve}
```

**Worker Proxmox Integration** ✅ (Implemented in `proxmox/client.py`):

- Clone VM template — full clone via `POST /api2/json/nodes/{node}/qemu/{vmid}/clone` (`full=1`)
- Start/stop VM — via Proxmox async task API with polling
- Configure VM network — attach to isolated bridge (`vmbr2`, via `PROXMOX_ISOLATED_BRIDGE`) via `virtio` NIC
- Get VM IP — via QEMU guest agent (`network-get-interfaces`)
- Inject cloud-init key — URL-encoded SSH public key via Proxmox config API
- Remove all network interfaces — before OVA export, with post-removal verification
- Export VM to OVA — SSH-based: `qm config` → `pvesm path` → `qemu-img convert` → OVF generation → `tar` → delivery (`sftp` default, or `shared` storage via `EXPORT_TRANSFER_MODE`)
- Delete VM — stops if running, then deletes; used in stage 16 and on failure cleanup

---

## Platform Features

This section documents every user-facing and operator-facing feature of the platform: what it does, how it is implemented, and which components are involved. Features are grouped by domain.

---

### 1. User Registration and Authentication

**What it does**: Users create accounts with a username, email address, and password. On login they receive a short-lived JWT that is required for all subsequent API calls. Logging out explicitly invalidates the session server-side.

**How it works**:

- `POST /api/auth/register` — `AuthService.register()` validates the password policy (12+ chars, mixed case, digit, symbol), hashes the password with Argon2id via `SecurityBeansConfig`, and persists the user with role `USER`.
- `POST /api/auth/login` — `AuthService.login()` loads the user, verifies the Argon2id hash, then `SessionService.createSession()` issues an HS256 JWT signed with `VMGEN_JWT_SECRET`. The JWT payload carries `sub` (userId), `role`, `sid` (sessionId), `iat`, and `exp`. The SHA-256 hash of the raw token is stored in the `sessions` table — never the plaintext token.
- `POST /api/auth/logout` — revokes the session row; subsequent requests with the same token are rejected by `JwtAuthenticationFilter`.
- On every request `JwtAuthenticationFilter` extracts the Bearer token, validates signature and expiry, looks up the session hash, and checks the session is not revoked or expired.
- All previous sessions for a user are revoked on new login (single-session enforcement).
- Rate limiting: 5 login attempts per IP per 5 minutes (`RateLimitService`, sliding window, in-memory `ConcurrentHashMap`).
- Frontend stores the JWT in a Pinia store (`stores/auth.ts`) in memory only — never `localStorage`.

**Components involved**: `AuthController`, `AuthService`, `SessionService`, `JwtService`, `JwtAuthenticationFilter`, `RateLimitService`, `PasswordPolicyValidator`, `User`, `Session` (entities), `users` + `sessions` (tables).

---

### 2. Role-Based Access Control

**What it does**: Two roles — `USER` and `ADMIN`. Regular users can submit builds, view their own builds, and submit flags. Admins additionally manage templates, scripts, categories, users, and sessions.

**How it works**:

- Every controller method carries a `@PreAuthorize` annotation (e.g. `hasRole('ADMIN')`, `hasAnyRole('USER','ADMIN')`).
- The class-level `@PreAuthorize("hasAnyRole('USER', 'ADMIN')")` on `BuildController` covers all build endpoints.
- Admin role is promoted via `POST /api/admin/users/{userId}/role` (`UserAdminController` → `UserService`).
- `@EnableMethodSecurity` is set on `SecurityConfig`, activating Spring's method-level security.
- Worker endpoints (`/api/worker/**`) bypass JWT and use `WorkerApiKeyFilter` with constant-time comparison against `WORKER_API_KEY`.

**Components involved**: `SecurityConfig`, `WorkerApiKeyFilter`, `JwtAuthenticationFilter`, all controllers with `@PreAuthorize`, `UserAdminController`, `UserService`.

---

### 3. VM Build Generation

**What it does**: Authenticated users configure a build by selecting a template, one or more vulnerability scripts, a difficulty level, and optional script parameters. The platform queues the build, a worker picks it up, provisions the VM, injects vulnerabilities, derives flags, exports an OVA, and marks the build ready for download.

**How it works**:

- `POST /api/builds` → `VmRequestService.createBuild()`:
  1. Validates the template exists and is `ACTIVE`.
  2. Validates all requested scripts exist and are active.
  3. Validates template–script compatibility (if the template has an explicit compatibility list, every requested script must be in it).
  4. Calls `ScriptCompatibilityService.resolveScriptSelection()` — Kahn's topological sort to expand dependencies and reject conflicts.
  5. Generates a 256-bit random `build_seed` (hex, `SecureRandom`).
  6. Persists `VmRequest` (state `QUEUED`), `VmRequestScript` rows (ordered), and `VmRequestScriptParameter` rows (user-provided + auto-generated values).
- A worker polls `POST /api/worker/jobs/claim` and atomically claims the oldest queued build via `SELECT FOR UPDATE SKIP LOCKED`.
- The worker executes the 16-stage pipeline (see §3 — Automation Worker).
- Progress is reported via `PATCH /api/worker/jobs/{id}/progress`; the frontend polls `GET /api/builds/{id}` every few seconds via `useBuildStatus` (TanStack Vue Query).
- On completion the worker calls `POST /api/worker/jobs/{id}/complete`; `WorkerService.completeJob()` transitions state to `READY`, stores the OVA path/checksum/size, and persists the provenance record and flag hashes.

**Components involved**: `BuildController`, `VmRequestService`, `ScriptCompatibilityService`, `ScriptParameterService`, `WorkerController`, `WorkerService`, `VmRequest` + `VmRequestScript` + `VmRequestScriptParameter` (entities), `vm_requests` + `vm_request_scripts` + `vm_request_script_parameters` (tables), `build/pipeline.py` (worker).

---

### 4. OVA Download

**What it does**: Once a build reaches `READY` state, the user can download the exported OVA file. Downloads support resumption via HTTP range requests, allowing interrupted downloads to continue from the point of failure.

**How it works**:

- `GET /api/builds/{id}/download` → `BuildController.downloadOva()`:
  1. `VmRequestService.getBuildDownloadInfo()` verifies build ownership, `READY` state, and that the OVA file has not been deleted.
  2. An audit event is recorded via `AuditService.logBuildDownloaded()`.
  3. The OVA is served as a `FileSystemResource` from the path stored in `vm_requests.storage_path`.

- **Full download (no `Range` header)**: responds `200 OK` with `Content-Length`, `Content-Disposition: attachment`, `Accept-Ranges: bytes` (signals range support to clients), and `X-Checksum-SHA256` (for integrity verification).

- **Partial download (`Range: bytes=N-M`)**: the `Range` header is parsed with Spring's `HttpRange.parseRanges()`. The resolved byte offsets are used to construct a `ResourceRegion(file, start, contentLength)`, which the `ResourceRegionHttpMessageConverter` streams efficiently without loading the whole file into memory. Response is `206 Partial Content` with `Content-Range: bytes N-M/total` and `Content-Length` set to the chunk size only.

- **Invalid range**: returns `416 Range Not Satisfiable` with `Content-Range: bytes */total` so the client can determine the correct file size.

- **Multi-range requests** (e.g. `Range: bytes=0-100, 200-300`): not supported; the server falls back to a full `200` response. No client in the current use case requires this.

- **Integrity**: the `X-Checksum-SHA256` header is included on both full and partial responses, carrying the SHA-256 hash of the complete OVA computed by the worker at export time.

**Components involved**: `BuildController.downloadOva()`, `VmRequestService.getBuildDownloadInfo()`, `AuditService`, `BuildDownloadInfo` (record DTO), Spring `ResourceRegion` + `HttpRange`, `vm_requests` (table, `storage_path`, `ova_sha256`, `ova_size_bytes`).

---

### 5. Build Retry (Clone Failed Build)

**What it does**: When a build fails — due to a worker crash, Proxmox error, SSH timeout, or script failure — the user can retry it with a single click. The retry creates a new `QUEUED` build that is an exact logical copy of the failed one, with auto-generated parameter values freshly randomised.

**How it works**:

- `POST /api/builds/{id}/clone` → `VmRequestService.cloneBuild()`:
  1. Loads the source build and verifies ownership. Returns 404 if not found or not owned.
  2. Rejects non-`FAILED` source builds with `409 Conflict` — only failed builds can be retried.
  3. Reads `VmRequestScript` rows for the source build, filtering out rows where `auto_added = true`. This reconstructs the original user-requested script list without auto-added dependencies, which will be re-resolved from scratch by `createBuild`.
  4. Reads `VmRequestScriptParameter` rows for the source build, filtering out rows where `was_auto_generated = true`. Only explicit user-provided parameter values are forwarded; auto-generated random values are intentionally dropped so they are freshly randomised in the new build (ensuring a distinct flag).
  5. Reconstructs a `CreateBuildRequest` with the same template, scripts, difficulty, and telemetry setting, and calls `createBuild()` — full validation (template still active, scripts still active, compatibility still valid) runs again.
- Returns `201 Created` with the new `BuildResponse`.
- Frontend (`BuildStatusPage.vue`): a "Retry Build" button appears in the failed state card. On click it calls `api.cloneBuild(id)` and navigates to the new build's status page on success. Inline error is shown if the clone request fails (e.g. template no longer active).

**Components involved**: `BuildController.cloneBuild()`, `VmRequestService.cloneBuild()` + `createBuild()`, `VmRequestScriptRepository.findWithScriptByVmRequestId()`, `VmRequestScriptParameterRepository.findByVmRequest_Id()`, `BuildStatusPage.vue`, `api.cloneBuild()` (`endpoints.ts`).

---

### 6. Flag Submission and Validation

**What it does**: After exploiting vulnerabilities and retrieving a flag from the VM, the user submits it through the platform for validation. The system validates cryptographically without storing flag plaintext, enforces a per-build attempt limit, and locks accounts that repeatedly fail across multiple builds.

**How it works**:

- `POST /api/builds/{id}/flag` → `FlagService.submitFlag()`:
  1. Checks `user.lockedUntil` — rejects with `423 Locked` if account is locked.
  2. Verifies the build is owned by the user and is in `READY` state.
  3. Checks `vm_requests.failed_flag_attempts` against the limit (5 per build).
  4. Normalises the submitted value (trim whitespace, uppercase).
  5. Computes `SHA-256(submittedFlag)` and uses `MessageDigest.isEqual()` (constant-time) to compare against all `flags.flag_value_hash` rows for this build.
  6. On match: logs `FLAG_SUBMITTED` audit event, returns `{correct: true, attemptsRemaining: N}`.
  7. On miss: increments `failed_flag_attempts`. If the user has ≥ 3 builds each with all 5 attempts exhausted, sets `user.locked_until = now + 1 hour`.
- Flag derivation (done by the **backend** when the worker calls `POST /api/worker/jobs/{id}/derive-flags` at Stage 7): `HMAC-SHA256(PLATFORM_SECRET, seed || requestId || scriptId || timestamp)` → truncate to 130 bits (17 bytes, `& 0xC0` on byte 16) → RFC 4648 Base32 → `FLAG{<28 chars>}`. The `PLATFORM_SECRET` never leaves the backend; the worker receives only raw flag values for placement.

**Components involved**: `BuildController`, `FlagService`, `AuditService`, `Flag` (entity), `flags` table, `users.locked_until` + `vm_requests.failed_flag_attempts` (both in V1 initial schema), frontend `BuildStatusPage.vue` flag submission form.

---

### 7. Provenance Retrieval

**What it does**: Every completed build has an immutable provenance record capturing all inputs used to produce the OVA — template version and hash, script versions and hashes, parameters, seed, timestamps, and OVA SHA-256. Users can retrieve and download this as JSON for offline verification.

**How it works**:

- `GET /api/builds/{id}/provenance` → `VmRequestService.getBuildProvenance()` — verifies ownership, loads the `ProvenanceRecord` and related `ProvenanceScript` rows, and maps to `ProvenanceResponse`.
- The provenance record is assembled by the worker (`build/provenance.py:assemble_provenance()`) and stored by `WorkerService.persistProvenance()` on job completion.
- The frontend `downloadManifest()` function in `BuildStatusPage.vue` fetches the provenance JSON and triggers a browser download as `provenance-{id}.json`.
- Records are append-only — no `UPDATE` or `DELETE` operations are permitted on `provenance_records`.

**Components involved**: `BuildController`, `VmRequestService.getBuildProvenance()`, `ProvenanceRecordRepository`, `ProvenanceRecord` + `ProvenanceScript` (entities), `provenance_records` + `prov_scripts` (tables), `build/provenance.py` (worker).

---

### 8. Health Check Endpoint

**What it does**: Exposes a public liveness endpoint that returns a simple `{"status":"UP"}` when the application is running and its dependencies are reachable. Intended for load balancers, container orchestration probes, and CI/CD smoke tests.

**How it works**:

- Provided by `spring-boot-starter-actuator`. Only the `health` endpoint is exposed (`management.endpoints.web.exposure.include=health`).
- `management.endpoint.health.show-details=never` ensures the response body contains only `{"status":"UP"}` or `{"status":"DOWN"}` for unauthenticated callers — no database connection details, disk space, or internal component names are leaked.
- `GET /actuator/health` is added to the `permitAll()` list in `SecurityConfig`, making it reachable without a JWT.
- Returns HTTP `200` when healthy, `503` when the application context detects a failing component.

**Components involved**: `spring-boot-starter-actuator` (dependency), `application.properties` (`management.*` properties), `SecurityConfig` (public matcher).

---

### 9. Template Management (Admin)

**What it does**: Admins register VM templates from Proxmox, validate they are accessible, annotate them with metadata and capability tags, and deprecate them when they are no longer suitable for new builds.

**How it works**:

- `POST /api/admin/templates` — registers a template by Proxmox VMID. `TemplateService` verifies the VM exists in Proxmox via `ProxmoxService`, creates the record with status `DRAFT`.
- `POST /api/admin/templates/{id}/validate` — re-checks Proxmox accessibility, sets status to `ACTIVE` and records `validatedAt` + `validatedBy`.
- `PATCH /api/admin/templates/{id}/deprecate` — sets status `DEPRECATED`; deprecated templates are hidden from the user catalog and cannot be used for new builds.
- `PATCH /api/admin/templates/{id}/metadata` — updates mutable fields (description, display name, etc.) without affecting status.
- Template–script compatibility is managed via `POST/DELETE /api/admin/templates/{id}/compatible-scripts`. If a template has any entries in this list, only those scripts may be selected for builds against it.
- Template SHA-256 is initially stored as `"PENDING"` (the backend cannot SSH into Proxmox independently). On the first completed build against a template, `WorkerService.persistProvenance()` computes the real hash via `get_template_config_hash()` and updates the record.

**Components involved**: `AdminTemplateController`, `TemplateService`, `ProxmoxService`, `Template` (entity), `templates` + `template_capabilities` + `script_compatibility` (tables), admin frontend pages (`TemplateList`, `TemplateRegister`, `TemplateDetail`, `TemplateEdit`).

---

### 10. Script Management (Admin)

**What it does**: Admins create and manage vulnerability injection scripts — the units of executable content that get run inside build VMs to introduce specific vulnerabilities. Scripts carry metadata (version, category, OS requirements, flag placement path), typed parameters, and dependency/conflict rules.

**How it works**:

- Full CRUD via `AdminScriptController` (`/api/admin/scripts`). Script content SHA-256 is computed on create/update in `ScriptService`.
- **Parameters**: each script can declare parameters with a type (`string`, `integer`, `choice`, `boolean`), a value source (`USER_INPUT`, `USER_INPUT_OR_RANDOM`, `RANDOM`, `FIXED`), validation rules, and allowed choices. `ScriptParameterService.processParameters()` validates user-supplied values and auto-generates random values for `USER_INPUT_OR_RANDOM` parameters not supplied by the user.
- **Conflicts**: mutual exclusion rules between scripts. `ScriptCompatibilityService.resolveScriptSelection()` rejects any build request that includes conflicting scripts.
- **Dependencies**: a script can declare that it requires another script to have been applied first. `resolveScriptSelection()` uses Kahn's topological sort (BFS) to expand transitive dependencies and compute the correct execution order.
- User-facing parameter discovery: `GET /api/scripts/{id}/parameters` (`ScriptController`) returns only `USER_INPUT` and `USER_INPUT_OR_RANDOM` parameters — internal/fixed parameters are not exposed to users.

**Components involved**: `AdminScriptController`, `ScriptController`, `ScriptService`, `ScriptCompatibilityService`, `ScriptParameterService`, `Script` + `ScriptParameter` + `ScriptConflict` + `ScriptDependency` (entities), `scripts` + related tables, admin frontend (`ScriptListPage`, `ScriptFormPage`).

---

### 11. Vulnerability Category Management (Admin)

**What it does**: Admins define a taxonomy of vulnerability categories (e.g. SQL Injection, Privilege Escalation, Weak Credentials) that scripts are classified under. Categories are ordered for display and can be deprecated.

**How it works**:

- Full CRUD + deprecation via `AdminCategoryController` (`/api/admin/categories`).
- `POST /api/admin/categories/reorder` accepts an ordered list of category IDs and updates the `display_order` column for each.
- Categories are associated with scripts via `script.vulnerability_category_id`.

**Components involved**: `AdminCategoryController`, `VulnerabilityCategoryService`, `VulnerabilityCategory` (entity), `vulnerability_categories` (table), `CategoryListPage.vue`.

---

### 12. Admin Operational Monitoring

**What it does**: Admins have visibility into platform health — queue depth, average build wait time, 24-hour completion counts, and per-worker CPU/memory/job metrics.

**How it works**:

- `GET /api/admin/metrics` → `AdminMetricsController` → `AdminMetricsService`:
  - Queue depth and avg wait time via `JdbcTemplate` queries against `vm_requests`.
  - 24-hour completion count from rows where `completed_at > now() - interval '24 hours'`.
  - Per-worker status from `WorkerService`'s in-memory `ConcurrentHashMap` of heartbeat payloads (CPU %, memory %, active job count, last seen).
- `GET /api/admin/sessions` — lists active sessions with user, IP, user agent, last activity.
- `POST /api/admin/sessions/revoke` / `revoke-all` — force-revokes individual or all sessions for a user.
- `GET /api/admin/builds` — paginated list of all builds across all users with state, failure reason, and artifact metadata.
- `GET /api/admin/builds/{id}/credentials` — returns the VM login username and password for a completed build (password derived server-side and stored in `provenance_records.vm_login_password`).

**Components involved**: `AdminMetricsController`, `AdminMetricsService`, `AdminBuildController`, `SessionAdminController`, `WorkerService` (heartbeat map), `AdminPanelPage.vue`, `AdminBuildPanel.vue`.

---

### 13. Audit Logging

**What it does**: Security-relevant events are appended to an immutable audit log with actor identity, IP address, user agent, and timestamp. The log is append-only — no record is ever updated or deleted.

**Events currently logged**:

| Event | Method |
|-------|--------|
| Login success | `logLogin` |
| Login failure | `logLoginFailed` |
| Logout | `logLogout` |
| Session revoked (admin) | `logSessionRevoked` |
| Role updated | `logRoleUpdated` |
| Build created | `logBuildCreated` |
| OVA downloaded | `logBuildDownloaded` |
| Provenance retrieved | `logProvenanceRetrieved` |
| Worker job completed | `logWorkerJobCompleted` |
| Worker job failed | `logWorkerJobFailed` |
| Flag submitted (correct or incorrect) | `logFlagSubmitted` |
| Account locked | `logAccountLocked` |
| Template registered | `logTemplateRegistered` |
| Template validated | `logTemplateValidated` |
| Template deprecated | `logTemplateDeprecated` |
| Template metadata updated | `logTemplateMetadataUpdated` |
| Script created | `logScriptCreated` |
| Script updated | `logScriptUpdated` |
| Script deprecated | `logScriptDeprecated` |

**How it works**: `AuditService` is injected into any service or controller that needs to log. Each method constructs an `AuditLog` entity and calls `auditLogRepository.save()`. The `audit_log` table has no soft-delete support and no update path.

**Components involved**: `AuditService`, `AuditLog` (entity), `audit_log` (table), `AuditContext` (DTO carrying IP + user agent extracted from `HttpServletRequest` by `HttpUtils.auditContext()`).

---

### 14. Worker Coordination

**What it does**: One or more automation workers register with the backend, poll for queued builds, execute the build pipeline, and report progress and completion. The backend provides atomic job claiming so multiple workers can run concurrently without conflicts.

**How it works**:

- **Claim**: `POST /api/worker/jobs/claim` uses `VmRequestRepository.claimNextJobUpdate()` — a native `UPDATE … WHERE state='QUEUED' … FOR UPDATE SKIP LOCKED` that atomically transitions the oldest queued build to `BUILDING` and assigns it to the requesting worker. Returns `204 No Content` if no job is available.
- **Progress**: `PATCH /api/worker/jobs/{id}/progress` updates `current_worker_stage` and `last_progress_at`. Auto-transitions to `EXPORTING` when stage matches.
- **Completion**: `POST /api/worker/jobs/{id}/complete` — `WorkerService.completeJob()` validates ownership, transitions state to `READY`, stores artifact metadata, persists provenance, independently re-derives and verifies flag hashes (constant-time), and stores SHA-256 flag hashes.
- **Failure**: `POST /api/worker/jobs/{id}/fail` transitions state to `FAILED` with a reason string.
- **Heartbeat**: `POST /api/worker/heartbeat` — workers report CPU %, memory %, and active job count every 60 seconds. Stored in `WorkerService`'s in-memory map.
- **Stale detection**: `ScheduledTasksService` fails builds with no progress update in >10 minutes, builds exceeding 3 hours wall-clock, and logs warnings for workers with no heartbeat in >180 seconds.
- **Flag derivation**: `POST /api/worker/jobs/{id}/derive-flags` — workers never hold or compute `PLATFORM_SECRET`; they request derived flag values and the VM login password from the backend over the authenticated worker API channel.

**Components involved**: `WorkerController`, `WorkerService`, `VmRequestRepository` (atomic claim), `ScheduledTasksService`, `AuditService`, `build/pipeline.py` (worker), `worker/backend/client.py` (worker HTTP client).

---

### Provenance Model (FR8, NFR2, NFR6)

**Purpose**: Enable reproducibility, auditability, and integrity verification

**Provenance Record Structure** (From Thesis):

```json
{
  "request_id": "uuid",
  "template": {
    "identifier": "ubuntu-22.04-base",
    "version": "v1.0.0",
    "sha256": "abc123..."
  },
  "scripts": [
    {
      "identifier": "ssh-weak-key",
      "version": "v2.0.1",
      "sha256": "def456...",
      "execution_order": 1
    },
    {
      "identifier": "sudo-misconfiguration",
      "version": "v1.1.0",
      "sha256": "789ghi...",
      "execution_order": 2
    }
  ],
  "parameters": {
    "difficulty": "medium",
    "telemetry_enabled": false
  },
  "seed": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "timestamps": {
    "submitted": "2026-01-20T10:00:00Z",
    "completed": "2026-01-20T10:45:00Z"
  },
  "ova": {
    "filename": "vmgen-abc123.ova",
    "size_bytes": 5368709120,
    "sha256": "789ghi..."
  }
}
```

**Key Properties**:

- **Immutability**: Append-only, no updates after finalization
- **Dual Storage**: Database (authoritative) + JSON export (offline verification)
- **Outlives Artifacts**: Retained even after OVA deleted
- **Non-Reproducible Elements**: Flags intentionally excluded (timestamp-based uniqueness)

**Implementation Status**:

- ✅ Database schema exists (`provenance_records`, `prov_scripts`)
- ✅ JPA entity `ProvenanceRecord` mapped to `VmRequest` (1:1)
- ✅ Provenance assembly implemented in worker `build/provenance.py` — `assemble_provenance()` merges job spec + runtime execution data
- ✅ SHA-256 hash computation implemented — OVA via `compute_sha256()` (chunked 64KB), template config via `get_template_config_hash()` (SSH `qm config`), scripts via content hash stored at upload time
- ✅ Provenance stored by `WorkerService.persistProvenance()` on `completeJob`
- ✅ JSON export: `GET /api/builds/{id}/provenance` returns full provenance record
- ❌ Offline verification tooling not provided (manual SHA-256 recomputation required)
- ❌ Digital signatures on provenance not implemented

---

### Script Repository (FR5, FR6, NFR5)

**Versioning Model**:

- Independent lifecycle from templates
- Semantic versioning: MAJOR.MINOR.PATCH
- All versions retained indefinitely
- Status: active | deprecated | retired

**Script Metadata**:

```yaml
identifier: "sqli-basic"
version: "v1.2.0"
vulnerability_category: "sql-injection"
difficulty: "medium"
os_families: ["linux"]
required_capabilities: ["web_server", "database"]
flag_location: "/var/www/html/admin/flag.txt"
flag_ownership: "www-data:www-data"
flag_permissions: "0644"
content_hash: "sha256:..."
```

**Implementation Status**:

- ✅ Database schema exists (`scripts`, `vulnerability_categories`, `script_conflicts`, `script_dependencies`, `script_parameters`, `script_parameter_choices`)
- ✅ JPA entities implemented (scripts, categories, conflicts/dependencies, parameters)
- ✅ Script management API endpoints implemented (admin): `AdminScriptController` (`/api/admin/scripts/*`)
- ✅ Script management UI implemented (admin): `vmgen-frontend/src/views/admin/scripts/*` + editor components
- ✅ Script content SHA-256 hashing implemented on create/update
- ✅ Template–script compatibility validation implemented (`VmRequestService.createBuild()` + `ScriptCompatibilityService.resolveScriptSelection()` — conflict detection, dependency expansion, Kahn's-algorithm cycle detection, called at `/generate` submit time)
- ❌ Dry-run testing / sandbox execution not implemented
- ✅ User-facing script parameters: GET /api/scripts/{id}/parameters (ScriptController returns USER_INPUT / USER_INPUT_OR_RANDOM only for scenario configuration)
- ❌ Full user-facing script catalog (non-admin list/browse) not implemented

---

## Security Architecture

### 1. Authentication & Authorization ✅ IMPLEMENTED

**JWT Token Structure (HS256)**:

```
Header: {alg: HS256, typ: JWT}
Payload: {
  iss: "vmgen-api",
  sub: "<userId>",
  iat: <issuedAt>,
  exp: <expiresAt>,
  role: "user" | "admin",
  sid: "<sessionId>"
}
Signature: HMAC256(secret)
```

**TTL Configuration**:

- User tokens: 3600 seconds (1 hour)
- Admin tokens: 1800 seconds (30 minutes)

**Session Management**:

- Tokens stored as SHA-256 hash in database (not plaintext)
- Sessions tracked with expiry and last activity timestamps
- Automatic cleanup of expired/idle sessions (24h idle timeout, 5 min cleanup interval)
- Login revokes all previous sessions for the user (single-session enforcement)
- Logout explicitly revokes session

**Password Policy** (enforced by `PasswordPolicyValidator`):

- Minimum 12 characters
- At least 1 uppercase letter
- At least 1 lowercase letter
- At least 1 digit
- At least 1 symbol
- Hashed with Argon2id (`SecurityBeansConfig`)

**Role-Based Access Control**:

| Role | Permissions |
|------|-------------|
| `USER` | View active templates, submit build requests, view own builds |
| `ADMIN` | All user permissions + register/validate/deprecate templates, manage sessions, promote/demote users, access Proxmox API |

**Worker API Authentication**:

- Workers use `X-Worker-Api-Key` header (separate from JWT)
- `WorkerApiKeyFilter` validates header with constant-time comparison
- Only applies to `/api/worker/**` endpoints

**Attack Prevention**:

| Attack | Defense |
|--------|---------|
| Timing attacks | Constant-time password verification + API key comparison (`TokenHashUtil`) |
| Brute force | Rate limiting: 5 login attempts per IP per 5 min |
| Token reuse | Session tokens stored as SHA-256 hashes; revoked on logout/new login |
| CSRF | Disabled (stateless JWT, not form-based) |
| SQL injection | JPA parameterized queries throughout |
| Session fixation | New session on each login; all old sessions revoked |

### 2. Cryptographic Flag Generation (FR10)

**HMAC-SHA256 Derivation** (From Thesis):

```
Inputs:
  k          = 256-bit platform secret (backend-only, never shared with workers)
  seed       = 256-bit random value (hex-encoded, 64 chars)
  request_id = UUID v4 (hyphenated string, 36 chars)
  script_id  = Script UUID (hyphenated string, 36 chars)
  timestamp  = ISO 8601 UTC timestamp at flag generation

Derivation:
  message = seed || request_id || script_id || timestamp
  flag_raw = HMAC-SHA256(k, message)
  flag_130bit = truncate(flag_raw, 130 bits)  # First 17 bytes, zero low 6 bits of byte 16 (& 0xC0)
  flag_encoded = base32(flag_130bit)          # 28 characters (A-Z, 2-7), always ends "AA"
  flag_final = "FLAG{" + flag_encoded + "}"
```

**Storage**: SHA-256(flag) stored in `flags.flag_value_hash` — never plaintext

**Implementation Status**:

- ✅ Database schema for flags (flag_value_hash, flag_derivation_timestamp, key_version)
- ✅ JPA entity `Flag` with hash-based storage design
- ✅ Flag derivation: server-side via `WorkerService.deriveRawFlag()` — worker requests derived flags and VM password from backend via `POST /api/worker/jobs/{id}/derive-flags` (`DeriveFlagsRequest`/`DeriveFlagsResponse` DTOs)
- ✅ Flag placement: piped via stdin to `sudo tee` — value never in command string (`ssh/executor.py:write_remote_file`)
- ✅ Flag hash verification: backend independently re-derives hash in `WorkerService.deriveFlagHash()` and constant-time compares against worker-submitted hash before storing
- ✅ Flag hashes stored in `flags` table via `WorkerService.completeJob`
- ✅ VM login password: HMAC-SHA256(platform_secret, `"vm-login:"+seed+job_id`) — derived server-side, returned alongside flags
- ✅ Flag submission endpoint (`POST /api/builds/{id}/flag`) — `BuildController` → `FlagService`
- ✅ FlagService — constant-time hash comparison, 5 attempts/build, account lockout after 3 exhausted builds (1h)

### 3. Build-Time Isolation (NFR4)

**Implementation Status**:

- ✅ `vmbr2` (BUILD) isolated bridge/subnet, separate from `vmbr1` management — genuinely routed and filtered by pfSense (fixed 2026-08-13, previously flat/shared with `vmbr1` and not actually enforced — see Network Isolation section)
- ✅ pfSense default-deny egress with explicit allow for Worker SSH, DNS/NTP, and HTTP/HTTPS
- ⚠️ Worker startup check (`verify_network_isolation()`) only verifies the Proxmox host itself has no gateway on that bridge — not a guest-level isolation guarantee, see note above
- ✅ Stage 11 (REMOVE_NETWORK) — all VM network interfaces stripped before OVA export
- ⚠️ Internet access during provisioning is permanent (not revoked before script execution) — known limitation, documented in NFR4
- ⚠️ Build-to-build isolation not enforced — all build VMs share the `vmbr2` subnet, same accepted limitation class as above
- ❌ Automated network monitoring not implemented

See [Deployment Architecture](#deployment-architecture) for full network topology.

### 4. Audit Logging (NFR2)

**Logged Events** ✅ IMPLEMENTED:

- Login attempts (success/failure, IP, user agent, timestamp)
- Logout events
- Administrative actions (template registration, role changes)
- Session lifecycle (created, revoked)

**Also Logged** ✅:

- Worker job completion and failure events (`auditService.logWorkerJobCompleted/Failed`)

**Not Logged** ❌:

- Build submissions
- Script uploads/modifications
- Flag submissions ✅ (now logged via `AuditService.logFlagSubmitted` — correct/rejected events, account lockout events)
- OVA downloads
- Provenance queries

---

## Workflow Specifications

### User Workflow: VM Generation (FR1, FR2, FR4)

```
1. AUTHENTICATE ✅
   └─ Frontend: POST /api/auth/login
      Backend: Validates credentials, issues JWT, revokes previous sessions

2. CONFIGURE SCENARIO ✅
   Frontend: /generate page
   ├─ Select template from catalog ✅
   ├─ Select vulnerability scripts ✅ (template-compatible scripts from GET /api/templates/{id}/scripts)
   ├─ Set parameters (difficulty, telemetry) ✅
   └─ Backend enforces template–script compatibility on submit ✅

3. SUBMIT BUILD ✅
   Frontend: POST /api/builds
   Backend (VmRequestService.createBuild):
   ├─ Validates template exists and is ACTIVE
   ├─ Validates all scripts exist and are active
   ├─ Validates template–script compatibility (if template has explicit compatibility entries, all scripts must be in that set)
   ├─ Generates 256-bit build seed (hex)
   ├─ Creates vm_request (state='QUEUED'), vm_request_scripts, vm_request_script_parameters
   └─ Returns BuildResponse
   Note: Script–script conflict/dependency validation is enforced at submit via `resolveScriptSelection` in `VmRequestService.createBuild`.

4. MONITOR PROGRESS ✅
   Frontend: Polls GET /api/builds/{id} (useBuildStatus composable)
   Backend: BuildController returns build state, worker stage, timestamps
   Worker: PATCH /api/worker/jobs/{id}/progress (endpoint ready)
   Stale detection: ScheduledTasksService fails builds with no progress >10 min

5. DOWNLOAD OVA ✅
   Frontend: GET /api/builds/{id}/download
   Backend: BuildController.downloadOva()
   ├─ Verifies state='READY'
   ├─ Verifies user owns request
   ├─ Streams OVA via FileSystemResource with Content-Disposition header
   └─ Returns X-Checksum-SHA256 header

6. EXPLOIT LOCALLY ✅ (No Platform Involvement)
   User imports OVA into VirtualBox/VMware/Proxmox
   VM runs on user infrastructure
   User exploits vulnerabilities
   User retrieves flag from compromised system

7. SUBMIT FLAG ✅ (FlagService implemented)
   Frontend: POST /api/builds/{id}/flag
   Body: { "flagValue": "FLAG{ABCD...}" }
   Response: { "correct": true/false, "attemptsRemaining": 0-5 }
   Backend:
   ├─ Checks account lockout (user.lockedUntil)
   ├─ Verifies build ownership and READY state
   ├─ Checks per-build attempt limit (5 max, tracked in vm_requests.failed_flag_attempts)
   ├─ Normalises flag (trim + uppercase)
   ├─ SHA-256 hash of submitted value, constant-time compare against all stored flag hashes
   ├─ On match: audits FLAG_SUBMITTED, returns correct=true
   └─ On miss: increments failed_flag_attempts; if ≥5 and user has ≥3 exhausted builds → 1h lockout
```

### Worker Workflow: Build Execution

**Status**: ✅ Fully implemented — backend endpoints ready, worker polls and executes complete 16-stage pipeline

**Atomic Job Claiming Flow** ✅:

```
Worker: POST /api/worker/jobs/claim {workerId: "worker-01"}

WorkerController → WorkerService.claimNextJob(workerId)
  → VmRequestRepository.claimNextJobUpdate(workerId)
    SQL: UPDATE vm_requests
         SET state='BUILDING', claimed_by_worker_id=?
         WHERE state='QUEUED'
         ORDER BY submitted_at ASC LIMIT 1
         FOR UPDATE SKIP LOCKED
  → Returns 200 with ClaimJobResponse or 204 No Content
```

**Progress Updates** ✅:

```
Worker: PATCH /api/worker/jobs/{id}/progress
  {workerId, stage, message, percent}

WorkerService.updateProgress():
  1. Verify worker owns job (claimedByWorkerId match)
  2. Update currentWorkerStage, lastProgressAt, workerLastHeartbeatAt
  3. Auto-transition to EXPORTING if stage == "exporting"
```

**Job Completion** ✅:

```
Worker: POST /api/worker/jobs/{id}/complete
  {workerId, ovaPath, ovaChecksum, ovaSizeBytes, provenance, vmLoginPassword, flags}

WorkerService.completeJob():
  1. Verify worker ownership
  2. Validate state transition via state machine
  3. Set state=READY, store artifact info (path, checksum, size)
  4. Persist ProvenanceRecord + ProvenanceScript records
  5. Re-derive flag hashes independently, constant-time compare against worker-submitted hashes
  6. Store verified flag hashes in flags table
  7. Update template SHA-256 from "PENDING" if first build against template
```

**Job Failure** ✅:

```
Worker: POST /api/worker/jobs/{id}/fail
  {workerId, errorType, message}

WorkerService.failJob():
  1. Optional worker ownership check
  2. Validate state can transition to FAILED
  3. Set state=FAILED, record reason
```

**Stale Build Detection** ✅ (automatic):

```
ScheduledTasksService runs every 60 seconds:
  → WorkerService.markStaleJobsAsFailed(Duration.ofMinutes(10))
    → Finds jobs in BUILDING/EXPORTING with no progress update >10 min
    → Marks as FAILED with reason "TIMEOUT: No progress update for 10 minutes"
```

---

### Admin Workflow: Template Management ✅ IMPLEMENTED

```
1. BROWSE PROXMOX VMs ✅
   GET /api/admin/proxmox/vms?node=pve
   → Lists VMs with template detection

2. REGISTER TEMPLATE ✅
   POST /api/admin/templates
   ├─ Validates identifier+version uniqueness
   ├─ Verifies Proxmox VM existence
   └─ Creates template with status=DRAFT

3. VALIDATE TEMPLATE ✅
   POST /api/admin/templates/{id}/validate
   ├─ Verifies Proxmox VM still accessible
   ├─ Fetches VM details
   └─ Sets status=ACTIVE, validatedAt, validatedBy

4. DEPRECATE TEMPLATE ✅
   PATCH /api/admin/templates/{id}/deprecate
   └─ Sets status=DEPRECATED (hidden from user catalog)

5. UPDATE METADATA ✅
   PATCH /api/admin/templates/{id}/metadata
   └─ Updates mutable fields (description, etc.)
```

### Admin Workflow: Script Management (FR6)

**Status**: 🚧 PARTIALLY IMPLEMENTED

Implemented:

- ✅ Admin script CRUD (`/api/admin/scripts`)
- ✅ Script compatibility rules (conflicts/dependencies)
- ✅ Script parameter definitions (typed parameters + choices)
- ✅ Admin UI pages for scripts
- ✅ Template–script compatibility model, enforced at `/generate` submit (`VmRequestService.createBuild()` + `ScriptCompatibilityService`)
- ✅ User-facing script parameters endpoint (GET /api/scripts/{id}/parameters)

Not yet implemented:

- ❌ Dry-run execution/testing pipeline
- ❌ Full user-facing script catalog (list/browse) not implemented

---

## Implementation Status Summary

### ✅ Phase 0: Foundation (Complete)

| Component | Status | Notes |
|-----------|--------|-------|
| Database schema | ✅ Complete | V1-V10 migrations applied (categories, script compatibility, script parameters added) |
| JWT authentication | ✅ Complete | HS256, session tracking, SHA256 token hash |
| Password security | ✅ Complete | Argon2id hashing, 12+ char policy, complexity rules |
| User management | ✅ Complete | Registration, role assignment |
| Session management | ✅ Complete | List active, revoke, auto-cleanup (24h idle) |
| Template registration | ✅ Complete | Register, validate, deprecate, edit |
| Proxmox API integration | ✅ Complete | List VMs, get config, verify existence |
| Admin UI | ✅ Complete | Users, sessions, templates, scripts, categories |
| Audit logging | ✅ Partial | Auth events only |
| Rate limiting | ✅ Complete | Login: 5 attempts / 5 min per IP |
| Frontend SPA | ✅ Complete | Vue 3, routing, auth, composables |

### ✅ Phase 0.5: Worker Coordination (Complete)

| Component | Status | Notes |
|-----------|--------|-------|
| Worker API endpoints | ✅ Complete | heartbeat, claim, progress, complete, fail, logs |
| Worker API key auth | ✅ Complete | X-Worker-Api-Key with constant-time comparison |
| Atomic job claiming | ✅ Complete | FOR UPDATE SKIP LOCKED, single-row claim |
| VmRequest state machine | ✅ Complete | QUEUED→BUILDING→EXPORTING→READY/FAILED→ARCHIVED |
| Progress tracking | ✅ Complete | Stage, message, heartbeat timestamp |
| Stale build detection | ✅ Complete | 10 min timeout, auto-fail, 1 min check interval |
| Long-running build detection | ✅ Complete | 3h wall-clock timeout, 5 min check interval |
| Worker liveness monitoring | ✅ Complete | Heartbeat every 60s; stale worker warning after 180s |
| VmRequest JPA entity | ✅ Complete | Full lifecycle fields, worker tracking |
| Worker DTOs | ✅ Complete | ClaimJob, Progress, Complete, Fail, Log, Heartbeat |

### ✅ Phase 1: Build System (Complete)

| Component | Status | Notes |
|-----------|--------|-------|
| Build request API (user) | ✅ Complete | POST /api/builds (BuildController, VmRequestService) |
| Build status API (user) | ✅ Complete | GET /api/builds, GET /api/builds/{id} |
| OVA download API | ✅ Complete | GET /api/builds/{id}/download — authenticated streaming with SHA-256 header |
| Worker job processing | ✅ Complete | Full 16-stage pipeline in `build/pipeline.py` |
| Proxmox integration | ✅ Complete | Clone, start/stop, guest agent IP, delete via `proxmox/client.py` |
| SSH provisioning | ✅ Complete | Ephemeral RSA keys, cloud-init injection, jump-host tunnelling (`ssh/executor.py`) |
| Script execution | ✅ Complete | Content piped to `bash -s` stdin — no temp file |
| Network isolation | ✅ Complete | `vmbr2` (BUILD) bridge/subnet + pfSense, genuinely routed; build-to-build isolation not covered (accepted gap) |
| Build monitoring UI | ✅ Complete | Frontend polls GET /api/builds/{id} |
| Build log appending | ✅ Complete | Worker fire-and-forget logs via `POST /api/worker/jobs/{id}/logs` |

### ✅ Phase 2: Vulnerability Injection (Complete)

| Component | Status | Notes |
|-----------|--------|-------|
| Script management API | ✅ Complete | Admin CRUD + compatibility + parameters |
| Script management UI | ✅ Complete | Admin interface |
| Script-template compatibility | ✅ Complete | Enforced at build submit; admin UI to manage |
| Script conflict/dependency at submit | ✅ Complete | `resolveScriptSelection` called in `VmRequestService.createBuild`; rejects conflicts, expands dependencies |
| Dry-run testing | ❌ Not started | Out of scope for thesis |

### ✅ Phase 3: Validation & Provenance (Mostly Complete)

| Component | Status | Notes |
|-----------|--------|-------|
| Flag derivation | ✅ Complete | Server-side via `WorkerService.deriveRawFlag()`; worker calls `POST /derive-flags` |
| Flag placement | ✅ Complete | Stdin-piped via `sudo tee`; value never in command string |
| Flag hash verification | ✅ Complete | Backend re-derives independently and constant-time compares |
| Flag hashes stored | ✅ Complete | `flags` table, SHA-256 only — never plaintext |
| VM login password | ✅ Complete | Server-side derivation; returned alongside flags via `derive-flags` endpoint |
| Flag submission endpoint | ✅ Complete | POST /api/builds/{id}/flag — FlagService, BuildController |
| Flag attempt counting + lockout | ✅ Complete | 5 attempts/build; 3 exhausted builds → 1h lockout |
| Provenance assembly | ✅ Complete | `assemble_provenance()` in worker + `WorkerService.persistProvenance()` |
| OVA SHA-256 | ✅ Complete | Chunked computation in `build/provenance.py` |
| Template SHA-256 | ✅ Complete | SSH `qm config` hash; `"PENDING"` until first build; auto-updated on completion |
| Script SHA-256 | ✅ Complete | Computed on script create/update in `ScriptService` |
| Provenance JSON export | ✅ Complete | GET /api/builds/{id}/provenance |

### ✅ Phase 3.5: Platform Quality Features (Complete)

| Component | Status | Notes |
|-----------|--------|-------|
| Health check endpoint | ✅ Complete | `GET /actuator/health` — public, `show-details=never`, `spring-boot-starter-actuator` |
| Resumable OVA download | ✅ Complete | `GET /api/builds/{id}/download` — RFC 7233 `Range` header, `206 Partial Content`, `ResourceRegion` |
| Build retry (clone failed build) | ✅ Complete | `POST /api/builds/{id}/clone` — reconstructs build from failed state; auto-generated params re-randomised |
| Retry button (frontend) | ✅ Complete | `BuildStatusPage.vue` failed state card — navigates to new build on success |

### 🚧 Phase 4: Advanced Features (Partial / Future)

| Component | Status | Notes |
|-----------|--------|-------|
| Guest telemetry | 🚧 Planned | `telemetryEnabled` field exists; pipeline stage not yet wired |
| Build log streaming | ❌ Not started | SSE or WebSocket; logs available via polling |
| Signed provenance | ❌ Not started | Digital signatures for non-repudiation |
| Admin operational dashboard | ❌ Not started | Queue depth, avg wait time, completion rate metrics |
| Advanced monitoring | ❌ Not started | Grafana, Prometheus integration |

---

## Deployment Architecture

### Minimum Requirements (From Thesis NFR)

**Network Isolation** (MANDATORY):

- Build VMs on isolated network segment
- Default-deny egress policy
- SSH-only ingress from Worker
- No routing to management networks

**Resource Allocation**:

- Build VM: 2 CPU cores, 2GB RAM (4GB for Windows)
- Backend: 1 CPU core, 1GB RAM
- Database: 1 CPU core, 2GB RAM, 50GB storage
- Worker: 2 CPU cores, 2GB RAM
- OVA storage: ~500GB for 100 concurrent builds

**Component Isolation**:

- Database NOT accessible from Frontend
- Build VMs CANNOT reach Backend API
- Worker authenticates to Backend via API key (not JWT)
- Worker writes via Backend API (state updates, provenance)

**Credential Separation**:

- Backend DB credentials (read-write)
- Hypervisor service account (VM operations only)
- Worker API key (Backend communication)
- Platform secret (flag derivation, >=32 chars)
- Ephemeral SSH keys (per-build, destroyed post-export)

### Reference Deployment

```
┌─────────────────────────────────────────────────────────────┐
│                    Management Network                       │
│                      (vmbr1 / VLAN 10)                      │
│                                                             │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌─────────┐│
│  │ Frontend │   │ Backend  │   │ Database │   │ Worker  ││
│  │  VM/Pod  │──▶│  VM/Pod  │──▶│  VM/Pod  │   │ VM/Pod  ││
│  └──────────┘   └──────────┘   └──────────┘   └────┬────┘│
│                       ▲                              │     │
│                       │ X-Worker-Api-Key             │     │
│                       └──────────────────────────────┘     │
└──────────────────────────────────────────────────────┼─────┘
                                                       │
                          ┌────────────────────────────┘
                          │
                          │ Proxmox API
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    Proxmox Hypervisor                       │
│                                                             │
│  ┌───────────────────────────────────────┐                │
│  │       Build Network (vmbr2, BUILD)    │                │
│  │  *** OWN SUBNET, ROUTED+FILTERED ***   │                │
│  │  (pfSense BUILD iface = real gateway) │                │
│  │                                        │                │
│  │  ┌──────────┐  ┌──────────┐  ┌───────────────┐       │
│  │  │ Build VM │  │ Build VM │  │  Build VM     │       │
│  │  │    #1    │  │    #2    │  │     #3        │       │
│  │  └──────────┘  └──────────┘  └───────────────┘       │
│  │        ▲             ▲              ▲                  │
│  │        └─────────────┴──────────────┘                  │
│  │         SSH from Worker ONLY (port 22)                 │
│  └───────────────────────────────────────┘                │
│                                                             │
│  ┌──────────────────────────────────────┐                │
│  │     Template Storage (local-lvm)     │                │
│  │  • ubuntu-22.04-base-v1.0.0.qcow2   │                │
│  │  • debian-11-minimal-v1.3.2.qcow2   │                │
│  └──────────────────────────────────────┘                │
│                                                             │
│  ┌──────────────────────────────────────┐                │
│  │     OVA Export Storage (local-nvme)  │                │
│  │  • /exports/{request_id}.ova         │                │
│  └──────────────────────────────────────┘                │
└─────────────────────────────────────────────────────────────┘
```

### Network Setup (Reference Implementation)

**Step 1: Proxmox Network Bridges** — edit `/etc/network/interfaces` on the Proxmox host:

```
# School network uplink (existing physical bridge)
auto vmbr0
iface vmbr0 inet dhcp
    bridge-ports enp0s31f6
    bridge-stp off
    bridge-fd 0

# Internal management bridge — NO physical ports
auto vmbr1
iface vmbr1 inet static
    address 192.168.1.2/24
    bridge-ports none
    bridge-stp off
    bridge-fd 0

# Internal BUILD bridge — NO physical ports, distinct subnet from vmbr1
auto vmbr2
iface vmbr2 inet static
    address 192.168.2.2/24
    bridge-ports none
    bridge-stp off
    bridge-fd 0
```

Apply: `ifreload -a` (Proxmox uses `ifupdown2`; no reboot needed)

**Step 2: pfSense VM** — three interfaces: WAN on `vmbr0`, LAN on `vmbr1` (`192.168.1.254/24`, DHCP for management), BUILD on `vmbr2` (`192.168.2.254/24`, DHCP `192.168.2.10–192.168.2.250`, real gateway = the interface's own address, NTP server address also handed out via the DHCP scope's NTP option).

**Step 3: pfSense firewall rules**, split across two interface tabs — pfSense filters on the interface traffic *enters*, so LAN-originated and BUILD-originated traffic need separate rules:

**LAN tab:**

| Rule | Action | Source | Destination | Purpose |
|------|--------|--------|-------------|---------|
| 1 | Pass TCP/22 | 192.168.1.2 | BUILD subnet | Proxmox SSH jump host → build VMs |
| 2 | Block | 192.168.1.10 | BUILD subnet | Platform VM has no direct path to build VMs — worker SSH always tunnels via the Proxmox jump host above, never directly |

**BUILD tab** (order matters — first match wins):

| Rule | Action | Source | Destination | Purpose |
|------|--------|--------|-------------|---------|
| 1 | Pass UDP/123 | BuildVMs | 192.168.2.254 | NTP |
| 2 | Block | BuildVMs | 192.168.1.10 | Build VMs cannot reach the platform VM |
| 3 | Pass TCP+UDP/53 | BuildVMs | 192.168.2.254 | DNS |
| 4 | Block | BuildVMs | 192.168.2.254 | Build VMs cannot reach pfSense's own admin interface |
| 5 | Block | BuildVMs | 192.168.1.0/24 | Build VMs cannot reach any other management-subnet host (closes a gap the HTTP/HTTPS rule below would otherwise leave open) |
| 6 | Pass TCP/80,443 | BuildVMs | Any | Package installation during provisioning |
| 7 | Block | BuildVMs | Any | Default deny all other egress |

Note: build-to-build traffic (two build VMs on the same `vmbr2` subnet) is **not** covered by any of this — same-subnet peers bypass pfSense entirely via link-layer switching, an accepted gap, not yet fixed. See the Build-Time Isolation (NFR4) section above.

**Step 4: Proxmox `vmgen` user** (worker SSH access):

```bash
useradd -m -s /bin/bash vmgen
# Add to sudoers (NOPASSWD, restricted to the commands the OVA export pipeline runs):
echo 'vmgen ALL=(root) NOPASSWD: /usr/sbin/qm config *, /usr/sbin/pvesm path *, /usr/bin/qemu-img convert *' \
  >> /etc/sudoers.d/vmgen
# Install worker SSH key
mkdir -p /home/vmgen/.ssh
cat vmgen_worker.pub >> /home/vmgen/.ssh/authorized_keys
```

---

### Docker Deployment (Implemented)

Full containerised deployment lives in `vmgen/docker/`: `docker-compose.yml`, `Dockerfile.api`, `Dockerfile.frontend`, `Dockerfile.worker`, `nginx.conf`, and a documented `.env.example`.

**Services** (`docker-compose.yml`):

| Service | Image / Build | Purpose |
|---------|---------------|---------|
| `postgres` | `postgres:16` | Primary datastore. Optional restricted `DB_API_USERNAME` role (DML-only) is created on first init if `DB_API_PASSWORD` is set; otherwise the API connects as the owner role |
| `vmgen-api` | `Dockerfile.api` | Spring Boot backend. Healthcheck: `GET /actuator/health` |
| `vmgen-frontend` | `Dockerfile.frontend` | nginx serving the built Vue SPA, which **also** reverse-proxies `/api/` and `/actuator/` to `vmgen-api:8080` (`nginx.conf`) — there is no separate nginx container |
| `vmgen-worker` | `Dockerfile.worker` | Python build automation worker. Not exposed on any port; connects outbound only, via SSH to Proxmox and HTTP to `vmgen-api` |

Volumes: `vmgen_pgdata` (Postgres data), `ova_storage` (shared OVA export volume between `vmgen-api` and `vmgen-worker`, or a bind-mounted host directory in `shared` export mode).

**Key deploy behaviours**:

- **Core stack runs without Proxmox**: `ProxmoxApiConfig.validate()` only checks that `PROXMOX_*` values are non-blank, so placeholder values let `postgres` + `vmgen-api` + `vmgen-frontend` come up standalone — `docker compose up -d --build postgres vmgen-api vmgen-frontend`. Only `vmgen-worker` needs a real Proxmox host and SSH key.
- **CORS must match exactly**: `VMGEN_CORS_ORIGINS` has to equal the exact browser URL (scheme + host + port) or login fails with a CORS error.
- **First-admin bootstrap is manual SQL**: roles are `USER`/`ADMIN`, registration always defaults to `USER`, and the promotion endpoint itself requires `ADMIN` (chicken-and-egg). Promote the first account directly against the database, then log out and back in — role is embedded in the JWT at login time:

  ```bash
  docker compose exec postgres psql -U $DB_USER -d $DB_NAME \
    -c "UPDATE users SET role='ADMIN' WHERE email='you@example.com';"
  ```

- **Worker SSH key must pre-exist on disk**: `PROXMOX_SSH_KEY_PATH` / `PROXMOX_KNOWN_HOSTS_PATH` must point to real files, chmod `0600`, before `docker compose up vmgen-worker` — otherwise Docker silently creates empty directories at those paths instead of mounting the key.
- **Worker fails closed**: at startup it verifies via the Proxmox API that the isolated build bridge (`PROXMOX_ISOLATED_BRIDGE` — code default is `vmbr1`, but the deployed `.env` must set this to `vmbr2`) has no gateway *on the Proxmox host's own interface config*. No Proxmox reachability = `exit(1)` and restart-loop — expected until Proxmox is reachable. This check does not verify guest-level isolation; that's entirely the pfSense ruleset's job.
- **OVA export has two transfer modes** (`EXPORT_TRANSFER_MODE`): `sftp` (default — the worker copies the OVA off the Proxmox host over SFTP; works when the worker is a different machine than Proxmox) or `shared` (Proxmox writes the OVA straight onto storage also mounted into the worker/API via `PROXMOX_SHARED_EXPORT_DIR`, skipping the copy — used when the worker is co-located on the Proxmox host).

Full field-by-field reference (every variable, generation commands, and the deploy gotchas above): [`docker/.env.example`](../docker/.env.example).

---

## Remaining Work

### Medium-Term Priority

2. **Build log streaming** ❌
   - SSE or WebSocket; logs currently available via polling only

3. **Signed provenance** ❌
   - Digital signatures for non-repudiation

---

## References

### Thesis Sections

- Chapter: Architecture (Complete specification)
- FR1-FR14: Functional Requirements
- NFR1-NFR14: Non-Functional Requirements

### Related Documentation

- [OPERATIONS.md](./OPERATIONS.md) — Day-to-day commands for running and debugging the platform
- [components/](./components/) — Per-component walkthroughs (backend, frontend)
- [diagrams/](./diagrams/) — Architecture, sequence and ER diagrams
- [brain/](./brain/) — Interactive architecture explorer

---

**Document Version**: 2.4
**Last Updated**: July 2026
**Based On**: VMGen Thesis Architecture Specification
**Implementation Status**: Phases 0–3.5 complete, Docker deployment complete (~99%); advanced features (Medium-Term Priority below) pending
**Maintained By**: VMGen Development Team
