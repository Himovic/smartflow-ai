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

/** Generated from the SmartFlow domain specification — table ppe_items. */
@Entity
@Table(name = "ppe_items")
@Getter
@Setter
public class PpeItem extends TenantEntity {

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
    @Column(name = "standard_ref")
    private String standardRef;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(name = "min_stock")
    private Integer minStock;

    @Column(name = "replacement_months")
    private Integer replacementMonths;
}
