# VMGen - Vulnerable VM Generation Platform

A platform for generating deterministic, reproducible virtual machines for cybersecurity education and training. Build vulnerable training environments with full provenance tracking and isolated execution.

## Project Concept

VMGen enables educators and security researchers to:

- **Generate Vulnerable VMs**: Create virtual machines with specific vulnerabilities for training purposes
- **Deterministic Builds**: Same configuration always produces the same VM (reproducible)
- **Provenance Tracking**: Complete audit trail of what was included in each build
- **Isolated Environments**: Each VM is self-contained and portable (OVA format)
- **Flag Management**: Secure flag generation and submission tracking for CTF-style exercises

### Key Features

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
- **State Management**: Pinia (auth state, user preferences)
- **Server State**: VueQuery (@tanstack/vue-query)
- **Styling**: Tailwind CSS v3+ with custom cybersecurity theme
- **Icons**: Lucide Vue Next
- **Build Tool**: Vite

### Backend (`vmgen-api`)
- **Framework**: Spring Boot 4.0 (Java 21)
- **Security**: Spring Security 6 with JWT authentication
- **Database**: PostgreSQL 16 (via Docker)
- **ORM**: JPA/Hibernate
- **Migrations**: Flyway
- **Password Hashing**: Argon2id
- **Scheduling**: Spring Scheduling for session cleanup

### Database
- **PostgreSQL 16** running in Docker
- **pgAdmin** for database visualization (optional)

## Prerequisites

- **Java 21 JDK** (not JRE)
- **Node.js 18+** and npm
- **Docker** and Docker Compose
- **Maven** (or use included `mvnw` wrapper)
- **IDE** (IntelliJ IDEA, VS Code, or Eclipse recommended)

## Quick Start

### 1. Start the Database

```bash
cd vmgen-api
docker-compose up -d postgres
```

This starts PostgreSQL on `localhost:5432` with:
- Database: `vmgen`
- Username: `vmgen`
- Password: `vmgen`

**Optional**: Start pgAdmin for database visualization:
```bash
docker-compose up -d
```
Then access pgAdmin at `http://localhost:5050` (login: `admin@example.com` / `admin`)

### 2. Start the Backend

**Option A: Via IDE**
- Open `vmgen-api` in your IDE
- Run `VMGenApiApplication.java`
- Backend will start on `http://localhost:8080`

**Option B: Via Maven**
```bash
cd vmgen-api
./mvnw spring-boot:run
# or on Windows:
mvnw.cmd spring-boot:run
```

The backend will:
- Connect to PostgreSQL
- Run Flyway migrations automatically
- Start on port `8080`

### 3. Start the Frontend

```bash
cd vmgen-frontend
npm install  # First time only
npm run dev
```

The frontend will start on `http://localhost:5173` (or another port if 5173 is busy).

### 4. Access the Application

- **Frontend**: `http://localhost:5173`
- **Backend API**: `http://localhost:8080`
- **API Docs**: Not yet implemented

## Authentication

### Register a New User

1. Navigate to `/register`
2. Fill in:
   - Username (min 3 chars)
   - Email
   - Password (min 12 chars, must include uppercase, lowercase, digit, and symbol)
3. After registration, you'll be redirected to login

### Login

1. Navigate to `/login`
2. Use your username/email and password
3. You'll receive a JWT token (stored in localStorage)
4. Protected routes require authentication

### Promote to Admin

To promote your first user to admin:

1. Register a user via the frontend
2. Copy the SQL script from `vmgen-api/db/bootstrap/promote_initial_admin.sql`
3. Edit the email in the script to match your registered user
4. Run it in the database:

```bash
docker exec -it vmgen-postgres psql -U vmgen -d vmgen -f /tmp/promote_initial_admin.sql
```

Or via pgAdmin: Execute the SQL query directly.

## License

See `LICENSE` file for details.

---

