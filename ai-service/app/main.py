"""SmartFlow AI — specialised AI/ML service (FastAPI).

The core platform never depends on this service: every endpoint has a deterministic fallback implemented in
the Spring Boot backend, which is used automatically when this service is unreachable.
"""
import logging
import os

from fastapi import FastAPI, HTTPException

from . import anomalies, forecasting, routing
from .models import AnomalyRequest, ForecastRequest, ForecastResult, Problem, Solution

logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
app = FastAPI(title="SmartFlow AI service", version="1.0.0",
              description="Route optimization (OR-Tools), demand forecasting (statsmodels) and anomaly detection (scikit-learn).")


@app.get("/health")
def health():
    return {"status": "UP"}


@app.post("/routing/optimize", response_model=Solution)
def optimize(problem: Problem):
    if not problem.stops:
        raise HTTPException(400, "No stops to route")
    if not problem.vehicles:
        raise HTTPException(400, "No vehicles available")
    try:
        return routing.solve(problem, int(os.getenv("ROUTING_TIME_LIMIT_S", "8")))
    except RuntimeError as e:
        raise HTTPException(422, str(e)) from e


@app.post("/forecast", response_model=ForecastResult)
def forecast(req: ForecastRequest):
    if len(req.series) < 8:
        raise HTTPException(400, "At least 8 observations are required")
    return forecasting.forecast(req)


@app.post("/quality/anomalies")
def quality_anomalies(req: AnomalyRequest):
    return anomalies.detect(req)
