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

/** Generated from the SmartFlow domain specification — table incidents. */
@Entity
@Table(name = "incidents")
@Getter
@Setter
public class Incident extends TenantEntity {

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
    @Size(max = 30)
    @OneOf({"SAFETY", "ENVIRONMENT", "HYGIENE", "SECURITY"})
    @Column(name = "domain", nullable = false)
    private String domain;

    @NotBlank
    @Size(max = 50)
    @Column(name = "type", nullable = false)
    private String type;

    @Size(max = 50)
    @Column(name = "env_category")
    private String envCategory;

    @Column(name = "severity")
    private Integer severity;

    @Column(name = "probability")
    private Integer probability;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Size(max = 50)
    @Column(name = "status")
    private String status;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Size(max = 255)
    @Column(name = "location_detail")
    private String locationDetail;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Size(max = 255)
    @Column(name = "reported_by_name")
    private String reportedByName;

    @Column(name = "reported_by_user_id")
    private UUID reportedByUserId;

    @Column(name = "injured_persons")
    private Integer injuredPersons;

    @Column(name = "lost_time_days")
    private Integer lostTimeDays;

    @Column(name = "immediate_actions")
    private String immediateActions;

    @Column(name = "root_cause")
    private String rootCause;

    @Column(name = "investigation_findings")
    private String investigationFindings;

    @Column(name = "impact_assessment")
    private String impactAssessment;

    @Size(max = 255)
    @Column(name = "investigator_name")
    private String investigatorName;

    @Column(name = "investigator_user_id")
    private UUID investigatorUserId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "closed_at")
    private Instant closedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields")
    private Map<String, Object> customFields;
}
