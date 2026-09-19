# Food Risk Analysis PWA — Project Overview

## 1. Executive Summary & Vision

Modern consumers are increasingly confronted with packaged foods containing complex chemical additives, hidden sodium, excessive free sugars, emulsifiers, and industrial processing aids. Ingredients lists are frequently presented in dense, fine print or masked behind technical E-numbers and INS codes, while nutritional tables are structured around obscure, non-standard serving sizes.

The **Food Risk Analysis PWA** is an awareness-oriented, mobile-first Progressive Web App designed to bridge the information gap between food manufacturers and everyday consumers. By capturing images of both the **ingredients label** and the **nutrition table**, users receive instant, clear, and scientifically grounded insights into what they are about to consume.

---

## 2. Non-Medical Disclaimer & Awareness Orientation

> [!IMPORTANT]
> **Strict Non-Medical Scope**:
> - The Food Risk Analysis PWA is exclusively an **educational and awareness tool**.
> - It **must never** present itself as a medical diagnostic device, clinical advisory system, or therapeutic prescription engine.
> - The system does not diagnose allergies, metabolic syndromes, or medical conditions.
> - All evaluations represent general dietary awareness derived from public reference standards established by the World Health Organization (WHO) and the Food Safety and Standards Authority of India (FSSAI).
> - Explicit disclaimer prompts must accompany all analysis screens.

---

## 3. End-to-End User Workflow (Target Experience)

The application facilitates a seamless, mobile-optimized 13-step journey:

```text
[1. Open PWA]
      ↓
[2. Register / Login] (M3)
      ↓
[3. Capture / Upload Ingredients Image] (M4)
      ↓
[4. Capture / Upload Nutrition Table Image] (M4)
      ↓
[5. OCR Text Extraction] (M5)
      ↓
[6. Gemini AI Normalization & Structured Extraction] (M6)
      ↓
[7. Food Classification (Human / Animal / Non-Food)] (M7)
      ↓
[8. Ingredient Risk Evaluation (NOVA, UPF, Additives)] (M8)
      ↓
[9. Nutrition Analysis (WHO / FSSAI Thresholds)] (M9)
      ↓
[10. Food Risk Score & Eating Guidance Synthesis] (M10)
      ↓
[11. Interactive Results Presentation] (M11)
      ↓
[12. Regulatory References (WHO/FSSAI)] (M11)
      ↓
[13. Actionable Consumer Awareness] (M11)
```

1. **Open PWA**: User launches the app from their mobile browser or homescreen icon (installed PWA).
2. **Register or Log In**: User authenticates with stateless credentials.
3. **Capture/Upload Ingredients Label**: High-resolution photo capture or file upload focusing on the ingredient statement.
4. **Capture/Upload Nutrition Table**: Photo capture or file upload focusing on nutritional facts (per 100g / per serving).
5. **OCR Extraction**: High-accuracy text extraction pulls raw textual data from both images.
6. **Gemini AI Understanding & Normalization**: Multimodal LLM parses ambiguous OCR output, resolves OCR errors, standardizes additive numbers (e.g. INS 621 → Monosodium Glutamate), and outputs normalized JSON.
7. **Intent Classification**: Evaluates whether the product is human food, pet/animal feed, non-edible household goods, or indeterminate.
8. **Ingredient Risk Engine**: Analyzes additives, emulsifiers, artificial sweeteners, preservatives, and degree of industrial processing (NOVA scale).
9. **Nutrition Analysis Engine**: Computes nutrient density and compares sodium, free sugar, saturated fat, and trans fat against WHO and FSSAI maximum thresholds.
10. **Scoring & Guidance Synthesis**: Computes a transparent composite Food Risk Score (0–100) and assigns a Risk Band (Low, Moderate, High, Critical).
11. **Eating Guidance**: Formulates consumer-friendly consumption recommendations (e.g., occasional treat vs. regular staple).
12. **WHO/FSSAI References**: Displays citations to official dietary guidelines for transparency and scientific trust.
13. **Awareness Presentation**: Delivers rich visual breakdown without diagnostic claims.

---

## 4. Technology Decisions

| Domain | Technology | Justification |
| :--- | :--- | :--- |
| **Frontend Framework** | **React 19** | Industry standard for rich reactive interfaces, modern concurrent rendering, and hooks architecture. |
| **Language** | **TypeScript** | Eliminates runtime type errors, enforces strict API contracts between client and server, and improves maintainability for student engineers. |
| **Build Tool** | **Vite** | Sub-second HMR, optimized Rollup production bundles, native ES modules. |
| **PWA Layer** | **vite-plugin-pwa** | Service worker lifecycle management, offline asset caching, standalone mobile installation. |
| **Styling & 3D** | **Mobile-First CSS + 3D UI** | Glassmorphism aesthetic, custom mobile-first responsive tokens, and authored Brand Orbs `aura` component for visual immersion. |
| **Backend Framework** | **Spring Boot 3.3.x** | Enterprise-grade Java framework with auto-configuration, robust dependency injection, and battle-tested production readiness. |
| **Language** | **Java 17 (LTS)** | Long Term Support standard offering modern language constructs (records, pattern matching, sealed classes). |
| **Build Tool** | **Apache Maven** | Standardized build lifecycle, predictable dependency management across platforms. |
| **Web Layer** | **Spring Web (MVC)** | High-throughput REST API controllers, structured request/response validation. |
| **Security Layer** | **Spring Security** | Stateless authentication filters, BCrypt password hashing, and CSRF protection. |
| **Persistence** | **Spring Data JPA & PostgreSQL** | Declarative repositories, ACID transactional safety, and scalable relational schema modeling. |

---

## 5. Root Folder Structure

The project enforces strict separation between client, server, and architectural documentation:

```text
food-risk-analysis/
├── frontend/                     # All React 19 PWA client code
├── backend/                      # All Spring Boot Java 17 backend code
├── docs/                         # Architecture, roadmap, and project specifications
│   ├── project-overview.md       # Project vision, workflow, tech stack, data privacy
│   ├── development-milestones.md # Full M0-M14 milestone roadmap and deliverable details
│   └── architecture-notes.md     # Architecture diagram, 500 CCU design, privacy lifecycle
└── README.md                     # Central onboarding, setup, and verification guide
```

---

## 6. Data Privacy Principles

User data protection and privacy-by-design are fundamental pillars:

### Permanent User Data (Minimal Footprint)
Only indispensable account credentials are stored persistently in the database:
- `id` (UUID / Long primary key)
- `name` (Display name)
- `email` (Unique login identifier)
- `password_hash` (BCrypt salt + hash; raw passwords never stored)
- `created_at` (Audit timestamp)

### Temporary Food Data (Ephemeral Lifecycle)
- Individual food photos and scan analysis results **must not** automatically become permanent user history.
- Scanned images and extracted nutritional records reside in memory or short-lived cache only during active processing.
- The lifecycle strictly follows:
  ```text
  Upload → Process (OCR) → Analyze (Gemini + Engines) → Return Result → Ephemeral Cache Expiry / Purge
  ```
- No food scanning history or image archives are stored permanently in the database.
- Database entities for food history are strictly forbidden.
