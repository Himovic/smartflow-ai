# Demonstration guide — Atlas Industries Group (fictional)

**Organization:** Atlas Industries Group, an FMCG company in Algeria making pasta, couscous, biscuits, tissue paper and baby diapers.
**Sites:** Constantine Industrial Complex (HQ, 4 lines), Algiers Distribution Center, Oran Regional Warehouse, Sétif Production Plant (3 lines).
**Data:** about 18 months of production batches, about 3,000 orders / 15,000 order lines / 3,000 deliveries, 19 trucks, 132 employees and 1,600 inspections. It also includes 260 NCs with full workflow histories, 330 CAPA, 70 incidents, risks and JSAs, audits and findings, documents, training, waste, energy, and IoT readings.

## 10-minute storyline
1. **Landing (`/demo`)**: scan the QR code and pick a role.
2. **QHSE director → Control tower**: show the KPIs vs targets (and their labels: measured / risk index), the operational risk gauge, live alerts and the site map.
3. **Critical NC**: open *"Corps étranger métallique signalé par un client"*. Point out the batch blocked automatically, the workflow stepper, the history, and the PDF report.
4. **Traceability & recall**: from the NC, open the batch. Show supplier and raw-material lots, inspections, remaining stock per warehouse and the customers reached. Click **Recall batch** and download the impact report.
5. **Employee (mobile)**: report an NC with category *Allergen* (severity is escalated to CRITICAL by the rules), then run a hygiene checklist on a phone. A failed critical criterion produces a FAIL result and auto-generates NCs.
6. **Logistics → Route planning**: optimize today's 22 Algiers deliveries. Compare the manual plan with the optimized one (km, fuel, CO2), then dispatch.
7. **AI Center**: show the forecast with its prediction interval and back-tested MAPE, the anomaly on line L2 (sealing defects spike) and the supplier index (PlastEmball).
8. **Assistant**: ask *"Quelles actions CAPA sont en retard ?"*, then in Arabic *"ما هي أخطر حالات عدم المطابقة هذا الشهر؟"*. Answers carry record references.
9. **Integration hub (admin)**: SAP / Odoo / SAP PM / GPS / IoT connectors are clearly marked DEMO / SIMULATED. Run a sync, then show the failed record (unknown warehouse `WH-BLIDA`) and its retry.
10. **Configuration**: switch the industry profile, toggle modules, and change the language to Arabic (RTL).

## Built-in scenarios to look for
- Line **L2** (short pasta) has recurrent sealing defects and a spike this week, which the anomaly detector flags.
- Supplier **PlastEmball** fails film-thickness checks, giving it the lowest supplier index.
- **Couscous moyen 1 kg**, **Biscuit Choco** and **Couches T4** are low on stock (stockout alerts).
- **Compressor — Sétif** is down, and the **biscuit oven** has overdue preventive maintenance.
- A serious hand injury on line **L7** happened yesterday and is awaiting investigation.
- The **ISO 9001 certificate** expires within about 40 days, and one SDS has expired.

The demo is reset nightly (03:00, Africa/Algiers) and can be reset on demand from *Demo administration*.
