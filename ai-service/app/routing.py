"""Capacitated vehicle routing with time windows solved with Google OR-Tools.

Distances use the great-circle distance multiplied by a road factor (same model as the backend fallback),
so the "before / after" comparison is consistent across engines. The manual reference plan groups stops by
town and loads vehicles first-fit, as dispatchers typically do.
"""
import math
from typing import List

from ortools.constraint_solver import pywrapcp, routing_enums_pb2

from .models import Problem, RouteStop, Solution, VehicleRoute


def haversine_km(lat1, lon1, lat2, lon2) -> float:
    r = 6371.0
    d_lat, d_lon = math.radians(lat2 - lat1), math.radians(lon2 - lon1)
    a = math.sin(d_lat / 2) ** 2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(d_lon / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def distance_matrix(p: Problem) -> List[List[float]]:
    pts = [(p.depot.latitude, p.depot.longitude)] + [(s.latitude, s.longitude) for s in p.stops]
    return [[0.0 if i == j else haversine_km(*pts[i], *pts[j]) * p.roadFactor for j in range(len(pts))] for i in range(len(pts))]


def _fuel(km: float, per100: float) -> float:
    return round(km * (per100 if per100 > 0 else 28.0) / 100.0, 2)


def _build_route(p: Problem, d, vehicle, seq: List[int]) -> VehicleRoute:
    t, dist, prev, stops = 0.0, 0.0, 0, []
    for k, i in enumerate(seq, start=1):
        s = p.stops[i - 1]
        leg = d[prev][i]
        dist += leg
        t += leg / p.averageSpeedKmh * 60
        if s.timeWindowStart is not None and t < s.timeWindowStart:
            t = s.timeWindowStart
        ok = s.timeWindowEnd is None or t <= s.timeWindowEnd
        stops.append(RouteStop(stopId=s.id, label=s.label, sequence=k, latitude=s.latitude, longitude=s.longitude,
                               distanceFromPreviousKm=round(leg, 2), arrivalMinute=round(t), timeWindowRespected=ok))
        t += s.serviceMinutes
        prev = i
    dist += d[prev][0]
    t += d[prev][0] / p.averageSpeedKmh * 60
    fuel = _fuel(dist, vehicle.consumptionPer100km)
    return VehicleRoute(vehicleId=vehicle.id, label=vehicle.label, stops=stops, distanceKm=round(dist, 2), durationMinutes=round(t),
                        loadKg=sum(p.stops[i - 1].demandKg for i in seq), capacityKg=vehicle.capacityKg, fuelLiters=fuel,
                        co2Kg=round(fuel * vehicle.co2FactorKgPerLiter, 2))


def manual_plan(p: Problem, d) -> List[VehicleRoute]:
    def town(label: str) -> str:
        return label[label.rfind("(") + 1:].strip(") ") if "(" in label else label
    order = sorted(range(1, len(p.stops) + 1), key=lambda i: town(p.stops[i - 1].label))
    routes, seq, load, vehicles = [], [], 0.0, list(p.vehicles)
    current = vehicles.pop(0) if vehicles else None
    for i in order:
        dem = p.stops[i - 1].demandKg
        if current and seq and load + dem > current.capacityKg:
            routes.append(_build_route(p, d, current, seq))
            seq, load = [], 0.0
            current = vehicles.pop(0) if vehicles else None
        if current is None or dem > current.capacityKg:
            continue
        seq.append(i)
        load += dem
    if current and seq:
        routes.append(_build_route(p, d, current, seq))
    return routes


def solve(p: Problem, time_limit_s: int = 10) -> Solution:
    d = distance_matrix(p)
    n, vcount = len(p.stops), len(p.vehicles)
    scale = 100  # OR-Tools works with integers: distances in 10 m units, demand in kg
    dist_int = [[int(round(x * scale)) for x in row] for row in d]
    travel_min = [[int(round(x / p.averageSpeedKmh * 60)) for x in row] for row in d]

    manager = pywrapcp.RoutingIndexManager(n + 1, vcount, 0)
    routing = pywrapcp.RoutingModel(manager)

    def dist_cb(i, j):
        return dist_int[manager.IndexToNode(i)][manager.IndexToNode(j)]

    transit = routing.RegisterTransitCallback(dist_cb)
    routing.SetArcCostEvaluatorOfAllVehicles(transit)

    def demand_cb(i):
        node = manager.IndexToNode(i)
        return 0 if node == 0 else int(math.ceil(p.stops[node - 1].demandKg))

    demand = routing.RegisterUnaryTransitCallback(demand_cb)
    routing.AddDimensionWithVehicleCapacity(demand, 0, [int(v.capacityKg) for v in p.vehicles], True, "Capacity")

    def time_cb(i, j):
        a, b = manager.IndexToNode(i), manager.IndexToNode(j)
        service = 0 if a == 0 else p.stops[a - 1].serviceMinutes
        return travel_min[a][b] + service

    time_idx = routing.RegisterTransitCallback(time_cb)
    routing.AddDimension(time_idx, 240, p.maxRouteMinutes, True, "Time")
    time_dim = routing.GetDimensionOrDie("Time")
    notes = []
    for node in range(1, n + 1):
        s = p.stops[node - 1]
        idx = manager.NodeToIndex(node)
        start = s.timeWindowStart if s.timeWindowStart is not None else 0
        end = s.timeWindowEnd if s.timeWindowEnd is not None else p.maxRouteMinutes
        time_dim.CumulVar(idx).SetMin(max(0, start))
        # soft upper bound: lateness is allowed but heavily penalised (urgent stops even more)
        time_dim.SetCumulVarSoftUpperBound(idx, max(start, end), 50 * (1 + s.priority))
        # allow dropping a stop only at a very high penalty (capacity shortfall)
        routing.AddDisjunction([idx], 10_000_000 * (1 + s.priority))

    params = pywrapcp.DefaultRoutingSearchParameters()
    params.first_solution_strategy = routing_enums_pb2.FirstSolutionStrategy.PATH_CHEAPEST_ARC
    params.local_search_metaheuristic = routing_enums_pb2.LocalSearchMetaheuristic.GUIDED_LOCAL_SEARCH
    params.time_limit.seconds = time_limit_s
    assignment = routing.SolveWithParameters(params)
    if assignment is None:
        raise RuntimeError("No feasible routing solution found")

    routes, served = [], set()
    for v in range(vcount):
        idx, seq = routing.Start(v), []
        while not routing.IsEnd(idx):
            node = manager.IndexToNode(idx)
            if node != 0:
                seq.append(node)
            idx = assignment.Value(routing.NextVar(idx))
        if seq:
            routes.append(_build_route(p, d, p.vehicles[v], seq))
            served.update(seq)
    unassigned = [p.stops[i - 1].id for i in range(1, n + 1) if i not in served]
    if unassigned:
        notes.append(f"{len(unassigned)} stop(s) could not be served with the available fleet capacity")
    baseline = manual_plan(p, d)
    km = sum(r.distanceKm for r in routes)
    return Solution(
        engine="Google OR-Tools CVRPTW (guided local search, ai-service)",
        routes=routes, unassignedStopIds=unassigned,
        originalDistanceKm=round(sum(r.distanceKm for r in baseline), 2), optimizedDistanceKm=round(km, 2),
        estimatedDurationMinutes=max([r.durationMinutes for r in routes], default=0),
        estimatedFuelLiters=round(sum(r.fuelLiters for r in routes), 2), estimatedCo2Kg=round(sum(r.co2Kg for r in routes), 2),
        originalCo2Kg=round(sum(r.co2Kg for r in baseline), 2),
        timeWindowViolations=sum(1 for r in routes for s in r.stops if not s.timeWindowRespected), notes=notes)
