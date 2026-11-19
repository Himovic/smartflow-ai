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

/** Generated from the SmartFlow domain specification — table audits. */
@Entity
@Table(name = "audits")
@Getter
@Setter
public class Audit extends TenantEntity {

    @Size(max = 50)
    @Column(name = "reference")
    private String reference;

    @NotBlank
    @Size(max = 255)
    @Column(name = "title", nullable = false)
    private String title;

    @NotBlank
    @Size(max = 30)
    @OneOf({"INTERNAL", "EXTERNAL", "SUPPLIER", "CERTIFICATION"})
    @Column(name = "audit_type", nullable = false)
    private String auditType;

    @Size(max = 50)
    @Column(name = "standard")
    private String standard;

    @Column(name = "scope")
    private String scope;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Size(max = 255)
    @Column(name = "lead_auditor")
    private String leadAuditor;

    @Size(max = 500)
    @Column(name = "audit_team")
    private String auditTeam;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Size(max = 30)
    @OneOf({"PLANNED", "IN_PROGRESS", "REPORTING", "COMPLETED", "CANCELLED"})
    @Column(name = "status")
    private String status;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "score")
    private Double score;

    @Column(name = "conclusion")
    private String conclusion;
}
