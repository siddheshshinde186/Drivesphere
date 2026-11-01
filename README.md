# DRIVESPHERE – SMART MOBILITY & FLEET INTELLIGENCE PLATFORM

DriveSphere is an enterprise-grade, full-stack smart mobility, autonomous telemetry, and fleet management platform engineered with **Java 21 LTS**, **Spring Boot 3.4.3**, **MySQL 8.x**, and a decoupled modern glassmorphic web frontend.

---

## 🌟 Key Capabilities & Features

1. **Role-Based Workspaces & Multi-Tenancy**:
   - **Customer Portal**: Vehicle inventory browsing, multi-attribute filtering, instant booking, driver accompaniment hiring, Razorpay test checkout, smart trip planner, live GPS tracking simulation, and post-trip reviews.
   - **Driver Partner Console**: Identity & driving credential submission, real-time **Driver Trust Score** transparency gauge, online/offline availability switch, trip dispatch acceptance, and completed journey logs.
   - **Fleet Manager Workspace**: Live operational KPIs (fleet utilization, active rentals, maintenance alerts), vehicle inventory management, work order maintenance scheduler, real-time regional Leaflet telemetry map, and Chart.js trend analytics.
   - **Administrator Governance**: Driver verification document queue (review & score license, police clearance, and medical records), user directory access toggling, platform reservation ledger, and CSV export.

2. **Free-First Architecture with Graceful Fallbacks**:
   - **Google Gemini 3.5 Flash-Lite AI**: Official API client integration with an intelligent domain heuristic fallback that generates vehicle recommendations and concierge chat responses even if no API key is provided.
   - **Open-Meteo Free Weather API**: Real-time destination temperature, cloud condition, and driving advisory without any API key required.
   - **Spatial Routing & Maps**: Leaflet.js + OpenStreetMap with OpenRouteService and fallback geodesic waypoint interpolation.
   - **Razorpay Test Payment Sandbox**: Supports Razorpay test checkout and auto-verifying Sandbox Mock mode for zero-cost academic testing.

---

## 🔒 Security & Production Authentication

DriveSphere enforces production-grade security standards across all environments:
- **Zero Default Passwords**: Production repositories and deployments contain NO pre-seeded hardcoded administrative or demo accounts.
- **Mandatory Email Verification**: All user registrations initiate a single-use token sent via SMTP. Accounts cannot authenticate or transact until email verification is confirmed.
- **Single-Use Password Reset**: Cryptographically hashed SHA-256 tokens with short expiration windows and automated refresh token revocation upon password modification.
- **Driver Heartbeat & Stale Session Enforcement**: Automated background scheduler invalidates stale driver sessions if heartbeats lapse past 5 minutes, preventing invalid dispatches.
- **Strict Concurrency & Revalidation**: All vehicle reservations execute concurrency checks with HTTP 409 Conflict return semantics on driver/vehicle collision.
- **Flyway Database Governance**: Single source of truth schema evolution (V1–V5) ensuring automated, non-destructive schema migrations.

---

## 📐 Architecture & Package Hierarchy

```
Drivesphere/
├── backend/
│   ├── src/main/java/com/drivesphere/
│   │   ├── config/            # CORS, WebMvc, OpenAPI, DataInitializer
│   │   ├── security/          # Spring Security 6, JWT Filter, LoginRateLimiter
│   │   ├── controller/        # REST Controllers (Auth, Vehicle, Driver, Booking, Trip, etc.)
│   │   ├── service/           # Domain Services (Auth, Booking, Payment, Driver, TrustScore, Email, etc.)
│   │   ├── repository/        # Spring Data JPA Repositories (with locking and index optimizations)
│   │   ├── entity/            # JPA Entities (User, Driver, Vehicle, Booking, Tokens, etc.)
│   │   ├── dto/               # Request & Response DTOs
│   │   ├── mapper/            # Entity <-> DTO converters
│   │   ├── exception/         # ProblemDetails Global Exception Handler
│   │   └── integration/       # External Clients (Gemini, Maps, Weather, Razorpay, Cloudinary)
│   ├── src/main/resources/
│   │   ├── db/migration/      # Flyway Migrations (V1 to V5)
│   │   ├── application.properties
│   │   └── application-prod.properties
│   ├── src/test/java/         # Unit, Security, and Production-Readiness Test Suites
│   ├── mvnw.cmd               # Maven Wrapper executable
│   └── pom.xml                # Maven Dependencies (Spring Boot 3.4.3, Java 21)
├── frontend/
│   ├── customer/index.html    # Customer Workspace
│   ├── driver/index.html      # Driver Partner Console
│   ├── fleet-manager/index.html # Fleet Manager Console
│   ├── admin/index.html       # Admin Dashboard Console
│   ├── login.html             # Secure Login Portal (with Forgot & Reset Password)
│   ├── register.html          # Dual Registration (Customer / Driver with document upload)
│   ├── terms.html             # Terms and Conditions
│   ├── privacy.html           # Privacy Policy
│   ├── refund-policy.html     # Tiered Cancellation and Refund Policy
│   ├── index.html             # Public Showcase & Hero Page
│   └── assets/
│       ├── css/style.css      # DriveSphere Dark Mobility Design System
│       └── js/
│           ├── api.js         # HTTP Client with automatic Bearer token injection
│           ├── auth.js        # Auth state guards
│           └── theme.js       # Light/Dark mode switcher
├── docker-compose.yml         # Development Docker Compose
├── docker-compose.prod.yml    # Production Docker Compose (Private DB & Backend, Public Frontend)
├── .env.example               # Template for required environment variables
└── README.md
```

