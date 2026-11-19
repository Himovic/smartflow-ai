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

/** Generated from the SmartFlow domain specification — table fuel_logs. */
@Entity
@Table(name = "fuel_logs")
@Getter
@Setter
public class FuelLog extends TenantEntity {

    @NotNull
    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "driver_id")
    private UUID driverId;

    @NotNull
    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @NotNull
    @Column(name = "liters", nullable = false)
    private Double liters;

    @Column(name = "cost")
    private BigDecimal cost;

    @Column(name = "odometer_km")
    private Integer odometerKm;

    @Size(max = 255)
    @Column(name = "station")
    private String station;
}
