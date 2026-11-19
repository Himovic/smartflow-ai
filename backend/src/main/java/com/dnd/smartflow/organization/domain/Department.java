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

/** Generated from the SmartFlow domain specification — table departments. */
@Entity
@Table(name = "departments")
@Getter
@Setter
public class Department extends TenantEntity {

    @NotNull
    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @NotBlank
    @Size(max = 50)
    @Column(name = "code", nullable = false)
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 50)
    @Column(name = "domain")
    private String domain;

    @Size(max = 255)
    @Column(name = "manager_name")
    private String managerName;

    @Column(name = "headcount")
    private Integer headcount;
}
