# SmartFlow AI + QHSE — Architecture

## 1. Principles
- **Modular monolith.** One deployable backend organized by business domain (`com.dnd.smartflow.<domain>`). Domains reference each other **by ID only**: there are no cross-aggregate JPA relations. That keeps aggregates small and makes later extraction into services possible without schema surgery.
- **Configuration over code.** Industry profiles, enabled modules, terminology, reference lists, checklist templates, workflow definitions, KPI targets, custom fields and notification rules are tenant data.
- **Honest analytics.** Every KPI is SQL over persisted records. Predictions state their model and back-tested error. Heuristic scores are labelled *risk index*. AI components degrade gracefully to deterministic engines.

## 2. Backend modules
| Package | Responsibility |
|---|---|
| `common` | Tenant-aware persistence base, generic CRUD layer (filters, search, sort, pagination, export, audit), validation, errors (RFC 7807), security (JWT, RBAC, AES-GCM credential cipher) |
| `tenant` | Tenants, settings (JSON), industry profile catalog (`config/industry-profiles.json`) |
| `iam`, `auth`, `audit` | Users, roles, login/refresh/logout/reset, sessions, login history, lockout, rate limiting, audit trail |
| `organization`, `assets` | Sites, departments, production lines, employees, equipment, maintenance work orders |
| `catalog`, `inventory`, `distribution`, `fleet` | Products, suppliers, customers, warehouses, batches, stock levels/movements, orders (FEFO), deliveries, vehicles, drivers, fuel, GPS positions, route plans |
| `traceability` | Backward/forward trace, recall (batch block, allocation release, critical NC, alerts, impact PDF) |
| `qhse` | NC, CAPA, incidents, inspections & templates (checklist scoring), risk assessments/JSA, permits, audits & findings, documents, training, certifications, PPE, cleaning, pest control, environmental readings, waste, chemicals |
| `workflow` | Configurable workflow engine (states, transitions, permissions, editable/required fields, comments, evidence, guards), history, comments, attachments |
| `analytics` | Executive / QHSE / distribution dashboards, live alerts, operational risk index |
| `ai` | Routing (OR-Tools via AI service, Clarke-Wright + 2-opt fallback), forecasting (statsmodels via AI service, Holt/Holt-Winters fallback), quality/maintenance/QHSE insights, assistant with LLM provider abstraction (OpenAI, OpenAI-compatible, Ollama) |
| `integration` | Connector SPI, SAP OData, Odoo JSON-RPC, generic REST, simulated ERP/CMMS/GPS/IoT, sync engine, scheduler, webhooks |
| `notification`, `reporting`, `qr`, `files`, `demo` | Real-time WebSocket push + e-mail, Excel/PDF exports, QR codes, evidence storage, demo dataset lifecycle |

## 3. Security model
- **Authentication.** Access JWT (HS256, 15 min) and an opaque refresh token (SHA-256 hashed, 7 days). Refresh tokens rotate on every use, and reusing a rotated token revokes all of the user's sessions. Passwords are hashed with BCrypt(11) and must meet a strength policy. After 5 failures the account locks for 15 minutes. Authentication endpoints have a per-IP sliding-window limit. Password reset uses a single-use, time-limited code delivered by e-mail, and the response never reveals whether an account exists.
- **Authorization.** 17 roles map to `module:action` grants (read, write, delete, approve, admin). A grant applies only if the tenant has enabled the module. Users can be restricted to specific sites, which filters lists, detail reads, dashboards and alerts. Workflow transitions declare their own required permission.
- **Tenant isolation.** Every business table carries `tenant_id`, handled by Hibernate `@TenantId`, so every query is filtered and every insert stamped automatically. The tenant comes from the token. Only `SUPER_ADMIN` may switch organization (`X-Tenant-Id`). A reserved root context is used only for authentication and cross-tenant jobs.
- **Data protection.** Integration credentials and LLM API keys are encrypted with AES-256-GCM and never returned to clients. Uploads are checked against an allow-list of types and stored under server-generated keys. Webhooks require an HMAC-SHA256 signature. Updates use an optimistic-locking version check. All mutations are written to the audit trail.

## 4. Workflow engine
Definitions (`workflow_definitions`) are JSON lists of states and transitions. Each transition has:
`code`, `label`, `from[]`, `to`, `permission`, `fields[]` (editable during the step), `requiredFields[]`, `requiresComment`, `requiresEvidence`, `guards[]`.

Guards are implemented by `WorkflowListener` beans. For example, `capasCompleted` blocks verification while linked corrective actions are open. Side effects (closure timestamps, notifications, obsolescence of previous document versions) are applied in the same transaction. The default definitions are NC, CAPA, safety incident, environmental incident and document approval.

## 5. Integration hub
`ConnectorProvider` SPI: `test()` and `fetch(entityType, cursor)` return canonical records (PRODUCT, CUSTOMER, SUPPLIER, STOCK, EQUIPMENT, WORK_ORDER, VEHICLE_POSITION, SENSOR_READING). The `SyncEngine` provides:
- incremental cursors per entity;
- idempotency (payload hash per external ID);
- a separate transaction per record;
- validation;
- conflict policies (REMOTE_WINS / LOCAL_WINS / MANUAL);
- a failed-record log with retry;
- per-connector throttling and client-side HTTP throttling;
- scheduled runs and signed webhooks.

Stock snapshots from an ERP are applied as traceable ADJUSTMENT movements rather than silent overwrites.

## 6. AI & analytics
| Capability | Primary engine | Fallback | Nature |
|---|---|---|---|
| Route optimization | OR-Tools CVRPTW (guided local search) | Clarke-Wright savings + 2-opt | Estimate (distance model) |
| Demand forecast | statsmodels exponential smoothing selected on hold-out | Holt / Holt-Winters with grid search | Prediction + measured MAPE |
| NC anomaly detection | Isolation Forest + z-score confirmation | z-score | Statistical |
| Supplier / batch / equipment risk | Transparent weighted indices | — | Risk index |
| Assistant | Intent router over authorized data, optional LLM rephrasing grounded on retrieved facts | Deterministic answers | As stated per answer |

## 7. Data model & migrations
Flyway migrations: `V1__core.sql` (tenancy, identity, audit, sequences, connectors), `V2__domain.sql` (around 50 business tables) and `V3__indexes.sql`. Hibernate runs in `validate` mode. Business references (e.g. `NC-2026-0042`) come from a concurrency-safe `reference_sequences` upsert.

## 8. Deployment
`docker-compose.yml` runs PostgreSQL, Redis, the AI service, the backend and Nginx (SPA + reverse proxy for `/api`, `/ws`, Swagger). `deploy/k8s/smartflow.yaml` provides reference manifests: probes, resources, ingress, and config/secret separation. The backend is stateless apart from the evidence volume, which can be replaced with S3/MinIO behind `StorageService`.

## 9. Known limitations / next steps
- The SAP and Odoo live connectors follow the public APIs (API_PRODUCT_SRV, API_BUSINESS_PARTNER, API_MATERIAL_STOCK_SRV, API_EQUIPMENT, API_MAINTENANCEORDER; Odoo `execute_kw`). Field mappings must be validated against each customer system. IDoc, RFID, LIMS, SMS and WhatsApp adapters are not implemented yet; the generic REST connector covers REST-capable devices.
- Predictive maintenance is a transparent risk index. A supervised model needs real failure history.
- Spring Batch is not used: synchronization is record-oriented and transactional per record.
- Checklist question texts in the template library are English data; they can be edited per tenant in the checklist designer.
- Server-generated texts (alert titles, PDF labels) are in English/French; UI chrome is fully translated FR/EN/AR.
