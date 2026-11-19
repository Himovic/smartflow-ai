package com.dnd.smartflow.fleet.domain;

import com.dnd.smartflow.common.persistence.TenantEntity;
import com.dnd.smartflow.common.validation.OneOf;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Generated from the SmartFlow domain specification — table vehicles. */
@Entity
@Table(name = "vehicles")
@Getter
@Setter
public class Vehicle extends TenantEntity {

    @NotBlank
    @Size(max = 30)
    @Column(name = "plate_number", nullable = false)
    private String plateNumber;

    @Size(max = 50)
    @Column(name = "code")
    private String code;

    @Size(max = 50)
    @OneOf({"TRUCK", "VAN", "REFRIGERATED_TRUCK", "SEMI_TRAILER", "PICKUP"})
    @Column(name = "type")
    private String type;

    @Size(max = 100)
    @Column(name = "brand")
    private String brand;

    @Size(max = 100)
    @Column(name = "model")
    private String model;

    @Column(name = "capacity_kg")
    private Double capacityKg;

    @Column(name = "capacity_pallets")
    private Integer capacityPallets;

    @Size(max = 30)
    @OneOf({"DIESEL", "GASOLINE", "LPG", "ELECTRIC"})
    @Column(name = "fuel_type")
    private String fuelType;

    @Column(name = "fuel_consumption_per100km")
    private Double fuelConsumptionPer100km;

    @Column(name = "co2_factor_kg_per_liter")
    private Double co2FactorKgPerLiter;

    @Size(max = 30)
    @OneOf({"AVAILABLE", "ON_ROUTE", "MAINTENANCE", "OUT_OF_SERVICE"})
    @Column(name = "status")
    private String status;

    @Column(name = "home_site_id")
    private UUID homeSiteId;

    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    @Column(name = "last_gps_at")
    private Instant lastGpsAt;

    @Column(name = "odometer_km")
    private Integer odometerKm;

    @Column(name = "next_service_km")
    private Integer nextServiceKm;

    @Column(name = "last_hygiene_check")
    private LocalDate lastHygieneCheck;

    @Column(name = "refrigerated")
    private Boolean refrigerated;

    @Size(max = 100)
    @Column(name = "external_ref")
    private String externalRef;
}
