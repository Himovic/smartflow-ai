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

/** Generated from the SmartFlow domain specification — table route_plans. */
@Entity
@Table(name = "route_plans")
@Getter
@Setter
public class RoutePlan extends TenantEntity {

    @Size(max = 50)
    @Column(name = "code")
    private String code;

    @NotNull
    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @NotNull
    @Column(name = "depot_warehouse_id", nullable = false)
    private UUID depotWarehouseId;

    @Size(max = 30)
    @OneOf({"DRAFT", "OPTIMIZED", "DISPATCHED", "COMPLETED"})
    @Column(name = "status")
    private String status;

    @Size(max = 100)
    @Column(name = "algorithm")
    private String algorithm;

    @Column(name = "original_distance_km")
    private Double originalDistanceKm;

    @Column(name = "optimized_distance_km")
    private Double optimizedDistanceKm;

    @Column(name = "estimated_duration_min")
    private Double estimatedDurationMin;

    @Column(name = "estimated_fuel_liters")
    private Double estimatedFuelLiters;

    @Column(name = "estimated_co2_kg")
    private Double estimatedCo2Kg;

    @Column(name = "vehicles_used")
    private Integer vehiclesUsed;

    @Column(name = "stops_count")
    private Integer stopsCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result")
    private Map<String, Object> result;
}
