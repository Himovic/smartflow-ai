package com.dnd.smartflow.organization.domain;

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

/** Generated from the SmartFlow domain specification — table production_lines. */
@Entity
@Table(name = "production_lines")
@Getter
@Setter
public class ProductionLine extends TenantEntity {

    @NotNull
    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "department_id")
    private UUID departmentId;

    @NotBlank
    @Size(max = 50)
    @Column(name = "code", nullable = false)
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 100)
    @Column(name = "product_family")
    private String productFamily;

    @Column(name = "capacity_per_hour")
    private Integer capacityPerHour;

    @Size(max = 50)
    @OneOf({"RUNNING", "STOPPED", "MAINTENANCE", "CHANGEOVER"})
    @Column(name = "status")
    private String status;

    @Column(name = "oee")
    private Double oee;
}
