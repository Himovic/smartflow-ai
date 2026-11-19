package com.dnd.smartflow.assets.domain;

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

/** Generated from the SmartFlow domain specification — table equipment. */
@Entity
@Table(name = "equipment")
@Getter
@Setter
public class Equipment extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "code", nullable = false)
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 100)
    @Column(name = "category")
    private String category;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "production_line_id")
    private UUID productionLineId;

    @Size(max = 100)
    @Column(name = "manufacturer")
    private String manufacturer;

    @Size(max = 100)
    @Column(name = "model")
    private String model;

    @Size(max = 100)
    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "commissioning_date")
    private LocalDate commissioningDate;

    @Size(max = 50)
    @OneOf({"OPERATIONAL", "MAINTENANCE", "DOWN", "RETIRED"})
    @Column(name = "status")
    private String status;

    @Size(max = 20)
    @OneOf({"LOW", "MEDIUM", "HIGH", "CRITICAL"})
    @Column(name = "criticality")
    private String criticality;

    @Column(name = "last_maintenance_date")
    private LocalDate lastMaintenanceDate;

    @Column(name = "next_maintenance_date")
    private LocalDate nextMaintenanceDate;

    @Column(name = "running_hours")
    private Integer runningHours;

    @Column(name = "failure_count")
    private Integer failureCount;

    @Column(name = "mtbf_hours")
    private Double mtbfHours;

    @Size(max = 100)
    @Column(name = "external_ref")
    private String externalRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields")
    private Map<String, Object> customFields;
}
