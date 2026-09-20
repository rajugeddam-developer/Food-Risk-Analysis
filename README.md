# Food Risk Analysis PWA

An awareness-oriented mobile-first Progressive Web App (PWA) designed to help consumers understand packaged food ingredients and nutrition information before consuming them.

---

## Important Notice & Medical Disclaimer

> [!IMPORTANT]
> **This application is an awareness and information tool. It is not a medical diagnosis or replacement for professional medical or nutritional advice.**
>
> All ratings, risk indicators, and nutritional breakdowns are provided solely for consumer education and general awareness based on publicly available health guidelines (such as WHO and FSSAI standards). Consumers with allergies, chronic medical conditions, or specific dietary requirements must consult qualified healthcare practitioners.

---

## Project Overview

### Purpose

Packaged food products frequently conceal high levels of sodium, added sugars, saturated fats, industrial additives, and emulsifiers behind complex chemical names, international numbering codes (INS/E-numbers), and confusing serving sizes. 

The **Food Risk Analysis PWA** empowers everyday consumers to make informed choices directly at the point of purchase or consumption using their mobile device.

### Inputs

1. **Ingredients Label Image**: Photo capture or image upload of the ingredient listing on the product packaging.
2. **Nutrition Table Image**: Photo capture or image upload of the nutrition facts / nutritional information table.

### Outputs

1. **Ingredient Analysis**: Identification and risk assessment of individual ingredients, chemical additives, preservatives, emulsifiers, artificial sweeteners, and allergens.
2. **Nutrition Analysis**: Quantitative evaluation of sodium, added sugars, total carbohydrates, saturated fats, trans fats, and caloric density against daily intake thresholds.
3. **Overall Food-Risk Score**: Composite numerical and visual rating reflecting the healthiness and processing level of the food product.
4. **Risk Level**: Clear visual risk rating category (Low, Moderate, High, Critical).
5. **Eating Guidance**: Practical, non-medical awareness guidance based on defined risk criteria (e.g., consumption frequency recommendations, serving size cautions).
6. **WHO / FSSAI-Based References**: Citations referencing guidelines from the World Health Organization (WHO) and the Food Safety and Standards Authority of India (FSSAI).
7. **Product Classification**: Intent classification indicating whether the product is:
   - *Human food*
   - *Animal / pet food*
   - *Not intended for human consumption (e.g., cosmetic/household chemical)*
   - *Unable to determine*
8. **Awareness Message**: Human-readable summary providing context and actionable consumer awareness.

---

## Technology Stack

