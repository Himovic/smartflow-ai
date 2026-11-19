"""Anomaly detection on weekly non-conformity counts per production line (Isolation Forest)."""
import numpy as np
from sklearn.ensemble import IsolationForest

from .models import AnomalyRequest


def detect(req: AnomalyRequest) -> dict:
    rows, feats = [], []
    for s in req.series:
        v = np.array(s.values, dtype=float)
        if len(v) < 6:
            continue
        base, last = v[:-1], v[-1]
        mean, sd = float(base.mean()), float(base.std(ddof=1)) if len(base) > 1 else 0.0
        z = (last - mean) / sd if sd > 0 else (3.0 if last > mean else 0.0)
        rows.append((s, last, mean, z))
        feats.append([last, mean, sd, last - mean, z])
    flagged = []
    if len(rows) >= 3:
        model = IsolationForest(n_estimators=200, contamination="auto", random_state=42).fit(np.array(feats))
        scores = -model.score_samples(np.array(feats))  # higher = more anomalous
        threshold = float(np.percentile(scores, 75))
        for (s, last, mean, z), score in zip(rows, scores):
            if score >= threshold and z >= 2 and last >= 2:
                flagged.append({"id": s.id, "label": s.label, "lastWeek": last, "baselineMean": round(mean, 1),
                                "zScore": round(z, 1), "anomalyScore": round(float(score), 3)})
    else:
        for s, last, mean, z in rows:
            if z >= 2 and last >= 2:
                flagged.append({"id": s.id, "label": s.label, "lastWeek": last, "baselineMean": round(mean, 1), "zScore": round(z, 1)})
    return {"method": "Isolation Forest on weekly NC counts (scikit-learn) confirmed by z-score ≥ 2", "nature": "STATISTICAL", "flagged": flagged}
