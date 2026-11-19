import math
import random

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health():
    assert client.get("/health").json() == {"status": "UP"}


def test_routing_respects_capacity_and_beats_manual_plan():
    rnd = random.Random(3)
    towns = ["Blida", "Alger", "Boumerdès", "Tipaza", "Médéa"]
    stops = [{"id": f"s{i}", "label": f"C{i} ({rnd.choice(towns)})", "latitude": 36.4 + rnd.random() * 0.5,
              "longitude": 2.7 + rnd.random(), "demandKg": 300 + rnd.randint(0, 900), "priority": 1,
              "timeWindowStart": None, "timeWindowEnd": None, "serviceMinutes": 15} for i in range(20)]
    vehicles = [{"id": "v1", "label": "T1", "capacityKg": 7500}, {"id": "v2", "label": "T2", "capacityKg": 7500}]
    body = {"depot": {"latitude": 36.71, "longitude": 3.17}, "stops": stops, "vehicles": vehicles}
    res = client.post("/routing/optimize", json=body)
    assert res.status_code == 200, res.text
    sol = res.json()
    assert "OR-Tools" in sol["engine"]
    assert all(r["loadKg"] <= r["capacityKg"] for r in sol["routes"])
    assert sum(len(r["stops"]) for r in sol["routes"]) + len(sol["unassignedStopIds"]) == 20
    assert sol["optimizedDistanceKm"] <= sol["originalDistanceKm"]


def test_forecast_seasonal_series():
    series = [{"period": f"w{t}", "value": 200 + t + 40 * math.sin(2 * math.pi * t / 52)} for t in range(120)]
    res = client.post("/forecast", json={"series": series, "horizon": 6, "seasonLength": 52}).json()
    assert len(res["forecast"]) == 6
    assert res["mape"] is not None and res["mape"] < 15
    assert all(lo <= f <= hi for lo, f, hi in zip(res["lower"], res["forecast"], res["upper"]))


def test_anomaly_flags_spike():
    series = [{"id": str(i), "label": f"L{i}", "values": [1, 0, 1, 1, 0, 1, 2, 1, 0, 1, 1, 0, 1]} for i in range(5)]
    series.append({"id": "spike", "label": "L2", "values": [0, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 1, 6]})
    res = client.post("/quality/anomalies", json={"series": series}).json()
    assert [f["id"] for f in res["flagged"]] == ["spike"]
