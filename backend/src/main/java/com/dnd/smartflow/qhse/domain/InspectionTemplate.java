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

/** Generated from the SmartFlow domain specification — table inspection_templates. */
@Entity
@Table(name = "inspection_templates")
@Getter
@Setter
public class InspectionTemplate extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "code", nullable = false)
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Size(max = 30)
    @OneOf({"QUALITY", "HYGIENE", "SAFETY", "ENVIRONMENT", "AUDIT"})
    @Column(name = "domain", nullable = false)
    private String domain;

    @Size(max = 50)
    @Column(name = "inspection_type")
    private String inspectionType;

    @Size(max = 50)
    @Column(name = "industry_profile")
    private String industryProfile;

    @Column(name = "template_version")
    private Integer templateVersion;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "description")
    private String description;

    @Column(name = "passing_score")
    private Double passingScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "questions")
    private List<Map<String, Object>> questions;
}
