# VMGen - Vulnerable VM Generation Platform

A platform for deterministic generation of vulnerable virtual machines for cybersecurity education and training. Build vulnerable training environments with full provenance tracking and isolated execution.

## Repository layout

| Path | Role |
|------|------|
| `vmgen-api/` | Spring Boot backend and Flyway migrations |
| `vmgen-frontend/` | Vue 3 + Vite SPA |
| `vmgen-worker/` | Python build worker (Proxmox + pipeline); see `vmgen-worker/SETUP.txt` |
| `docker/` | Full Docker Compose deployment (PostgreSQL, API, frontend, and worker) |

## Project concept

VMGen enables educators and security researchers to:

- **Generate Vulnerable VMs**: Create virtual machines with specific vulnerabilities for training purposes
- **Deterministic Builds**: Same configuration → same learning outcome (identical vulnerability, identical exploitation technique). Flags and timestamps differ per build by design — this is functional equivalence, not bit-for-bit replication
- **Provenance Tracking**: Complete audit trail of what was included in each build
- **Isolated Environments**: Each VM is self-contained and portable (OVA format)
- **Flag Management**: Secure HMAC-SHA256 flag generation and submission tracking for CTF-style exercises

### Key features

- **Template-Based System**: Start from validated base OS templates (Ubuntu, Debian, CentOS, Windows)
- **Vulnerability Scripts**: Apply vulnerability scripts in a controlled order
- **Build Orchestration**: Queue, build, and export VMs with progress tracking
- **Provenance Records**: Complete metadata about templates, scripts, and build parameters
- **User Management**: Role-based access control (user/admin) with audit logging
- **Session Management**: Secure JWT-based authentication with stateful sessions

## Architecture

### Frontend (`vmgen-frontend`)

- **Framework**: Vue 3 (Composition API) with TypeScript
- **Routing**: Vue Router 4
- **State Management**: Pinia (auth, confirmations, UI state)
- **Server state**: TanStack Vue Query
- **Styling**: Tailwind CSS v3+ with custom cybersecurity theme
- **Icons**: Lucide Vue Next
- **Build tool**: Vite

### Backend (`vmgen-api`)

- **Framework**: Spring Boot 4.0 (Java 21)
- **Security**: Spring Security 7 with JWT authentication (HS256; session tokens tracked server-side)
- **Database**: PostgreSQL 16 (via Docker for local dev)
- **ORM**: JPA/Hibernate
- **Migrations**: Flyway (`src/main/resources/db/migration`; consolidated baseline `V1__initial_schema.sql`, with older increments archived under `db/migration_legacy/`)
- **Password hashing**: Argon2id
- **Scheduling**: Spring Scheduling for session cleanup and stale build detection
- **API docs**: springdoc OpenAPI (Swagger UI) when enabled — see Quick Start

### Worker (`vmgen-worker`)

- **Language**: Python 3.14+
- **Proxmox integration**: `proxmoxer` (API) + `paramiko` (SSH)
- **Build pipeline**: Multi-stage pipeline — clone, provision, scripts, flags, OVA export, provenance
- **Flag derivation**: Requests raw flags from backend API (`POST /api/worker/jobs/{id}/derive-flags`); platform secret never leaves the backend
- **Concurrency**: `ThreadPoolExecutor` (configurable; default 3 parallel builds)
- **Metrics**: `psutil` for CPU/memory reporting via heartbeat

### Database

- **PostgreSQL 16** in Docker (local dev)

## Prerequisites

- **Java 21 JDK** (not JRE)
- **Node.js 20.19+ or 22.12+** and npm
- **Docker** and Docker Compose
- **Maven** (or use included `mvnw` / `mvnw.cmd` in `vmgen-api`)
- **IDE** (IntelliJ IDEA, VS Code, or Cursor recommended)

For running builds end-to-end you also need **Python 3.14+**, **Proxmox VE**, and worker-specific setup (`vmgen-worker/SETUP.txt`).

## Quick start

Typical local flow: database → API → frontend. The worker is optional until you run real builds.

### 0. Configure environment variables

The API loads an optional **properties-style** `.env` file from the **working directory** when you run the app (see `spring.config.import` in `application.properties`). You can instead export real env vars in your shell or IDE.

**Required for anything beyond trivial defaults:**

