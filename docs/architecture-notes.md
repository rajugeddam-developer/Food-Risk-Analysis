# Food Risk Analysis PWA — Architecture Notes

This document defines the overarching system architecture, design principles, data privacy constraints, and scalability strategy.

---

## 1. System Architecture Overview

The system employs a clean, decoupled client-server architecture. The frontend handles mobile-first presentation, image capture, and user interaction, while the backend orchestrates image extraction, AI processing, risk calculation, and data persistence.

```text
Mobile Browser
      ↓
React PWA (React 19 + TypeScript + Vite)
      ↓ (HTTPS / REST API)
Spring Boot REST API (Java 17 + Spring Boot 3.3.x)
      ↓
Application / Domain Services
      ↓
 ┌────┼───────────┬───────────┐
 ↓    ↓           ↓           ↓
OCR  Gemini   PostgreSQL    Redis
```

### Architectural Tiers

1. **Client Tier (React PWA)**:
   - Built with **React 19**, **TypeScript**, and **Vite**.
   - Configured as an installable Progressive Web App via `vite-plugin-pwa` with service worker caching for static assets.
   - Designed strictly for mobile-first viewports with touch-first interactions.
   - Contains **zero** business rules, zero risk-scoring algorithms, and **zero** external service credentials (such as Gemini API keys).

2. **API & Gateway Tier (Spring Boot REST API)**:
   - Built with **Spring Boot 3.3.x** on **Java 17**.
   - Exposes RESTful HTTP endpoints with JSON contracts.
   - Enforces stateless authentication via Spring Security and JWT.
   - Provides strict input validation, rate limiting, and multipart image upload handling.

3. **Application & Domain Services**:
   - Encapsulates discrete domain components:
     - `OcrService`: Extracts raw textual content from image binaries.
     - `GeminiNormalizationService`: Communicates with Google Gemini API to normalize extracted terms.
     - `FoodClassificationService`: Detects product category (Human vs Animal vs Non-food).
     - `IngredientRiskEngine`: Applies NOVA, additive toxicity, and allergen scoring.
     - `NutritionEngine`: Applies WHO and FSSAI threshold evaluations.
     - `ScoreSynthesisService`: Synthesizes final score and awareness messaging.

4. **External Services & Data Tier**:
   - **PostgreSQL**: Stores permanent user account records only.
   - **Redis** *(Future — M12)*: Provides fast ephemeral caching for frequent ingredient tokens, additive lookups, and short-term processing caches.
   - **OCR Engine** *(Future — M5)*: Optical character recognition engine.
   - **Gemini API** *(Future — M6)*: Multimodal generative language model.

---

## 2. Core Architectural Principles

### Strict Separation of Concerns
- **Frontend**: Exclusively responsible for capturing inputs, displaying data, and facilitating user interactions. All calculations, scoring, and regulatory rule enforcement occur on the backend.
- **Backend**: Exclusively responsible for processing logic, external API integrations, data security, and evaluation rules.
- **Secret Isolation**: Sensitive API credentials (e.g., Gemini API keys, JWT secret keys, database credentials) are securely held in backend environment variables and are never transmitted to or exposed in client bundles.

### Clean Code for Student Developers
- Avoid over-engineering, unnecessary microservices, or complex distributed message brokers.
- Organize backend packages cleanly by feature/layer (`config`, `controller`, `service`, `model`, `repository`, `dto`).
- Maintain explicit, well-documented code with minimal magic.

---

## 3. Scalability Strategy: 500 Concurrent Users

The target deployment specification requires supporting approximately **500 concurrent users** performing active scans and queries.

### Scalability Pillars (Deferred to M12)

1. **Stateless API Architecture**:
   - REST endpoints maintain no HTTP session state on the server.
   - Requests carry signed JWT tokens, allowing easy horizontal scaling behind a standard reverse proxy (e.g., Nginx) if needed.

2. **Connection Pool Optimization**:
   - PostgreSQL connection pooling managed via HikariCP, tuned to handle peak concurrent transactions efficiently.

3. **In-Memory Caching (Redis in M12)**:
   - High-frequency lookups (e.g., E-number database entries, standard nutrient guidelines, common ingredients) are cached in Redis to eliminate repetitive external API calls and database roundtrips.

