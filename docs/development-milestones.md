# Food Risk Analysis PWA — Development Milestones (M0–M14)

This document establishes the official milestone sequence, scope boundaries, and core responsibilities from project inception through production readiness.

---

## Master Roadmap Overview

```text
M0  Project Foundation
M1  3D PWA UI + Mobile-First Design System
M2  PostgreSQL + Backend Domain Model
M3  Authentication + JWT Security
M4  Image Capture + Upload
M5  OCR Integration
M6  Gemini AI Integration
M7  Food Classification
M8  Ingredient Risk Analysis Engine
M9  Nutrition Analysis + WHO/FSSAI Rules
M10 Final Score + Awareness Guidance
M11 Final Result UI + Full Frontend Integration
M12 Redis + Performance + 500 Concurrent Users
M13 End-to-End Integration + Resilience + Error Handling
M14 Production Hardening + Deployment
```

---

## Detailed Milestone Specifications

### Milestone M0: Project Foundation (Current Milestone)
- **Primary Goal**: Establish repository scaffolding, clean developer documentation, and standalone starter skeletons for frontend and backend.
- **Frontend Scope**:
  - React 19, TypeScript, and Vite setup.
  - PWA manifest (`manifest.webmanifest`) and service worker configuration via `vite-plugin-pwa`.
  - Starter shell verifying compilation, bundling, and mobile viewport responsiveness.
  - No application pages, forms, or scan UI.
- **Backend Scope**:
  - Java 17, Spring Boot 3.3.x, and Maven setup.
  - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `postgresql`.
  - Temporary development `SecurityConfig` permitting requests so the starter boots cleanly.
  - `DataSourceAutoConfiguration` excluded so the backend boots without requiring a running PostgreSQL instance in M0.
  - Baseline Spring Boot context-load test.
- **Explicit Exclusions**: No authentication, no database entities, no OCR, no Gemini, no Redis, no risk engines, no fake APIs.

---

### Milestone M1: 3D PWA UI + Mobile-First Design System
- **Primary Goal**: Construct the mobile-first frontend presentation layer, 3D visual effects, and navigation structure.
- **Home Page**:
  - Premium 3D / glassmorphism visual design.
  - Mobile-first responsive layout tailored for handheld devices.
  - "Know What You Eat" awareness branding.
  - Hero section with prominent "Scan Your Food" Call-To-Action (CTA).
  - Food analysis preview cards and feature highlights.
  - Accessible, responsive mobile bottom navigation.
  - Informative "How It Works" and educational awareness sections.
- **Brand Orbs `aura` Visual Element**:
  - The homepage **must** utilize the verified Brand Orbs `aura` visual effect.
  - Use the authored local `BrandOrbs` implementation rather than depending purely on external runtime package entry points.
  - Integrate as a local effect/component in the frontend codebase.
  - Preserve its authored behavior, high-fidelity shader rendering, and performance attributes.
  - The animated aura serves as a visual backdrop while foreground food analysis content remains crisp and readable.
  - Do not substitute with low-quality CSS keyframes or alternative 3D libraries.
- **Authentication UI (UI Only)**:
  - **Login View**: Email input, password input, Login button, "Forgot Password?" link, "Create an Account" navigation.
  - **Register View**: Full name, email, password, confirm password, Register button, "Already have an account?" navigation.
  - **Forgot Password View**: Email input, "Send Reset Link" button, "Back to Login" navigation.
  - *Strict Rule*: In M1, these forms are UI components only. Real authentication and backend integration are deferred to M3.

---