---

## 🛠️ Deployment & Getting Started

### Prerequisites
- **Java 21 LTS** (JDK 21+)
- **MySQL 8.x** or **Docker & Docker Compose**
- **SMTP Credentials** (for email verification and password resets)

### Option 1: Production Deployment (Docker Compose)
1. Copy `.env.example` to `.env` and configure your production secrets:
   ```bash
   cp .env.example .env
   ```
2. Launch the isolated production stack (Private MySQL, Private Spring Boot backend, and Frontend reverse proxy on port 80):
   ```bash
   docker compose -f docker-compose.prod.yml up -d --build
   ```
   *Note: Database migrations run automatically via Flyway on backend startup. No manual SQL execution required.*

### Option 2: Local Development Setup
1. **Database Startup**:
   Start the development MySQL instance:
   ```bash
   docker compose up -d
   ```
2. **Start Spring Boot Backend**:
   ```bash
   cd backend
   .\mvnw.cmd spring-boot:run
   ```
   *For optional mock/sample data in local development only, set `DRIVESPHERE_DEMO_DATA_ENABLED=true` in your development environment.*

3. **Serve Frontend**:
   Serve the `frontend/` directory using any HTTP server (e.g. `npx serve frontend` or VS Code Live Server).

---

## 🛡️ Driver Trust Score Algorithm

DriveSphere implements a transparent, multi-factor safety evaluation model:

$$\text{Overall Score} = \text{Verification} (25\%) + \text{Experience} (20\%) + \text{Customer Rating} (25\%) + \text{Trip Completion} (20\%) - \text{Cancellation Penalty}$$

- **Verification Factor (0 - 25 pts)**: Admin validation of government license, identity, and police records.
- **Experience Factor (0 - 20 pts)**: Commercial driving tenure ($\min(20, \text{ExperienceYears} \times 3.5)$).
- **Rating Factor (0 - 25 pts)**: Verified customer feedback star rating ($(\text{Rating} / 5.0) \times 25$).
- **Trip Completion (0 - 20 pts)**: Tiered points based on verified trips completed ($\ge 50 \to 20, \ge 20 \to 18, \ge 5 \to 16, < 5 \to 15$).
- **Incident Penalty (0 - 10 pts deduction)**: Deductions proportional to cancellation rate ($\min(10, \text{CancellationRate} \times 0.5)$).

*Classification*:
- **`EXCELLENT`**: $\ge 85.0$
- **`GOOD`**: $70.0 - 84.9$
- **`AVERAGE`**: $50.0 - 69.9$
- **`LOW`**: $< 50.0$

---

## ⚙️ Key Configuration Properties

- `drivesphere.jwt.secret`: Main JWT signing secret. Supports both `DRIVESPHERE_JWT_SECRET` and `JWT_SECRET` environment variables. Production startup enforces minimum 32 characters and blocks default demo secrets.
- `drivesphere.gps.allow-simulation`: Canonical property controlling simulated GPS route progression (`true` in dev/test/h2, `false` in production).
- `drivesphere.payment.allow-mock`: Controls mock payment bypass (`true` in dev/test/h2, `false` in production).
- `drivesphere.gemini.api-key`: Google Gemini API key (`GEMINI_API_KEY`). If absent or failed, gracefully operates in heuristic mode with transparent provider attribution.
- `drivesphere.gemini.model`: Google Gemini model identifier (`GEMINI_MODEL`, defaults to `gemini-3.5-flash-lite`).

---

## 🧪 Automated Testing

To run the complete automated test suite (**66 comprehensive tests**: 25 Security & IDOR regression tests, 11 AI integration & fallback tests, 15 business logic & fleet capacity tests, 13 production readiness tests, and 2 application tests):
```bash
cd backend
.\mvnw.cmd test
```

---

## 📄 License & Academic Attribution
Created for academic research and portfolio demonstration as the **DriveSphere Smart Mobility & Fleet Intelligence Platform**. Free-first and open for educational evaluation.
