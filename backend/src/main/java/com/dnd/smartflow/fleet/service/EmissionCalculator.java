package com.dnd.smartflow.fleet.service;

/**
 * Transport fuel and CO2 estimation. Default tank-to-wheel factors (kg CO2 per litre): diesel 2.68,
 * gasoline 2.31, LPG 1.61. Vehicle-specific factors configured on the vehicle take precedence.
 * Results are estimates derived from distance × consumption, not measurements.
 */
public final class EmissionCalculator {
    private EmissionCalculator() {
    }

    public static double defaultFactor(String fuelType) {
        if (fuelType == null) return 2.68;
        return switch (fuelType) {
            case "GASOLINE" -> 2.31;
            case "LPG" -> 1.61;
            case "ELECTRIC" -> 0.0;
            default -> 2.68;
        };
    }

    public static double fuelLiters(double distanceKm, Double consumptionPer100km) {
        double c = consumptionPer100km == null || consumptionPer100km <= 0 ? 28.0 : consumptionPer100km;
        return round(distanceKm * c / 100.0);
    }

    public static double co2Kg(double liters, Double factor, String fuelType) {
        double f = factor == null || factor <= 0 ? defaultFactor(fuelType) : factor;
        return round(liters * f);
    }

    public static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
