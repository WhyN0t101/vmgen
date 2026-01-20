# VMGen - Vulnerable VM Generation Platform

A platform for generating deterministic, reproducible virtual machines for cybersecurity education and training. Build vulnerable training environments with full provenance tracking and isolated execution.

## 🎯 Project Concept

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

## 🏗️ Architecture

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

## 📋 Prerequisites

- **Java 21 JDK** (not JRE)
- **Node.js 18+** and npm
- **Docker** and Docker Compose
- **Maven** (or use included `mvnw` wrapper)
- **IDE** (IntelliJ IDEA, VS Code, or Eclipse recommended)

## 🚀 Quick Start

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

## 🔐 Authentication

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

## 📁 Project Structure

```
vmgen/
├── vmgen-api/                 # Spring Boot backend
│   ├── src/main/java/         # Java source code
│   │   └── io/vmgen/api/
│   │       ├── config/        # Security, JWT, CORS config
│   │       ├── controller/    # REST endpoints
│   │       ├── dto/           # Data Transfer Objects
│   │       ├── exceptions/    # Custom exceptions
│   │       ├── models/        # JPA entities
│   │       ├── repository/    # Spring Data repositories
│   │       ├── service/       # Business logic
│   │       └── utils/         # Utilities (password, token hashing)
│   ├── src/main/resources/
│   │   ├── db/migration/      # Flyway migrations
│   │   └── application.properties
│   ├── db/bootstrap/          # Admin promotion script
│   └── docker-compose.yml     # PostgreSQL + pgAdmin
│
└── vmgen-frontend/            # Vue 3 frontend
    ├── src/
    │   ├── api/              # API client and endpoints
    │   ├── components/       # Vue components
    │   │   ├── ui/          # Base UI components
    │   │   ├── layout/      # Layout components
    │   │   ├── templates/   # Template selection
    │   │   └── vulnerabilities/ # Vulnerability selection
    │   ├── composables/      # VueQuery composables
    │   ├── router/           # Vue Router config
    │   ├── stores/           # Pinia stores (auth)
    │   ├── types/            # TypeScript types
    │   └── views/            # Page components
    └── public/               # Static assets
```

## 🔌 API Endpoints

### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - Login (returns JWT token)
- `POST /api/auth/logout` - Logout (revokes session)

### Admin Endpoints (require admin role)
- `GET /api/admin/sessions` - List all active sessions
- `POST /api/admin/sessions/revoke/:sessionId` - Revoke a session
- `POST /api/admin/sessions/revoke-all` - Revoke all sessions
- `PUT /api/admin/users/:userId/role` - Update user role

### Protected Endpoints (require authentication)
- `GET /api/templates` - List available templates *(not yet implemented)*
- `GET /api/vulnerabilities` - List available vulnerabilities *(not yet implemented)*
- `POST /api/builds` - Submit a build request *(not yet implemented)*
- `GET /api/builds/:requestId` - Get build status *(not yet implemented)*
- `GET /api/builds` - List user's builds *(not yet implemented)*

## 🗄️ Database Schema

Key tables:
- **users** - User accounts with roles (user/admin)
- **sessions** - Active JWT sessions with token hashes
- **audit_log** - Security audit trail
- **templates** - Base VM templates
- **scripts** - Vulnerability scripts
- **vm_requests** - Build requests and status
- **provenance_records** - Build provenance metadata
- **flags** - Generated flags for CTF exercises

See `vmgen-api/src/main/resources/db/migration/V1__initial_schema.sql` for full schema.

## 🚧 Current Status

### ✅ Implemented

- [x] User registration and authentication (JWT + sessions)
- [x] Role-based access control (user/admin)
- [x] Session management with revocation
- [x] Audit logging
- [x] Frontend authentication flow (login/register/logout)
- [x] Frontend UI components and routing
- [x] Database schema and migrations
- [x] Security hardening (Argon2id, constant-time comparisons, etc.)

### 🚧 In Progress / Planned

- [ ] Template management API
- [ ] Vulnerability script management API
- [ ] Build orchestration (VM generation)
- [ ] Build status tracking and polling
- [ ] OVA export functionality
- [ ] Provenance record generation
- [ ] Flag generation and submission
- [ ] Admin panel UI
- [ ] Telemetry viewer
- [ ] API documentation (OpenAPI/Swagger)

## 🔧 Development

### Backend Development

- **Hot Reload**: Spring Boot DevTools is included (restart on code changes)
- **Database Migrations**: Add new migrations to `src/main/resources/db/migration/`
- **Logging**: Check console output for Spring Boot logs
- **JWT Secret**: Change `vmgen.jwt.secret` in `application.properties` for production

### Frontend Development

- **Hot Reload**: Vite provides instant HMR (Hot Module Replacement)
- **Type Checking**: Run `npm run type-check` to verify TypeScript
- **Mock Data**: Currently using mock data (see `src/api/endpoints.ts` - `USE_MOCK = true`)

### Database Access

**Via pgAdmin**:
1. Start: `docker-compose up -d` (includes pgAdmin)
2. Access: `http://localhost:5050`
3. Connect to server `postgres` (host: `postgres`, port: `5432`)

**Via psql**:
```bash
docker exec -it vmgen-postgres psql -U vmgen -d vmgen
```

## 🔒 Security Features

- **Password Hashing**: Argon2id with secure defaults
- **JWT Authentication**: HS256 with configurable expiration
- **Session Management**: Stateful sessions with database-backed revocation
- **Token Hashing**: SHA-256 hashing of complete JWT tokens for revocation
- **Constant-Time Comparisons**: Prevents timing attacks
- **Audit Logging**: All authentication events logged
- **CORS**: Configured for frontend origins
- **Role-Based Access**: Admin endpoints protected

## 📝 Notes

- **JWT Secret**: Currently using a placeholder. **Change before production!**
- **Database**: Data persists in Docker volume `vmgen_pgdata`
- **Ports**: 
  - Backend: `8080`
  - Frontend: `5173` (Vite default)
  - PostgreSQL: `5432`
  - pgAdmin: `5050`

## 🤝 Contributing

This is a thesis project. Development guidelines:
- Follow existing code style
- Write meaningful commit messages
- Update this README when adding features
- Test authentication flows after changes

## 📄 License

See `LICENSE` file for details.

---

**Last Updated**: January 2026
