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

/** Generated from the SmartFlow domain specification — table maintenance_work_orders. */
@Entity
@Table(name = "maintenance_work_orders")
@Getter
@Setter
public class MaintenanceWorkOrder extends TenantEntity {

    @Size(max = 50)
    @Column(name = "reference")
    private String reference;

    @NotNull
    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @NotBlank
    @Size(max = 50)
    @OneOf({"PREVENTIVE", "CORRECTIVE", "PREDICTIVE", "INSPECTION"})
    @Column(name = "type", nullable = false)
    private String type;

    @Size(max = 50)
    @OneOf({"PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELLED"})
    @Column(name = "status")
    private String status;

    @Size(max = 20)
    @OneOf({"LOW", "MEDIUM", "HIGH", "CRITICAL"})
    @Column(name = "priority")
    private String priority;

    @Column(name = "description")
    private String description;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Column(name = "completed_date")
    private LocalDate completedDate;

    @Column(name = "downtime_hours")
    private Double downtimeHours;

    @Column(name = "cost")
    private BigDecimal cost;

    @Size(max = 255)
    @Column(name = "technician")
    private String technician;

    @Size(max = 50)
    @Column(name = "source")
    private String source;

    @Size(max = 100)
    @Column(name = "external_ref")
    private String externalRef;
}
