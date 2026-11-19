"""Builds public/i18n/{en,fr,ar}.json from the translation sources. Run: python3 i18n-src/build.py"""
import json, os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
from ui import UI
from terms import F, E, RES, KPI, MOD

out = {"en": {}, "fr": {}, "ar": {}}
def put(key, triple):
    for i, lang in enumerate(("en", "fr", "ar")):
        node = out[lang]
        parts = key.split(".")
        for p in parts[:-1]:
            node = node.setdefault(p, {})
        node[parts[-1]] = triple[i]
for k, v in UI.items(): put(k, v)
for line in F.strip().splitlines():
    k, en, fr, ar = line.split("|"); put("fields." + k, (en, fr, ar))
for line in E.strip().splitlines():
    k, en, fr, ar = line.split("|")
    for i, lang in enumerate(("en", "fr", "ar")):
        out[lang].setdefault("enum", {})[k] = (en, fr, ar)[i]
for k, v in RES.items():
    for i, lang in enumerate(("en", "fr", "ar")): out[lang].setdefault("res", {})[k] = v[i]
for k, v in KPI.items(): put("kpi." + k, v)
for k, v in MOD.items(): put("module." + k, v)
# breadcrumbs: route segments
crumbs = {"dashboard": "nav.executive", "qhse": "nav.qhseDashboard", "distribution": "nav.distributionDashboard", "analytics": "nav.analytics", "orders": "nav.orders",
          "deliveries": "nav.deliveries", "route-planning": "nav.routes", "fleet": "nav.fleet", "inventory": "nav.inventory", "batches": "nav.batches", "traceability": "nav.traceability",
          "warehouses": "nav.warehouses", "products": "nav.products", "customers": "nav.customers", "suppliers": "nav.suppliers", "equipment": "nav.equipment",
          "non-conformities": "nav.nonConformities", "capa": "nav.capa", "incidents": "nav.incidents", "inspections": "nav.inspections", "checklists": "nav.checklists",
          "risk-assessments": "nav.risks", "work-permits": "nav.permits", "audits": "nav.audits", "documents": "nav.documents", "training": "nav.training", "hygiene": "nav.hygiene",
          "environment": "nav.environment", "ai": "nav.aiCenter", "assistant": "nav.assistant", "reports": "nav.reports", "sites": "nav.sites", "departments": "nav.departments",
          "employees": "nav.employees", "integrations": "nav.integrations", "workflows": "nav.workflows", "users": "nav.users", "settings": "nav.settings", "demo-admin": "nav.demoAdmin",
          "notifications": "nav.notifications", "profile": "shell.profile", "production-lines": "res.production-lines", "forbidden": "forbidden.title"}
for seg, key in crumbs.items():
    v = UI.get(key) or (RES.get(key[4:]) if key.startswith("res.") else None)
    if v: put("crumb." + seg, v)
here = os.path.join(os.path.dirname(__file__), "..", "public", "i18n")
for lang, data in out.items():
    json.dump(data, open(os.path.join(here, f"{lang}.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
print({k: sum(1 for _ in json.dumps(v)) for k, v in out.items()})
