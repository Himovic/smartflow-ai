package com.dnd.smartflow.ai.routing;

import java.util.List;

/** Vehicle-routing problem and solution exchanged with the optimization engines (Java fallback and ai-service). */
public final class RoutingModels {
    private RoutingModels() {
    }

    public record Location(double latitude, double longitude) {
    }

    /** timeWindowStart/End and serviceMinutes are expressed in minutes after the depot opening. */
    public record Stop(String id, String label, double latitude, double longitude, double demandKg, int priority,
                       Integer timeWindowStart, Integer timeWindowEnd, int serviceMinutes) {
    }

    public record VehicleSpec(String id, String label, double capacityKg, double consumptionPer100km, double co2FactorKgPerLiter) {
    }

    public record Problem(Location depot, List<Stop> stops, List<VehicleSpec> vehicles, double averageSpeedKmh, double roadFactor,
                          int maxRouteMinutes) {
    }

    public record RouteStop(String stopId, String label, int sequence, double latitude, double longitude, double distanceFromPreviousKm,
                            double arrivalMinute, boolean timeWindowRespected) {
    }

    public record VehicleRoute(String vehicleId, String label, List<RouteStop> stops, double distanceKm, double durationMinutes,
                               double loadKg, double capacityKg, double fuelLiters, double co2Kg) {
    }

    public record Solution(String engine, List<VehicleRoute> routes, List<String> unassignedStopIds, double originalDistanceKm,
                           double optimizedDistanceKm, double estimatedDurationMinutes, double estimatedFuelLiters, double estimatedCo2Kg,
                           double originalCo2Kg, int timeWindowViolations, List<String> notes) {
    }
}