4. **Asynchronous Processing Pipeline**:
   - Heavy operations (OCR extraction and Gemini AI processing) execute through Spring's asynchronous task executors (`@Async`), preventing thread starvation on standard web request threads.

*Note: In Milestone M0, this scalability design is documented as a future architectural requirement and is not prematurely implemented.*

---

## 4. Data Privacy by Design & Persistence Model (M2)

The application adheres to a strict data minimization philosophy.

### Permanent Data Model (PostgreSQL)
Only essential account credentials required for user identity and authentication are retained in the `users` table:

```text
users
├── id             : UUID (Primary Key)
├── name           : VARCHAR(100) NOT NULL
├── email          : VARCHAR(255) NOT NULL UNIQUE
├── password_hash  : VARCHAR(255) NOT NULL
├── created_at     : TIMESTAMP WITH TIME ZONE NOT NULL
└── updated_at     : TIMESTAMP WITH TIME ZONE NOT NULL
```

- **Index Optimization**: The `UNIQUE(email)` constraint automatically creates a backing B-tree index in PostgreSQL; no redundant secondary index is added.
- **Password Column**: Named `password_hash` in preparation for M3 BCrypt password hashing. M2 persists strings directly without hashing; real hashing is introduced in M3.

### Transient Session Model (`food_analysis_sessions`)
Food scan processing requires temporary coordination across future OCR, Gemini, and risk-scoring stages without permanently logging user scans:

```text
food_analysis_sessions
├── id             : UUID (Primary Key)
├── session_token  : VARCHAR(64) NOT NULL UNIQUE
├── status         : VARCHAR(30) NOT NULL (CREATED, PROCESSING, COMPLETED, FAILED, EXPIRED)
├── user_id        : UUID NULL (Foreign Key -> users.id ON DELETE SET NULL)
├── created_at     : TIMESTAMP WITH TIME ZONE NOT NULL
├── expires_at     : TIMESTAMP WITH TIME ZONE NOT NULL (Indexed: idx_sessions_expires_at)
└── updated_at     : TIMESTAMP WITH TIME ZONE NOT NULL (Indexed: idx_sessions_status)
```

- **Strictly Ephemeral**: Mandatory `expires_at` timestamp defines the session's time-to-live (TTL).
- **Guest & Authenticated Scans**: `user_id` is nullable, allowing both anonymous guest scans and user-associated scans without creating a permanent consumption history log.
- **Zero Image / Result Blobs**: Zero `byte[]`, `Blob`, Base64 images, OCR dumps, or AI responses are stored in PostgreSQL.

### Flyway Schema Migration Strategy
Database schemas are strictly version-controlled and managed via Flyway:
- `V1__create_users_table.sql`: Provisions the `users` table and uniqueness constraints.
- `V2__create_food_analysis_sessions_table.sql`: Provisions the `food_analysis_sessions` table and targeted lookup/cleanup indexes.
- In production and local validation, Hibernate operates in `validate` mode (`spring.jpa.hibernate.ddl-auto: validate`), preventing arbitrary DDL drift.

### Ephemeral Food Data Lifecycle
Uploaded food images and scan analyses are strictly transient processing artifacts:

```text
User Uploads Images
        ↓
Temporary In-Memory / Spool Storage
        ↓
OCR Text Extraction (M5)
        ↓
Gemini AI Normalization (M6)
        ↓
Risk Engines Evaluation (M7–M10)
        ↓
Structured Result Returned to User
        ↓
Temporary Image & Scan Data Purged / Expired
```

- **No Permanent Scan History**: The database schema intentionally does NOT include tables for scanned food images, scan logs, or user consumption history.
- **Zero Image Archiving**: Images uploaded for OCR are never archived into PostgreSQL.

---

## 5. User Authentication & Security Architecture (M3)

Milestone M3 implements stateless, session-free JWT authentication with BCrypt password encryption.

```text
React PWA (M1 UI)
       │
       ├── POST /api/auth/register ──► AuthService ──► BCryptPasswordEncoder ──► PostgreSQL (users)
       │
       ├── POST /api/auth/login ──────► AuthenticationManager ──► JwtService ──► Returns JWT Access Token
       │                                                                  │
       └── GET /api/user/me ◄── Bearer <JWT> ── JwtAuthenticationFilter ◄┘ (SecurityContext populated)
```

