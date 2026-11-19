"""Pydantic schemas mirroring the Java records exchanged with the SmartFlow backend (camelCase JSON)."""
from typing import List, Optional

from pydantic import BaseModel, Field


class Location(BaseModel):
    latitude: float
    longitude: float


class Stop(BaseModel):
    id: str
    label: str = ""
    latitude: float
    longitude: float
    demandKg: float = 0
    priority: int = 1
    timeWindowStart: Optional[int] = None
    timeWindowEnd: Optional[int] = None
    serviceMinutes: int = 15


class VehicleSpec(BaseModel):
    id: str
    label: str = ""
    capacityKg: float
    consumptionPer100km: float = 28.0
    co2FactorKgPerLiter: float = 2.68


class Problem(BaseModel):
    depot: Location
    stops: List[Stop]
    vehicles: List[VehicleSpec]
    averageSpeedKmh: float = 55.0
    roadFactor: float = 1.3
    maxRouteMinutes: int = 660


class RouteStop(BaseModel):
    stopId: str
    label: str
    sequence: int
    latitude: float
    longitude: float
    distanceFromPreviousKm: float
    arrivalMinute: float
    timeWindowRespected: bool


class VehicleRoute(BaseModel):
    vehicleId: str
    label: str
    stops: List[RouteStop]
    distanceKm: float
    durationMinutes: float
    loadKg: float
    capacityKg: float
    fuelLiters: float
    co2Kg: float


class Solution(BaseModel):
    engine: str
    routes: List[VehicleRoute]
    unassignedStopIds: List[str]
    originalDistanceKm: float
    optimizedDistanceKm: float
    estimatedDurationMinutes: float
    estimatedFuelLiters: float
    estimatedCo2Kg: float
    originalCo2Kg: float
    timeWindowViolations: int
    notes: List[str] = Field(default_factory=list)


class SeriesPoint(BaseModel):
    period: str
    value: float


class ForecastRequest(BaseModel):
    series: List[SeriesPoint]
    horizon: int = 12
    seasonLength: int = 52


class ForecastResult(BaseModel):
    model: str
    forecast: List[float]
    lower: List[float]
    upper: List[float]
    mape: Optional[float]
    holdout: int


class AnomalySeries(BaseModel):
    id: str
    label: str
    values: List[float]


class AnomalyRequest(BaseModel):
    series: List[AnomalySeries]
