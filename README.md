# SmartFlow AI + QHSE

**One intelligent platform connecting industrial operations, distribution and QHSE.**
Developed by **D&D | Data&Development** — prepared for the *Smart Care Challenge 2026*.

SmartFlow AI is a multi-tenant enterprise SaaS platform. It covers two pillars:

| Pillar 1 — Smart distribution & operations | Pillar 2 — Integrated QHSE |
|---|---|
| Orders with FEFO batch allocation, shipments, deliveries | Non-conformities with configurable workflow, 5-Whys RCA, CAPA |
| Inventory by batch / location, immutable stock movements | Digital checklists (quality, hygiene, safety, environment) with scoring, conditional questions, signature, photos |
| Forward / backward batch traceability and one-click recall | Incidents, near misses, environmental & sanitary events |
| Fleet, drivers, fuel, GPS positions | 5×5 risk matrix, residual risk, job safety analysis, permits to work |
| AI route optimization (capacity, priorities, time windows, CO2) | Audits & findings, controlled documents with approval and versions |
| Demand forecasting, stockout risk, replenishment | Training matrix, habilitations, PPE, cleaning plans, pest control |
| Carbon footprint (transport + energy) | Waste, water, energy, chemicals, ESG indicators |

Cross-cutting: real-time control tower, role-based dashboards, AI Center, FR/EN/AR assistant (with RTL), Integration Hub (SAP, Odoo, CMMS/GMAO, GPS, IoT), notifications (in-app real-time + e-mail), Excel/PDF reports, QR codes, audit trail, and an industry configuration engine (9 profiles).

> **Honesty notes.** ISO 9001 / 14001 / 45001 are *configurable reference frameworks*. Using the platform does not certify anyone. In the demo, the SAP / Odoo / CMMS / GPS / IoT connectors are **DEMO / SIMULATED**. The live connectors (SAP OData, Odoo JSON-RPC, generic REST) are implemented but have not been run against customer systems. Every analytic is labelled **Measured**, **Prediction**, **Statistical** or **Risk index**. Risk indices are transparent weighted scores, not trained models.

---

## Quick start (Docker)

```bash
cp .env.example .env          # then replace every secret
docker compose up -d --build
```

Open **http://localhost:8088/demo**. The landing page shows the QR code and demonstration accounts. The first start seeds the demo dataset in about 5 seconds.

| Role | E-mail | Password |
|---|---|---|
| Super admin | demo.admin@smartflowai.demo | `Demo@2026!` |
| QHSE director | demo.qhse@smartflowai.demo | `Demo@2026!` |
| Quality manager (Constantine & Sétif only) | demo.quality@smartflowai.demo | `Demo@2026!` |
| Safety manager | demo.safety@smartflowai.demo | `Demo@2026!` |
| Logistics manager | demo.logistics@smartflowai.demo | `Demo@2026!` |
| Employee (line L2 operator) | demo.employee@smartflowai.demo | `Demo@2026!` |
| General manager / Hygiene / Environment / Auditor | demo.gm@ · demo.hygiene@ · demo.environment@ · demo.auditor@smartflowai.demo | `Demo@2026!` |
| 2nd tenant (pharma, isolation demo) | admin@saharanpharma.demo | `Demo@2026!` |

These credentials exist only when `DEMO_ENABLED=true`. They are never meant for production: set `DEMO_ENABLED=false` for real tenants.

## Local development

Prerequisites: Java 21, Maven 3.9, Node 22, Python 3.12, PostgreSQL 16 (Redis optional).

```bash
# database
createuser smartflow -P && createdb -O smartflow smartflow
# backend  (http://localhost:8080, Swagger UI at /swagger-ui.html)
cd backend && mvn spring-boot:run
# AI service (optional — the backend falls back to built-in engines)
cd ai-service && python -m venv .venv && . .venv/bin/activate && pip install -r requirements-dev.txt && uvicorn app.main:app --port 8000
# frontend (http://localhost:4200, proxies /api and /ws to :8080)
cd frontend && npm ci && python3 i18n-src/build.py && npx ng serve
```

Tests: `cd backend && mvn verify` (set `SMARTFLOW_IT=true` and `IT_DB_URL` to also run the PostgreSQL end-to-end test), `cd ai-service && pytest`, `cd frontend && npx ng build`.

## Architecture (summary)

```
 Browser (Angular 22, signals, ECharts, Leaflet, ngx-translate FR/EN/AR + RTL)
     │  HTTPS / WebSocket (/ws/events)
 Nginx ──► Spring Boot 3.5 modular monolith (Java 21) ──► PostgreSQL 16 (Flyway)
                 │   JWT + rotating refresh tokens, RBAC, tenant isolation       ▲
                 │   workflow engine, analytics, integration hub, demo seeding    │ Redis (cache)
                 └──► FastAPI AI service (OR-Tools, statsmodels, scikit-learn) — optional, with fallbacks
                 └──► External systems: SAP S/4HANA (OData), Odoo (JSON-RPC), CMMS / GPS / IoT (REST, webhooks)
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for modules, security model, data model and design decisions. See [docs/DEMO_GUIDE.md](docs/DEMO_GUIDE.md) for a guided demonstration scenario.

## Repository layout

```
backend/      Spring Boot modular monolith (domains: organization, assets, catalog, inventory, distribution, fleet,
              traceability, qhse, workflow, integration, analytics, ai, notification, reporting, tenant, iam, auth, demo…)
ai-service/   FastAPI service: /routing/optimize, /forecast, /quality/anomalies
frontend/     Angular application (core, shared, layout, features/*), i18n sources in frontend/i18n-src
deploy/k8s/   Reference Kubernetes manifests
docs/         Architecture and demonstration guide
```
