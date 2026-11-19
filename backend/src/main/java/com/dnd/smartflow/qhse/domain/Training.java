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

/** Generated from the SmartFlow domain specification — table trainings. */
@Entity
@Table(name = "trainings")
@Getter
@Setter
public class Training extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "code", nullable = false)
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(name = "title", nullable = false)
    private String title;

    @Size(max = 30)
    @OneOf({"SAFETY", "HYGIENE", "QUALITY", "ENVIRONMENT", "TECHNICAL", "MANAGEMENT"})
    @Column(name = "category")
    private String category;

    @Column(name = "mandatory")
    private Boolean mandatory;

    @Column(name = "validity_months")
    private Integer validityMonths;

    @Column(name = "duration_hours")
    private Double durationHours;

    @Size(max = 255)
    @Column(name = "provider")
    private String provider;

    @Column(name = "description")
    private String description;
}
