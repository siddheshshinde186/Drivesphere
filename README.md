# DriveSphere – Smart Mobility & Fleet Intelligence Platform

DriveSphere is an enterprise-grade smart mobility and connected fleet intelligence platform built with a **Spring Boot 3.x** and **Java 21 LTS** backend and a decoupled modern web frontend.

---

## Architecture Overview

```
Drivesphere/
├── backend/
│   ├── .mvn/wrapper/                 # Maven wrapper configuration & binaries
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/drivesphere/ # Clean modular package structure
│   │   │   │   ├── config/           # CORS & application configuration
│   │   │   │   ├── security/         # Spring Security 6 & JJWT 0.12.6 foundation
│   │   │   │   ├── controller/       # REST controllers (HealthController, etc.)
│   │   │   │   ├── service/          # Business logic service layer
│   │   │   │   ├── repository/       # Spring Data JPA repositories
│   │   │   │   ├── entity/           # JPA persistent domain entities
│   │   │   │   ├── dto/              # Standardized API response envelopes & DTOs
│   │   │   │   ├── mapper/           # Entity <-> DTO transformation mappers
│   │   │   │   ├── exception/        # Global @RestControllerAdvice exception handler
│   │   │   │   ├── validation/       # Custom business constraints and validators
│   │   │   │   └── integration/      # Gemini AI, ORS, Cloudinary, Razorpay clients
│   │   │   └── resources/
│   │   │       └── application.properties # Environment-driven properties
│   │   └── test/                     # Unit test suites (compilation & logic tests)
│   ├── mvnw                          # Maven wrapper shell script
│   ├── mvnw.cmd                      # Maven wrapper Windows batch script
│   └── pom.xml                       # Maven build descriptor (Java 21, Spring Boot 3.4.3)
├── frontend/
│   ├── assets/
│   │   ├── css/style.css             # Glassmorphism dark theme & design system
│   │   ├── js/app.js                 # API health checker, Leaflet map, Chart.js
│   │   └── images/                   # Static platform visual assets
│   └── index.html                    # Polished foundation landing-page placeholder
├── database/
│   ├── schema.sql                    # Relational schema DDL for MySQL 8.x
│   └── seed.sql                      # Seed roles, categories, and test fleet units
├── .env.example                      # Template for all platform environment variables
├── .gitignore                        # Git ignore rules for Java, Maven, IDE, & secrets
└── README.md                         # Platform documentation & setup guide
```

---

## Technology Stack

| Layer | Technology | Version | Description |
|---|---|---|---|
| **Language** | Java SE | 21 LTS | Modern Java features and LTS runtime |
| **Framework** | Spring Boot | 3.4.3 | Spring Web, Spring Data JPA, Spring Security |
| **Build Tool** | Apache Maven Wrapper | 3.9.16 | Embedded wrapper (`mvnw` / `mvnw.cmd`) |
| **Security** | JJWT (Java JWT) | 0.12.6 | Stateless HMAC-SHA256 token authentication |
| **Database** | MySQL | 8.x | Relational storage with InnoDB & UTF-8 mb4 |
| **Frontend UI** | HTML5, CSS3, Bootstrap | 5.3.3 | Responsive dark glassmorphic dashboard |
| **Spatial / Maps** | Leaflet.js | 1.9.4 | Interactive dark-mode fleet tracking tiles |
| **Telemetry Charts**| Chart.js | 4.4.1 | Telemetry performance & battery analytics |

---

## Getting Started

### 1. Prerequisites
- **Java 21 LTS**: Verify with `java -version`
- **MySQL Server 8.x**: Running locally on `localhost:3306` (or remote MySQL)
- **Web Browser**: Modern browser (Chrome, Firefox, Edge)

### 2. Environment Variables Setup
Copy the `.env.example` template to `.env` (or configure system environment variables):

```bash
cp .env.example .env
```

Edit `.env` and provide your credentials:
```env
DB_URL=jdbc:mysql://localhost:3306/drivesphere?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=root
DB_PASSWORD=your_mysql_password
JWT_SECRET=your_jwt_secret_key_minimum_32_characters_long_for_hmac_sha256
```

### 3. Database Initialization
Create the database and apply the initial schema and seed data:

```bash
mysql -u root -p < database/schema.sql
mysql -u root -p < database/seed.sql
```

### 4. Build and Compile Backend

Navigate to the `backend/` directory and use the included Maven Wrapper:

**On Windows (PowerShell / Command Prompt):**
```powershell
cd backend
.\mvnw.cmd compile
```

**Run Automated Unit Tests:**
```powershell
.\mvnw.cmd test
```

**Start the Spring Boot Application:**
```powershell
.\mvnw.cmd spring-boot:run
```

The backend server starts on port `8080`.

### 5. Verify the Health Endpoint

Once the backend is running, test the health check endpoint:

```bash
curl http://localhost:8080/api/health
```

Expected JSON response:
```json
{
  "success": true,
  "message": "DriveSphere API is operational and healthy",
  "data": {
    "status": "UP",
    "service": "drivesphere-backend",
    "version": "1.0.0-SNAPSHOT",
    "environment": "development",
    "timestamp": "2026-09-03T14:45:00Z"
  },
  "timestamp": "2026-09-03T14:45:00Z"
}
```

### 6. Launch the Frontend

Open `frontend/index.html` in your browser or run a lightweight local web server (such as VS Code Live Server on port 5500, or `npx serve frontend`).

- The frontend landing page features an interactive **API Health Diagnostic Terminal** that pings `http://localhost:8080/api/health` in real-time.
- Features an interactive **Leaflet.js** dark map preview and **Chart.js** fleet telemetry metrics visualization.

---

## Security Foundation

- **Stateless Session Management**: Spring Security is configured with `SessionCreationPolicy.STATELESS`.
- **JWT Authorization Filter**: `JwtAuthenticationFilter` intercepts incoming requests, parses `Authorization: Bearer <token>`, and verifies token validity using HMAC SHA-256 keys.
- **Open Endpoints**: `/api/health` and `/api/auth/**` are permitted without authentication.
- **CORS Support**: Configured via `CorsConfig` to allow standard frontend dev ports (`http://localhost:5500`, `http://localhost:3000`, `http://localhost:5173`).

---

## Next Steps

The platform foundation is fully established and validated. The core architecture is prepared for complete business feature implementation:
- Authentication & RBAC (User, Driver, Dispatcher, Admin)
- Vehicle Management & Real-Time Telematics Ingestion
- Trip Dispatching & Smart Dynamic Routing (ORS)
- AI Predictive Fleet Maintenance (Google Gemini)
- Digital Vehicle Inspections (Cloudinary)
- Payment Settlement (Razorpay)
