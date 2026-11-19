package com.dnd.smartflow.qhse.domain;

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

/** Generated from the SmartFlow domain specification — table environmental_readings. */
@Entity
@Table(name = "environmental_readings")
@Getter
@Setter
public class EnvironmentalReading extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "reading_type", nullable = false)
    private String readingType;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Size(max = 255)
    @Column(name = "location")
    private String location;

    @NotNull
    @Column(name = "reading_value", nullable = false)
    private Double readingValue;

    @Size(max = 20)
    @Column(name = "unit")
    private String unit;

    @Column(name = "min_threshold")
    private Double minThreshold;

    @Column(name = "max_threshold")
    private Double maxThreshold;

    @Column(name = "within_limits")
    private Boolean withinLimits;

    @NotNull
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Size(max = 30)
    @OneOf({"MANUAL", "IOT", "IMPORT", "INSPECTION"})
    @Column(name = "source")
    private String source;

    @Size(max = 100)
    @Column(name = "sensor_id")
    private String sensorId;
}