### Milestone M2: PostgreSQL + Backend Domain Model (Completed)
- **Primary Goal**: Configure persistent relational database storage and establish the core user account model.
- **Key Deliverables Completed**:
  - Configured PostgreSQL datasource with HikariCP connection pooling via environment variables (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`).
  - Integrated Flyway migration framework (`flyway-core`, `flyway-database-postgresql`) with `V1__create_users_table.sql` and `V2__create_food_analysis_sessions_table.sql`.
  - Defined the `User` account entity (`id`, `name`, `email`, `passwordHash`, `createdAt`, `updatedAt`) with database-level uniqueness constraint on `email`.
  - Defined the transient `FoodAnalysisSession` coordination entity with `AnalysisStatus` lifecycle and mandatory `expiresAt` TTL.
  - Implemented Spring Data JPA repositories: `UserRepository` (`findByEmail`, `existsByEmail`) and `FoodAnalysisSessionRepository` (`findBySessionToken`, `findByExpiresAtBefore`, `findByStatus`).
  - Implemented safe `/api/health` connectivity check endpoint.
  - Comprehensive automated test suite against PostgreSQL validating persistence, uniqueness constraints, expiration queries, and application context startup.
  - *Strict Privacy Boundary Enforced*: Zero tables or columns for food images, OCR data, AI dumps, or permanent user scan history.


---

### Milestone M3: Authentication + JWT Security (Completed)
- **Primary Goal**: Implement stateless authentication and protect private REST API endpoints.
- **Key Deliverables Completed**:
  - Stateless Spring Security architecture with `SessionCreationPolicy.STATELESS` and CORS configured for frontend development.
  - Password encryption via `BCryptPasswordEncoder` (cost factor 10); passwords never persisted in plain text or returned in responses.
  - `JwtService` implementing token generation, signature validation (HMAC SHA-256 via JJWT 0.12.6), claims isolation (`sub`, `iat`, `exp`), and expiration checking.
  - `JwtAuthenticationFilter` reading `Authorization: Bearer <token>` and populates `SecurityContext` for authenticated requests without blocking public endpoints.
  - Authentication endpoints: `POST /api/auth/register` (201 Created) and `POST /api/auth/login` (200 OK returning accessToken).
  - Protected endpoint: `GET /api/user/me` (requires Bearer token, returns sanitized user profile without `passwordHash`).
  - Global error handling: 400 (validation), 401 (invalid credentials / missing token), 409 (duplicate email).
  - Frontend integration: `authApi`, `userApi`, `AuthContext` (using browser `sessionStorage`), protected `/scan` route, and dynamic login/logout state in `Navbar`.
  - Comprehensive automated tests (23 passing tests covering registration, login, BCrypt, JWT, protected routes, and repository queries).


---

### Milestone M4: Image Capture + Upload (Completed)
- **Primary Goal**: Build mobile camera capture, image upload pipeline, and client-side preprocessing.
- **Key Deliverables Completed**:
  - HTML5 Camera API / file picker integration for capturing ingredients label and nutrition table (`UploadCard.tsx`).
  - Client-side image validation (MIME types, dimension checking, 10MB limits, memory management).
  - Preview card with thumbnail generation, camera switch option, and clear remove triggers.
  - Ephemeral client-side blob URL cleanup on unmount to prevent browser memory leaks.

---

### Milestone M5: Offline OCR Integration (Completed)
- **Primary Goal**: Implement Optical Character Recognition to extract raw textual data from packaging images using offline Tesseract.
- **Key Deliverables Completed**:
  - Offline Tesseract OCR engine (`TesseractOcrProvider`) requiring local `eng.traineddata` (fails fast on startup with zero network dependencies).
  - Clean OCR abstraction (`OcrProvider`, `OcrLabelType`, `OcrResult`, `ImageValidator`).
  - Strict image validation: magic bytes checking for JPEG (`FF D8 FF`), PNG (`89 50 4E 47`), WEBP (`RIFF...WEBP`), decodability, dimension bounds, size limits (10MB).
  - Rejection of invalid formats (PDF, ZIP, EXE, HTML, corrupted images) with HTTP 400.
  - Ephemeral file lifecycle: temporary files created with random UUID names and deleted in `finally` blocks.
  - Endpoints: `POST /api/analysis/session`, `GET /api/analysis/{sessionId}`, `POST /api/analysis/{sessionId}/ocr`.
  - Session TTL enforcement (15 minutes, returns HTTP 410 GONE when expired).
  - Support for partial OCR scans (either ingredients or nutrition table missing).
  - Correlation tracking via `X-Request-ID` header.
  - Frontend UI: `OcrResultModal.tsx` presenting raw unverified OCR text and processing duration with clear disclaimers.
  - Comprehensive unit test coverage: `ImageValidatorTest`, `TesseractOcrProviderTest`, `OcrServiceTest`, `AnalysisControllerTest`.

---

### Milestone M6: Gemini AI Normalization (Completed)
- **Primary Goal**: Utilize Google Gemini AI exclusively to clean, normalize, and extract structured semantic food data from OCR text.
- **Key Deliverables Completed**:
  - Secure Spring Boot `RestClient` Gemini client (`GeminiClient`, `GeminiProperties`, `GeminiPromptBuilder`).
  - Gemini API key strictly server-side (`GEMINI_API_KEY`) and configurable model (`GEMINI_MODEL`). Never exposed to React or logs.
  - Text-only input architecture: Gemini receives structured raw OCR text only, never image binaries.
  - Strict prompt guardrails: strictly forbids calculating health risks, Good/Bad scores, medical advice, or human/pet food categorization.
  - Output validation: `FoodNormalizationService` validates untrusted model responses, rejecting negative calories, negative nutrients, and impossible quantities (>100g per 100g).
  - Missing values preserved as `null` without zero-fabrication.
  - Endpoint: `POST /api/analysis/{sessionId}/normalize`.
  - Frontend UI: `NormalizedFoodModal.tsx` rendering structured ingredients, additives, INS codes, nutrition table, and uncertainties with clear AI disclaimers.
  - Comprehensive unit test coverage (mocked AI responses): `GeminiPromptBuilderTest`, `GeminiClientTest`, `FoodNormalizationServiceTest`, `AnalysisControllerNormalizeTest`.

---

### Milestone M7: Food Categorization & Intent Classification (Completed)
- **Primary Goal**: Verify product category and consumption intent using deterministic rule-based evidence hierarchy.
- **Key Deliverables Completed**:
  - Deterministic evaluation engine (`FoodClassificationEngine`) classifying products into:
    - `HUMAN_FOOD` (standard packaged human consumables)
    - `PET_FOOD` (dog food, cat kibble, animal companion formulations)
    - `ANIMAL_FEED` (livestock, poultry, cattle feed)
    - `NON_FOOD` (detergents, cleaners, cosmetics, packaging materials)
    - `UNKNOWN` (insufficient evidence or ambiguous markers)
  - Clear evidence hierarchy: Explicit packaging markers > Supporting ingredients/nutrition facts > Conflicting/insufficient markers default to `UNKNOWN`.
  - Machine-readable `ClassificationReasonCode` (`EXPLICIT_HUMAN_FOOD_MARKER`, `EXPLICIT_PET_FOOD_MARKER`, `EXPLICIT_ANIMAL_FEED_MARKER`, `EXPLICIT_NON_FOOD_MARKER`, `CONFLICTING_EVIDENCE`, `INSUFFICIENT_INFORMATION`, `UNKNOWN_PRODUCT`).
  - Strict tone guideline: Never accuses products of being "waste food".
  - Endpoint: `POST /api/analysis/{sessionId}/classify` with strict server-side state resolution via `AnalysisContextStore`.
  - Frontend components: `FoodClassificationCard.tsx` rendering category badge, certainty, rationale, extracted evidence, and non-human consumption warnings.

---

### Milestone M8: Ingredient Risk Analysis Engine — Core & Additive Evaluation (Completed)
- **Primary Goal**: Implement verified rule-based evaluation of individual ingredients and chemical additives based on authoritative FSSAI and WHO standards.
- **Key Deliverables Completed**:
  - Authoritative rule datasets: `sources.json` (FSSAI, WHO, Codex Alimentarius), `additives.json` (12 verified additives with INS/E codes, functional classes, regulatory status), and `ingredient-rules.json` (Palm Oil, Hydrogenated Fat, HFCS, Maida, Invert Sugar, etc.).
  - `RiskRuleRepository` / `JsonRiskRuleRepository` with canonical INS/E resolution (`INS 330` == `E330` == `INS330` == `Citric Acid`), alias matching, and strict rejection of ambiguous OCR characters (`?`, `*`).
  - `IngredientRiskEngine` evaluating normalized ingredients with strict decoupling:
    - Decouples `RegulatoryStatus` (`PERMITTED`, `RESTRICTED`, `BANNED`, `UNKNOWN`) from `IngredientRiskLevel` (`NO_CONCERN`, `LOW_ATTENTION`, `MODERATE_ATTENTION`, `HIGH_ATTENTION`, `UNKNOWN`). Permitted additives are not marked "bad"; unknown ingredients are not marked "banned".
    - Preserves M6 OCR uncertainty flags (`uncertain: true`) as unconfirmed items rather than confirmed hazards.
    - Deduplicates repeated ingredients in summary counts while preserving raw OCR entries for traceability.
    - Zero final product scoring or Good/Bad/Worst ratings (strictly deferred to M10).
  - State management abstraction: `AnalysisContextStore` and `InMemoryAnalysisContextStore` with thread-safe ConcurrentHashMap and 15-minute TTL eviction.
  - Endpoint: `POST /api/analysis/{sessionId}/ingredient-risk`.
  - Frontend components: `EvidenceBadge.tsx`, `IngredientRiskCard.tsx`, `IngredientRiskList.tsx`, and `AnalysisResultModal.tsx` with summary tiles, interactive filter tabs, and regulatory disclaimers.
  - Comprehensive automated test suite: 103 passing tests covering repository indexing, engine evaluation, service lifecycle, error responses (409/410/404), and controller endpoints.

---

### Milestone M9: Nutrition Analysis + WHO/FSSAI Rules
- **Status**: **Completed**
- **Primary Goal**: Evaluate quantitative nutrient metrics against official global and regional standards.
- **Key Architectural Deliverables**:
  - Authoritative reference dataset: `nutrition-rules.json` (indexed by `JsonNutritionRuleRepository`, versioned 2026.09).
  - Explicit reference types: `REGULATORY_LIMIT` (FSSAI trans fat), `DIETARY_GUIDELINE` (WHO free sugars, saturated fat, sodium), `NUTRITION_REFERENCE` (Codex fibre/protein benchmark), `PROJECT_HEURISTIC`.
  - Strict basis normalization: `PER_100G`, `PER_100ML`, `PER_SERVING`, `PER_PACKAGE`, `UNKNOWN`. Per-serving data requires explicit `servingSizeGrams` for mathematical conversion; otherwise returns `status: INSUFFICIENT_DATA`.
  - Missing != zero rule: Undeclared nutrients receive `NutrientValueState.NOT_DECLARED` rather than fabricated 0 values.
  - Beneficial nutrient detection: Positive indicators for dietary fibre (>= 3g/100g), dietary protein (>= 6g/100g), and low sodium (<= 120mg/100g).
  - Endpoint: `POST /api/analysis/{sessionId}/nutrition`.
  - Ephemeral caching in `AnalysisContextStore`.

---

### Milestone M10: Food Awareness Score + Guidance Engine
- **Status**: **Completed**
- **Primary Goal**: Synthesize all analysis modules into a transparent Food Awareness Score (0–100) and actionable consumer guidance.
- **Key Architectural Deliverables**:
  - Deterministic score calculation from `scoring-rules.json` (baseline: 100, min: 0, max: 100).
  - Named **Food Awareness Score** (0–100); strictly never called a WHO/FSSAI score or claimed as official certification.
  - Status thresholds:
    - 80–100: `GOOD_CHOICE`
    - 50–79: `NEEDS_ATTENTION`
    - 0–49: `HIGH_ATTENTION`
  - Anti-double-counting composite overlap rules:
    - `SUGAR_OVERLAP` (-20 penalty): replaces separate ingredient sugar and nutrition sugar penalties.
    - `TRANS_FAT_OVERLAP` (-25 penalty): replaces separate hydrogenated oil and trans fat penalties.
    - `SODIUM_OVERLAP` (-20 penalty): replaces separate salt and high sodium penalties.
  - Category priority: Pet food and non-food items immediately receive `overallScore = null` and `overallStatus = NOT_INTENDED_FOR_HUMAN_CONSUMPTION`.
  - Non-medical demographic guidance for general population, children, and special dietary needs.
  - Endpoint: `GET /api/analysis/{sessionId}/assessment`.
  - TypeScript types and API client functions integrated into `frontend/src/api/analysisApi.ts`.

---

### Milestone M11: Final Result UI + Full Frontend Integration
- **Primary Goal**: Build the complete, interactive scan results screen on the React 19 PWA.
- **Key Responsibilities**:
  - Visual summary card with overall Food Risk Score gauge and risk badge.
  - Tabbed breakdown:
    - Ingredient details with high-risk additive alerts and NOVA indicator.
    - Nutrition table with traffic-light indicators and % daily value bars.
    - Classification badge (Human food verification).
    - Official WHO / FSSAI reference accordion.
  - Persistent, prominent educational disclaimer banner.

---

### Milestone M12: Redis + Performance + 500 Concurrent Users
- **Primary Goal**: Optimize backend architecture, introduce Redis caching, and validate high concurrency.
- **Key Responsibilities**:
  - Redis cache integration for parsed ingredient definitions, common additive lookups, and token dictionaries.
  - HikariCP database connection pool tuning.
  - Spring Boot asynchronous thread pool execution (`@Async`) for independent OCR and Gemini analysis tasks.
  - Stress testing and benchmarking to guarantee stable performance under **500 concurrent users**.

---

### Milestone M13: End-to-End Integration + Resilience + Error Handling
- **Primary Goal**: End-to-end integration hardening, fault tolerance, and automated test coverage.
- **Key Responsibilities**:
  - Circuit breakers and retry policies for external AI/OCR services.
  - User-friendly error messaging for unreadable, blurry, or misaligned photos.
  - Comprehensive end-to-end integration tests (Spring Boot MockMvc, Cypress/Playwright).
  - Rate limiting to protect API endpoints against abuse.

---

### Milestone M14: Production Hardening + Deployment
- **Primary Goal**: Final production preparation, containerization, and PWA certification.
- **Key Responsibilities**:
  - Complete Lighthouse PWA audit (100% PWA criteria, installability, offline caching).
  - Multi-stage `Dockerfile` for React 19 frontend (Nginx production image).
  - Multi-stage `Dockerfile` for Spring Boot backend (Eclipse Temurin JRE 17).
  - Orchestration via `docker-compose.yml`.
  - Production environment configuration and deployment runbook.
