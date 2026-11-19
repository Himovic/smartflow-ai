package com.dnd.smartflow.distribution.domain;

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

/** Generated from the SmartFlow domain specification — table deliveries. */
@Entity
@Table(name = "deliveries")
@Getter
@Setter
public class Delivery extends TenantEntity {

    @Size(max = 50)
    @Column(name = "delivery_number")
    private String deliveryNumber;

    @Column(name = "order_id")
    private UUID orderId;

    @NotNull
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "route_plan_id")
    private UUID routePlanId;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Size(max = 10)
    @Column(name = "time_window_start")
    private String timeWindowStart;

    @Size(max = 10)
    @Column(name = "time_window_end")
    private String timeWindowEnd;

    @Size(max = 30)
    @OneOf({"PLANNED", "IN_TRANSIT", "DELIVERED", "DELAYED", "FAILED", "CANCELLED"})
    @Column(name = "status")
    private String status;

    @Column(name = "departed_at")
    private Instant departedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "fuel_liters")
    private Double fuelLiters;

    @Column(name = "co2_kg")
    private Double co2Kg;

    @Column(name = "stop_sequence")
    private Integer stopSequence;

    @Column(name = "delay_minutes")
    private Integer delayMinutes;

    @Size(max = 500)
    @Column(name = "proof_note")
    private String proofNote;
}
