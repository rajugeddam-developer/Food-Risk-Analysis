# Food Risk Analysis — Production Deployment Guide

## 1. System Overview & Architecture

Food Risk Analysis is an evidence-based consumer dietary literacy tool engineered as a decoupled, privacy-first web application:
- **Frontend**: Progressive Web App (PWA) built with React 19, TypeScript, and Vite. Served through Nginx Alpine with SPA fallback routing and strict Content Security Policies.
- **Backend**: Spring Boot 3.3.5 / OpenJDK 21 REST API. Executes offline Tesseract OCR extraction, AI label normalization, and deterministic risk evaluation (M7–M10).
- **Database**: PostgreSQL 16/18 with Flyway declarative versioned migrations (`V1`, `V2`).
- **OCR Engine**: Tess4J backed by native Debian `tesseract-ocr` and `libtesseract-dev` C++ packages and local `eng.traineddata`.

```text
[ Internet User / Mobile Device ]
              │ (HTTPS :443)
              ▼
    [ Nginx Reverse Proxy ]
     ├── Static PWA Assets (SPA)
     └── /api/* Proxy Pass
              │
              ▼
   [ Spring Boot Backend (:8080) ]
     ├── Tesseract OCR (Native C++)
     ├── Gemini AI (Client Header Auth)
     └── M7-M10 Deterministic Scoring
              │
              ▼
     [ PostgreSQL Database ]
```

---

## 2. Environment Variables & Secret Configuration

Production secrets must never be checked into version control. Configure environment variables in `.env` (or cloud secret manager):

| Variable | Required | Description | Example / Default |
| :--- | :---: | :--- | :--- |
| `DB_HOST` | Yes | PostgreSQL host address | `postgres` (container) or `localhost` |
| `DB_PORT` | Yes | PostgreSQL port | `5432` |
| `DB_NAME` | Yes | Database name | `food_risk_analysis` |
| `DB_USERNAME` | Yes | Database user | `postgres` |
| `DB_PASSWORD` | Yes | Database password | *(Cryptographically secure password)* |
| `JWT_SECRET` | Yes | 256-bit HMAC-SHA signing key | *(Min 32 characters)* |
| `JWT_EXPIRATION`| No | Token TTL in milliseconds | `86400000` (24 hours) |
| `GEMINI_API_KEY`| No | Google Gemini API key | `AIzaSy...` (transient text structuring only) |
| `CORS_ALLOWED_ORIGINS` | Yes | Comma-separated allowed web origins | `https://yourdomain.com` |
| `TESSDATA_PATH` | No | Path to directory containing traineddata | `/app/tessdata` (Docker) or `tessdata` |
| `SPRING_PROFILES_ACTIVE` | No | Active Spring configuration profile | `prod` |

---

## 3. Deployment with Docker Compose

### Prerequisites
- Docker Engine 24.0+ and Docker Compose v2.20+
- Host ports 80 and 443 open

### Startup Procedure
```bash
# 1. Clone repository
git clone https://github.com/your-org/food-risk-analysis.git
cd food-risk-analysis

# 2. Configure environment secrets
cp .env.example .env
# Edit .env and supply DB_PASSWORD and JWT_SECRET

# 3. Build images and launch services
docker compose up -d --build

# 4. Verify running containers and health status
docker compose ps
```

---

## 4. Health Checks & Verification

The application exposes standard HTTP health probes:
- **Liveness Probe**: `GET http://localhost:8080/api/health`
  Returns `{"status":"UP","database":"UP","timestamp":"..."}`
- **Container Health**:
  Docker Compose automatically tracks container health:
  - `foodrisk-postgres`: checks `pg_isready`
  - `foodrisk-backend`: checks `/api/health`
  - `foodrisk-frontend`: checks HTTP 200 on root

Run the automated smoke test suite:
```powershell
.\scripts\production-smoke-test.ps1 -BaseUrl "http://localhost:8080"
```

---

## 5. Structured Logging & Observability

- **Console & File Logging**: Standardized ISO-8601 timestamps, log level, logger name, and MDC correlation ID (`reqId`).
- **Log Files**: Written to `logs/food-risk-backend.log` with daily gzip compression, 10MB file limit, and 30-day retention.
- **Privacy Enforcement**: Logback strictly suppresses user passwords, authorization tokens, Gemini API keys, and image binary payloads.

---

## 6. Resource Limits & Sizing Guidelines

| Component | Minimum Spec | Recommended Production |
| :--- | :--- | :--- |
| **CPU** | 2 vCPU | 4 vCPU |
| **RAM** | 2 GB | 4–8 GB |
| **Storage** | 10 GB SSD | 50 GB SSD (for DB growth) |
| **JVM Heap** | `-XX:MaxRAMPercentage=75.0` | Default G1GC container sizing |
| **DB Pool** | 5 minimum, 20 max | HikariCP auto-scaling |

---

## 7. Troubleshooting & Common Issues

1. **Database connection failed on startup:**
   - Verify PostgreSQL container is healthy: `docker compose ps`
   - Check password credentials in `.env`
2. **OCR extraction returns empty text:**
   - Ensure `eng.traineddata` exists inside `/app/tessdata`
   - Check image clarity and lighting
3. **Session expired error (HTTP 410):**
   - Sessions have an intentional 15-minute TTL to enforce privacy. Start a new scan.