### Security Principles & Controls
1. **Stateless Session Policy**: Configured with `SessionCreationPolicy.STATELESS`. The server creates zero HTTP sessions and maintains no stateful session tables.
2. **Password Encryption**: Employs `BCryptPasswordEncoder` (cost factor 10). Plain-text passwords are never persisted and `passwordHash` is never exposed in API responses or JWT claims.
3. **JWT Token Structure**:
   - Algorithms: HMAC SHA-256 (`HS256`) via JJWT 0.12.6.
   - Claims: strictly limited to standard claims (`sub` = user email, `iat` = issued at, `exp` = expires at).
   - Zero sensitive metadata (passwords, hashes, role internals) in claims.
4. **Environment-Driven Secrets**:
   - `JWT_SECRET`: Signing secret injected via environment variable (minimum 256 bits). Never committed to Git or source files.
   - `JWT_EXPIRATION`: Token validity period in milliseconds (default: 86400000 ms / 24 hours).
5. **Token Storage & Frontend State**:
   - Stored in browser `sessionStorage` (`fra_access_token`).
   - Reusable `authFetch` utility automatically attaches `Authorization: Bearer <token>`.
   - Lightweight `AuthContext` provides `isAuthenticated`, `user`, `login`, and `logout` across the PWA.
   - Route guard `ProtectedRoute` prevents unauthenticated access to `/scan`, redirecting to `/login`.
6. **Error Sanitization**:
   - Duplicate email returns `409 Conflict` (`{"message": "An account with this email already exists."}`).
   - Invalid credentials return generic `401 Unauthorized` (`{"message": "Invalid email or password."}`).
   - Validation failures return `400 Bad Request` with field error map.
   - Stack traces, database details, and internal exceptions are suppressed.

---

## 6. Offline OCR Architecture & Ephemeral Pipeline (M5)

Milestone M5 implements local, offline Optical Character Recognition without external cloud dependencies.

```text
Packaging Image (JPEG/PNG/WEBP)
       │
       ▼
ImageValidator (MIME, Magic Bytes, Decodability, Dimensions, 10MB limit)
       │
       ▼
Ephemeral Temp File (System temp dir, random UUID filename: ocr_ephemeral_<uuid>.ext)
       │
       ▼
TesseractOcrProvider (Tess4J, local eng.traineddata, no internet connection)
       │
       ▼
Raw OCR Text + Confidence + Processing Duration
       │
       ▼ (finally block: immediate file deletion)
Temp File Deleted (Never persisted to PostgreSQL or disk)
```

### Key Architectural Safeguards:
1. **Offline Guarantee**: Requires local trained data (`backend/tessdata/eng.traineddata`). Startup fails fast if missing, without attempting any internet download.
2. **File Deletion Lifecycle**: Temporary files are created with randomized names (never original client filenames) and are strictly destroyed in `finally` blocks.
3. **Partial Input Support**: The pipeline gracefully handles scans where only the ingredients image or only the nutrition table image is provided.
4. **Session Expiration Guard**: Checks session `expiresAt` before processing. Expired sessions are rejected with HTTP 410 GONE.
5. **Request Correlation**: Each analysis trace is correlated via the `X-Request-ID` HTTP header for cross-component observability.

---

## 7. Gemini AI Food Normalization Architecture (M6)

Milestone M6 employs Google Gemini AI strictly as a data normalization and entity extraction engine.

```text
Raw OCR Text (From M5)
       │
       ▼
GeminiPromptBuilder (Strict Guardrails: No risk scoring, no Good/Bad rating, no hallucination)
       │
       ▼
GeminiClient (Spring Boot RestClient, server-side GEMINI_API_KEY, configurable model)
       │
       ▼
Gemini 1.5 Flash API (Structured JSON output)
       │
       ▼
FoodNormalizationService (Validates untrusted AI response: rejects negative nutrients, checks impossible basis)
       │
       ▼
NormalizedFoodData (Ingredients, Additives, INS Codes, Nutrition Facts, Uncertainties)
```