```properties
# JWT signing (minimum ~32 characters; use a strong random value in production)
VMGEN_JWT_SECRET=<min-32-char-random-string>

# Flag derivation secret (backend only — workers never receive it; they call /derive-flags)
PLATFORM_SECRET=<256-bit-hex-or-long-random-secret>

# Worker authentication — same value as the worker’s BACKEND_API_KEY
WORKER_API_KEY=<random-key-shared-with-workers>
```

**Database** — defaults match Docker Compose:

```properties
# JDBC URL (overrides host/port/db in one string)
DB_URL=jdbc:postgresql://localhost:5432/vmgen
DB_USERNAME=vmgen
DB_PASSWORD=vmgen
```

**Proxmox** (required for template validation, VM listing, and related admin flows):

```properties
# Create the API token in Proxmox VE: Datacenter → Permissions → API Tokens
# Token ID format: <user>@<realm>!<tokenname> — e.g. worker@pve!automation
PROXMOX_API_URL=https://<proxmox-host>:8006/api2/json
PROXMOX_TOKEN_ID=<user>@<realm>!<tokenname>
PROXMOX_TOKEN_SECRET=<token-secret-from-proxmox>
```

**Optional:**

```properties
# Comma-separated origins (default includes Vite dev server)
VMGEN_CORS_ORIGINS=http://localhost:5173

# Disable Swagger in production
SWAGGER_ENABLED=false
```

### 1. Start PostgreSQL for local development

The API expects PostgreSQL on `localhost:5432` by default. Start a local PostgreSQL 16 instance with the following development values, or override them through the environment variables shown above:

- **Database**: `vmgen`
- **Username / password**: `vmgen` / `vmgen`

The Compose deployment under `docker/` is intended to run the containerised platform as a stack; its PostgreSQL port is not published to the host. To run the complete stack instead of the local-development flow, copy `docker/.env.example` to `docker/.env`, replace every required `CHANGE_ME` value, and run:

```bash
cd docker
docker compose up -d --build
```

(On older Docker installs you may need `docker-compose` with a hyphen.)

**Connection URL** (IDEs, `psql`, scripts):

`postgresql://vmgen:vmgen@localhost:5432/vmgen`

**Reset database** (destructive — drops Compose volumes):

```bash
cd docker
docker compose down -v
docker compose up -d --build
```

### 2. Start the backend

**Option A — IDE**

- Open `vmgen-api` in your IDE
- Run `VMGenApiApplication.java`
- API base URL: `http://localhost:8080`

**Option B — Maven**

```bash
cd vmgen-api
./mvnw spring-boot:run
```

Windows:

```bash
cd vmgen-api
mvnw.cmd spring-boot:run
```

On startup the API connects to Postgres, runs Flyway, and listens on port **8080**.

**Swagger UI** (default local dev, unless `SWAGGER_ENABLED=false`):

- **http://localhost:8080/swagger-ui.html**

OpenAPI JSON is available under `/v3/api-docs` (springdoc defaults).

### 3. Start the frontend

```bash
cd vmgen-frontend
npm install   # first time only
npm run dev
```

Dev server: **http://localhost:5173** (Vite may pick another port if 5173 is busy).

The SPA calls the API on `localhost:8080`; ensure `VMGEN_CORS_ORIGINS` includes your dev origin if you change ports.

### 4. (Optional) Start the worker

Only needed for **real Proxmox builds** (not for browsing the UI or API-only testing). Copy `vmgen-worker/.env.example` → `.env`, set `BACKEND_API_KEY` to the same value as **`WORKER_API_KEY`** on the API (workers do not need `PLATFORM_SECRET`), then follow **`vmgen-worker/SETUP.txt`** (SSH key to Proxmox, `EXPORT_PATH`, etc.).

```bash
cd vmgen-worker
python -m worker.main
```

## Authentication

### Register a new user

1. Go to `/register`
2. Username (min 3 characters), email, password (min 12 characters; uppercase, lowercase, digit, and symbol required)
3. You are redirected to login

### Login

1. Go to `/login`
2. The API returns a JWT; the SPA keeps it **in memory only** (Pinia), not in `localStorage`, and attaches it to API requests — a full page refresh logs you out unless you sign in again

### Promote the first user to admin

There is no bootstrap SQL file in-repo; run SQL once against your database.

**Example** (replace the email):

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@example.com';
```

From the host, using the Compose Postgres container:

```bash
docker exec -it vmgen-postgres psql -U vmgen -d vmgen -c "UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@example.com';"
```

Or run the same statement through your preferred PostgreSQL client.

## License

See `LICENSE` for details.

---