### Frontend
- **Framework**: [React 19](https://react.dev/)
- **Language**: [TypeScript](https://www.typescriptlang.org/)
- **Build Tool**: [Vite](https://vitejs.dev/)
- **Application Model**: Progressive Web App (PWA) via `vite-plugin-pwa`
- **Design Philosophy**: Mobile-first responsive layout with glassmorphism and modern visual aesthetics

### Backend
- **Platform**: [Java 17](https://adoptium.net/)
- **Framework**: [Spring Boot 3.3.x](https://spring.io/projects/spring-boot)
- **Build Tool**: [Apache Maven](https://maven.apache.org/)
- **Modules**:
  - `spring-boot-starter-web` (REST API)
  - `spring-boot-starter-security` (Security & Stateless JWT in future milestones)
  - `spring-boot-starter-data-jpa` (Persistence)
  - `postgresql` (PostgreSQL driver)

### Target Scalability
- **Concurrency Target**: 500 concurrent users without unnecessary distributed complexity.

---

## Directory Structure

```text
food-risk-analysis/
├── frontend/                     # React 19 + TypeScript + Vite PWA
├── backend/                      # Java 17 + Spring Boot 3.3.x REST API
├── docs/                         # Architecture & milestone documentation
│   ├── project-overview.md       # Vision, user workflows, privacy & tech decisions
│   ├── development-milestones.md # Comprehensive roadmap for Milestones M0 through M14
│   └── architecture-notes.md     # Architecture flow, concurrency, & data privacy lifecycle
└── README.md                     # Root project onboarding guide
```

---

## Milestone Roadmap Summary

| Milestone | Name | Status / Focus |
| :--- | :--- | :--- |
| **M0** | **Project Foundation** | **Completed** — Skeletons, docs, tooling verification |
| **M1** | **3D PWA UI + Mobile-First Design System** | **Completed** — Premium UI, Brand Orbs `aura` component, Auth UI |
| **M2** | **PostgreSQL + Backend Domain Model** | **Completed** — PostgreSQL, Flyway migrations, User entity, transient session entity |
| **M3** | **Authentication + JWT Security** | **Completed** — Stateless JWT authentication, BCrypt hashing, protected routes |
| **M4** | **Image Capture + Upload** | **Completed** — Camera capture, file upload, client-side validation, dual packaging preview |
| **M5** | **Offline OCR Integration** | **Completed** — Offline Tesseract OCR, magic byte validation, ephemeral image deletion, raw OCR text |
| **M6** | **Gemini AI Normalization** | **Completed** — Structured JSON normalization, strict prompt guardrails, zero risk scoring, external API validation |
| **M7** | **Food Classification & Intent** | **Completed** — Deterministic human/pet/feed/non-food intent classification, machine-readable reason codes |
| **M8** | **Ingredient Risk Engine** | **Completed** — Verified FSSAI/WHO additive & ingredient evaluation, canonical INS/E resolution, decoupled regulatory status |
| **M9** | **Nutrition Analysis + WHO/FSSAI Rules** | **Completed** — Quantitative nutrient evaluation against authoritative WHO and FSSAI standards, basis scaling, missing!=zero |
| **M10** | **Final Score + Awareness Guidance** | **Completed** — Deterministic Food Awareness Score (0–100), anti-double-counting overlap rules, category priority, non-medical population guidance |
| **M11** | **Final Result UI + Full Frontend Integration** | **Next** — Result cards, interactive breakdown, awareness disclaimers |
| **M12** | **Redis + Performance + 500 Concurrent Users** | Caching, deduplication, connection pool tuning for 500 CCU |
| **M13** | **End-to-End Integration + Resilience + Error Handling** | Resilience, fallbacks for low-quality photos, integration test suite |
| **M14** | **Production Hardening + Deployment** | Lighthouse PWA audit, Docker containerization, production readiness |

---

## Development & Verification (M5 + M6)

### Prerequisites
- **Java**: Version 17 LTS
- **Maven**: Version 3.9+
- **PostgreSQL**: Version 15+ (tested on PostgreSQL 17)
- **Node.js**: Version 22+
- **npm**: Version 12+
- **Tesseract Trained Data**: Local `eng.traineddata` in `backend/tessdata/` (zero internet downloads during runtime)

### Environment Variables

The backend requires configuration supplied via environment variables:

| Variable | Description | Default (Local) |
| :--- | :--- | :--- |
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `food_risk_analysis` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | *(Must be supplied via environment)* |
| `JWT_SECRET` | HMAC-SHA signing key (min 256-bit) | *(Must be supplied via environment)* |
| `JWT_EXPIRATION` | Token expiration in ms | `86400000` (24 hours) |
| `TESSDATA_PATH` | Local directory containing `.traineddata` files | `tessdata` |
| `TESSERACT_LANGUAGE` | Tesseract language code | `eng` |
| `GEMINI_API_KEY` | Google Gemini API key (server-side only) | *(Configured via environment)* |
| `GEMINI_MODEL` | Gemini generative model | `gemini-3.6-flash` |
| `GEMINI_TIMEOUT_SECONDS` | Gemini API request timeout | `30` |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend origins | `http://localhost:5173,http://localhost:4173` |

### Setting Up Local Database & OCR Data

1. Ensure PostgreSQL is running locally on port 5432.
2. Verify local Tesseract trained data exists:
   - `backend/tessdata/eng.traineddata`
3. Flyway automatically executes migrations (`V1` and `V2`) on startup. No tables are created for OCR or AI results (strictly ephemeral).

### Running Backend Tests (M5 + M6)

```powershell
# Navigate to backend directory
cd food-risk-analysis/backend

# Set environment credentials
$env:DB_PASSWORD = "<your-postgres-password>"
$env:JWT_SECRET = "<your-jwt-secret-at-least-32-chars-long>"

# Run automated tests (137 passing tests covering auth, JWT, offline OCR, Gemini normalization, classification, ingredient risk, nutrition standards, and scoring synthesis)
mvn clean test

# Run the backend locally
mvn spring-boot:run
```

The Spring Boot backend starts on `http://localhost:8080`.
- Health check: `GET http://localhost:8080/api/health`
- Create Analysis Session: `POST http://localhost:8080/api/analysis/session`
- Get Session: `GET http://localhost:8080/api/analysis/{sessionId}`
- Run Offline OCR: `POST http://localhost:8080/api/analysis/{sessionId}/ocr`
- Run Gemini Normalization: `POST http://localhost:8080/api/analysis/{sessionId}/normalize`
- Classify Product Intent (M7): `POST http://localhost:8080/api/analysis/{sessionId}/classify`
- Analyze Ingredient Risk (M8): `POST http://localhost:8080/api/analysis/{sessionId}/ingredient-risk`
- Analyze Nutrition Standards (M9): `POST http://localhost:8080/api/analysis/{sessionId}/nutrition`
- Synthesize Food Awareness Assessment (M10): `GET http://localhost:8080/api/analysis/{sessionId}/assessment`
- Swagger UI Documentation: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI Specification: `GET http://localhost:8080/v3/api-docs`

### Running Frontend

```powershell
# Navigate to frontend directory
cd food-risk-analysis/frontend

# Build for production
npm run build

# Run local development server
npm run dev
```

The Vite frontend server runs on `http://localhost:5173`.

---

## License & Attribution

This project is developed as an educational awareness system. All guidelines are derived from published World Health Organization (WHO) and Food Safety and Standards Authority of India (FSSAI) public standards.