### Strict Milestone Boundaries:
- **No Images to AI**: Gemini receives structured text evidence only, never image binaries.
- **Zero Risk Scoring**: Gemini is forbidden from evaluating health risk, calculating eating scores, classifying items as Good/Bad/Worst, or categorizing foods as human vs pet/animal food (deferred to M7–M10).
- **Untrusted Response Validation**: AI output is treated as untrusted input. Negative calories or nutrients and impossible quantities (>100g per 100g) are rejected with application-level errors.
- **Server-Side Credential Isolation**: The Gemini API key resides strictly in the `GEMINI_API_KEY` environment variable on the server. It is never logged, exposed to React, or returned in HTTP headers or error responses.

---

## 8. Food Categorization & Intent Classification Architecture (M7)

Milestone M7 provides deterministic product categorization and consumption intent classification using an evidence-based hierarchy.

```text
NormalizedFoodData (From M6 Context Store)
       │
       ▼
FoodClassificationEngine (Hierarchical Evidence Rules)
  ├── 1. Explicit Packaging Markers (Pet food, animal feed, non-food keywords)
  ├── 2. Ingredient & Nutrition Profiling (Human food cues)
  └── 3. Ambiguity & Conflict Detection (Conflicting or insufficient evidence -> UNKNOWN)
       │
       ▼
FoodClassificationResult (category, certainty, reasonCode, reason, evidence, warnings)
       │
       ▼
AnalysisContextStore (Ephemeral in-memory cache, 15-min TTL)
```

### Key Principles:
1. **Deterministic Rule Engine**: Driven by authoritative keywords in `food-category-rules.json`. No ungrounded heuristics or probabilistic guesses.
2. **Explicit Evidence Hierarchy**: Direct packaging markers take precedence over generic ingredient cues. Conflicting markers immediately fall back to `UNKNOWN`.
3. **Never "Waste Food"**: Products are categorized objectively as `HUMAN_FOOD`, `PET_FOOD`, `ANIMAL_FEED`, `NON_FOOD`, or `UNKNOWN`. The system never labels edible food as waste.
4. **Machine-Readable Reason Codes**: Uses typed `ClassificationReasonCode` enums (`EXPLICIT_HUMAN_FOOD_MARKER`, etc.) for auditability.

---

## 9. Ingredient Risk Engine Architecture — Core & Additive Evaluation (M8)

Milestone M8 evaluates individual ingredients and chemical additives against authoritative regulatory datasets (FSSAI, WHO, Codex Alimentarius).

```text
NormalizedFoodData.ingredients (From M6 Context Store)
       │
       ▼
JsonRiskRuleRepository (Classpath JSON datasets: sources.json, additives.json, ingredient-rules.json)
  ├── Canonical INS/E-number resolution: "INS 330" == "E330" == "INS330" == "Citric Acid"
  └── Rejection of ambiguous OCR patterns: Reject strings with '?' or '*'
       │
       ▼
IngredientRiskEngine
  ├── Decouples RegulatoryStatus (PERMITTED/RESTRICTED/BANNED) from IngredientRiskLevel
  ├── Evaluates risk level: NO_CONCERN, LOW_ATTENTION, MODERATE_ATTENTION, HIGH_ATTENTION, UNKNOWN
  ├── Preserves M6 OCR uncertainty flags (uncertain: true remains UNKNOWN with INSUFFICIENT evidence)
  └── Deduplicates identical ingredients in summary counts while preserving full audit items
       │
       ▼
IngredientRiskAnalysisResult (summary, items with sources and reasons, evaluatedAt)
```

### Key Principles:
1. **Decoupled Regulatory Status**: Permitted additives (e.g. INS 330) are `PERMITTED` with `NO_CONCERN`. Permitted additives with caution (e.g. INS 621 MSG) are `PERMITTED` with `LOW_ATTENTION`. Unknown ingredients default to `UNKNOWN`, never `BANNED`.
2. **State Store Abstraction**: Inter-milestone state is coordinated through `AnalysisContextStore` (`InMemoryAnalysisContextStore` with 15-minute TTL eviction), establishing a clean boundary ready for Redis in M12.
3. **Session-Only Endpoints**: `POST /api/analysis/{sessionId}/classify` and `POST /api/analysis/{sessionId}/ingredient-risk` accept only the session ID in the URL path. No client request-body overrides are permitted.
4. **Zero Composite Product Scoring**: M8 strictly evaluates individual ingredients. Composite health scores and Good/Bad/Worst ratings are deferred to M10.

