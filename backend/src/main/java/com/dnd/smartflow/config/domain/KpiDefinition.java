package com.dnd.smartflow.config.domain;

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

/** Generated from the SmartFlow domain specification — table kpi_definitions. */
@Entity
@Table(name = "kpi_definitions")
@Getter
@Setter
public class KpiDefinition extends TenantEntity {

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
    @Column(name = "domain", nullable = false)
    private String domain;

    @Size(max = 20)
    @Column(name = "unit")
    private String unit;

    @Column(name = "target")
    private Double target;

    @Size(max = 30)
    @OneOf({"HIGHER_IS_BETTER", "LOWER_IS_BETTER"})
    @Column(name = "direction")
    private String direction;

    @NotBlank
    @Size(max = 100)
    @Column(name = "calculator", nullable = false)
    private String calculator;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "description")
    private String description;
}
