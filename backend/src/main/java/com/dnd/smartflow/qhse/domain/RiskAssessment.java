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

/** Generated from the SmartFlow domain specification — table risk_assessments. */
@Entity
@Table(name = "risk_assessments")
@Getter
@Setter
public class RiskAssessment extends TenantEntity {

    @Size(max = 50)
    @Column(name = "reference")
    private String reference;

    @NotBlank
    @Size(max = 255)
    @Column(name = "title", nullable = false)
    private String title;

    @Size(max = 30)
    @OneOf({"GENERAL", "JSA", "ENVIRONMENTAL", "PROCESS"})
    @Column(name = "assessment_type")
    private String assessmentType;

    @Size(max = 255)
    @Column(name = "activity")
    private String activity;

    @Column(name = "hazard")
    private String hazard;

    @Size(max = 50)
    @Column(name = "hazard_category")
    private String hazardCategory;

    @Size(max = 100)
    @Column(name = "risk_category")
    private String riskCategory;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @NotNull
    @Column(name = "probability", nullable = false)
    private Integer probability;

    @NotNull
    @Column(name = "severity", nullable = false)
    private Integer severity;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Size(max = 20)
    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "existing_controls")
    private String existingControls;

    @Column(name = "preventive_controls")
    private String preventiveControls;

    @Column(name = "residual_probability")
    private Integer residualProbability;

    @Column(name = "residual_severity")
    private Integer residualSeverity;

    @Column(name = "residual_score")
    private Integer residualScore;

    @Size(max = 20)
    @Column(name = "residual_level")
    private String residualLevel;

    @Size(max = 255)
    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Size(max = 30)
    @OneOf({"DRAFT", "ACTIVE", "UNDER_REVIEW", "ARCHIVED"})
    @Column(name = "status")
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "jsa_steps")
    private List<Map<String, Object>> jsaSteps;
}
