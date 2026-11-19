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

/** Generated from the SmartFlow domain specification — table non_conformities. */
@Entity
@Table(name = "non_conformities")
@Getter
@Setter
public class NonConformity extends TenantEntity {

    @Size(max = 50)
    @Column(name = "reference")
    private String reference;

    @NotBlank
    @Size(max = 255)
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @NotBlank
    @Size(max = 50)
    @Column(name = "type", nullable = false)
    private String type;

    @Size(max = 100)
    @Column(name = "category")
    private String category;

    @Size(max = 20)
    @OneOf({"MINOR", "MAJOR", "CRITICAL"})
    @Column(name = "severity")
    private String severity;

    @Size(max = 50)
    @Column(name = "status")
    private String status;

    @Size(max = 50)
    @Column(name = "source")
    private String source;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "production_line_id")
    private UUID productionLineId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "inspection_id")
    private UUID inspectionId;

    @Column(name = "audit_id")
    private UUID auditId;

    @Column(name = "detected_at")
    private Instant detectedAt;

    @Size(max = 255)
    @Column(name = "reported_by_name")
    private String reportedByName;

    @Column(name = "reported_by_user_id")
    private UUID reportedByUserId;

    @Column(name = "assignee_user_id")
    private UUID assigneeUserId;

    @Size(max = 255)
    @Column(name = "assignee_name")
    private String assigneeName;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "root_cause")
    private String rootCause;

    @Size(max = 30)
    @Column(name = "root_cause_method")
    private String rootCauseMethod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "five_whys")
    private List<String> fiveWhys;

    @Column(name = "immediate_action")
    private String immediateAction;

    @Column(name = "quantity_affected")
    private Integer quantityAffected;

    @Column(name = "cost_estimate")
    private BigDecimal costEstimate;

    @Column(name = "closed_at")
    private Instant closedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields")
    private Map<String, Object> customFields;
}
