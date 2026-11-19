package com.dnd.smartflow.catalog.domain;

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

/** Generated from the SmartFlow domain specification — table suppliers. */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
public class Supplier extends TenantEntity {

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

    @Size(max = 100)
    @Column(name = "country")
    private String country;

    @Size(max = 100)
    @Column(name = "city")
    private String city;

    @Size(max = 255)
    @Column(name = "contact_name")
    private String contactName;

    @Size(max = 255)
    @Column(name = "email")
    private String email;

    @Size(max = 50)
    @Column(name = "phone")
    private String phone;

    @Size(max = 50)
    @OneOf({"QUALIFIED", "CONDITIONAL", "PENDING", "SUSPENDED"})
    @Column(name = "status")
    private String status;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Column(name = "on_time_rate")
    private Double onTimeRate;

    @Size(max = 500)
    @Column(name = "certifications")
    private String certifications;

    @Column(name = "last_audit_date")
    private LocalDate lastAuditDate;

    @Size(max = 100)
    @Column(name = "external_ref")
    private String externalRef;
}
