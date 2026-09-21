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
   - **Google Gemini 2.5 Flash-Lite AI**: Official API client integration with an intelligent domain heuristic fallback that generates vehicle recommendations and concierge chat responses even if no API key is provided.
   - **Open-Meteo Free Weather API**: Real-time destination temperature, cloud condition, and driving advisory without any API key required.
   - **Spatial Routing & Maps**: Leaflet.js + OpenStreetMap with OpenRouteService and fallback geodesic waypoint interpolation.
   - **Razorpay Test Payment Sandbox**: Supports Razorpay test checkout and auto-verifying Sandbox Mock mode for zero-cost academic testing.

---

## 🚀 Instant 1-Click Demo Credentials

All demo accounts are pre-seeded in the database with the default password: `Password@123`

| Role | Email Address | Password | Workspace URL |
|---|---|---|---|
| **Platform Administrator** | `admin@drivesphere.local` | `Password@123` | `/admin/index.html` |
| **Fleet Manager** | `fleet@drivesphere.local` | `Password@123` | `/fleet-manager/index.html` |
| **Driver Partner** | `driver@drivesphere.local` | `Password@123` | `/driver/index.html` |
| **Customer** | `customer@drivesphere.local` | `Password@123` | `/customer/index.html` |

> 💡 **Quick Login**: The `/login.html` page includes **1-Click Demo Login Buttons** for each role to immediately populate and authenticate credentials.

---

## 📐 Architecture & Package Hierarchy

```
Drivesphere/
├── backend/
│   ├── src/main/java/com/drivesphere/
│   │   ├── config/            # CORS, WebMvc, OpenAPI config
│   │   ├── security/          # Spring Security 6, UserDetails, JWT filter
│   │   ├── controller/        # 14 REST Controllers:
│   │   │                      # Auth, Vehicle, Driver, Booking, Trip, Payment,
│   │   │                      # Maintenance, Fleet, Review, Notification, AI, Maps, Weather
│   │   ├── service/           # 11 Domain Services & Business Logic
│   │   ├── repository/        # 14 Spring Data JPA Repositories
│   │   ├── entity/            # 15 Persistent JPA Entities + 14 Domain Enums
│   │   ├── dto/               # Request & Response Data Transfer Objects
│   │   ├── mapper/            # Type-safe Entity <-> DTO converter
│   │   ├── exception/         # Global Exception Handler & ProblemDetails
│   │   └── integration/       # External Clients with Graceful Fallbacks:
│   │                          # GeminiClient, MapsClient, WeatherClient, RazorpayClient, CloudinaryClient
│   ├── src/test/java/         # Unit and Integration Test Suites
│   ├── mvnw.cmd               # Maven Wrapper executable
│   └── pom.xml                # Maven Dependencies (Spring Boot 3.4.3, Java 21)
├── frontend/
│   ├── customer/index.html    # Customer Workspace (Booking, Tracker, Planner)
│   ├── driver/index.html      # Driver Partner Console (Trust Score, Verification)
│   ├── fleet-manager/index.html # Fleet Manager Console (Telemetry, Inventory, Maintenance)
│   ├── admin/index.html       # Administrator Governance (Review Queue, Reports)
│   ├── login.html             # Role Login Portal with 1-Click Demo Buttons
│   ├── register.html          # Dual Registration (Customer vs Driver Partner)
│   ├── index.html             # Public Landing Page & Showcase
│   └── assets/
│       ├── css/style.css      # Dark Glassmorphic Design System
│       └── js/
│           ├── api.js         # Centralized HTTP Client with Bearer Token injection
│           ├── auth.js        # Authentication & Role-Based Route Guards
│           ├── chatbot.js     # Floating Gemini AI Concierge Widget
│           └── app.js         # Landing Page interactive features
├── database/
│   ├── schema.sql             # Full 16-table normalized DDL for MySQL 8.x
│   ├── seed.sql               # Foundational roles, categories, and demo accounts
│   └── sample-data.sql        # 10 diverse fleet vehicles, trips, maintenance, and reviews
├── docker-compose.yml         # 1-command MySQL 8 container with schema auto-init
└── README.md
```

---

## 🛠️ Getting Started & Local Setup

### Prerequisites
- **Java 21 LTS** (JDK 21+)
- **MySQL 8.x** (or Docker for running MySQL)

### Step 1: Database Startup
#### Option A: Using Docker Compose (Recommended)
Run the pre-configured MySQL 8 container which automatically executes `schema.sql`, `seed.sql`, and `sample-data.sql`:
```bash
docker compose up -d
```

#### Option B: Using Local MySQL
Connect to your local MySQL instance (port 3306) and execute:
```sql
SOURCE database/schema.sql;
SOURCE database/seed.sql;
SOURCE database/sample-data.sql;
```

### Step 2: Run the Spring Boot Backend
Navigate to the `backend/` directory and start the server using the Maven Wrapper:
```bash
cd backend
.\mvnw.cmd spring-boot:run
```
The REST API will start on **`http://localhost:8080`**.

### Step 3: Open the Frontend
Open `frontend/index.html` or `frontend/login.html` in any web browser (or serve via Live Server / Python `python -m http.server 3000`).

---

## 🛡️ Driver Trust Score Algorithm

DriveSphere implements a transparent, multi-factor safety evaluation model:

$$\text{Overall Score} = \text{Verification} (25\%) + \text{Experience} (20\%) + \text{Customer Rating} (25\%) + \text{Trip Completion} (20\%) - \text{Cancellation Penalty}$$

- **Verification Factor (0 - 25 pts)**: Admin validation of government license, identity, and police records.
- **Experience Factor (0 - 20 pts)**: Commercial driving tenure (up to 10+ years).
- **Rating Factor (0 - 25 pts)**: Verified customer feedback star rating ($(\text{Rating} / 5.0) \times 25$).
- **Trip Completion (0 - 20 pts)**: Ratio of completed vs accepted dispatches.
- **Incident Penalty**: Deductions for high cancellation frequency or safety warnings.

*Classification*:
- **`EXCELLENT`**: $\ge 85.0$
- **`GOOD`**: $70.0 - 84.9$
- **`AVERAGE`**: $55.0 - 69.9$
- **`LOW`**: $< 55.0$

---

## 🧪 Automated Testing

To run the unit test suite verifying JWT token signing, role authorities, Driver Trust Score calculation, AI fallback execution, and Razorpay signature verification:
```bash
cd backend
.\mvnw.cmd test
```

---

## 📄 License & Academic Attribution
Created for academic research and portfolio demonstration as the **DriveSphere Smart Mobility & Fleet Intelligence Platform**. Free-first and open for educational evaluation.
