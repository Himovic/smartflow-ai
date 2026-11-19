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

/** Generated from the SmartFlow domain specification — table inspections. */
@Entity
@Table(name = "inspections")
@Getter
@Setter
public class Inspection extends TenantEntity {

    @Size(max = 50)
    @Column(name = "reference")
    private String reference;

    @Column(name = "template_id")
    private UUID templateId;

    @NotBlank
    @Size(max = 255)
    @Column(name = "title", nullable = false)
    private String title;

    @NotBlank
    @Size(max = 30)
    @OneOf({"QUALITY", "HYGIENE", "SAFETY", "ENVIRONMENT"})
    @Column(name = "domain", nullable = false)
    private String domain;

    @Size(max = 50)
    @Column(name = "inspection_type")
    private String inspectionType;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "production_line_id")
    private UUID productionLineId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "inspector_user_id")
    private UUID inspectorUserId;

    @Size(max = 255)
    @Column(name = "inspector_name")
    private String inspectorName;

    @Size(max = 30)
    @OneOf({"SCHEDULED", "IN_PROGRESS", "COMPLETED", "CANCELLED"})
    @Column(name = "status")
    private String status;

    @Column(name = "score")
    private Double score;

    @Size(max = 30)
    @Column(name = "result")
    private String result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers")
    private List<Map<String, Object>> answers;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "humidity")
    private Double humidity;

    @Column(name = "signature")
    private String signature;

    @Column(name = "notes")
    private String notes;

    @Column(name = "generated_nc_count")
    private Integer generatedNcCount;
}
