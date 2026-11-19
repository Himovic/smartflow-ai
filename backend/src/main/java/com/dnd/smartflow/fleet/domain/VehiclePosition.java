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

/** Generated from the SmartFlow domain specification — table vehicle_positions. */
@Entity
@Table(name = "vehicle_positions")
@Getter
@Setter
public class VehiclePosition extends TenantEntity {

    @NotNull
    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @NotNull
    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @NotNull
    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    @NotNull
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Size(max = 30)
    @Column(name = "source")
    private String source;
}
