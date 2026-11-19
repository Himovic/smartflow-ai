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

/** Generated from the SmartFlow domain specification — table products. */
@Entity
@Table(name = "products")
@Getter
@Setter
public class Product extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "sku", nullable = false)
    private String sku;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 100)
    @Column(name = "category")
    private String category;

    @Size(max = 100)
    @Column(name = "brand")
    private String brand;

    @Size(max = 20)
    @Column(name = "unit")
    private String unit;

    @Column(name = "unit_weight_kg")
    private Double unitWeightKg;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "co2_per_unit_kg")
    private Double co2PerUnitKg;

    @Column(name = "min_stock")
    private Integer minStock;

    @Column(name = "reorder_point")
    private Integer reorderPoint;

    @Size(max = 50)
    @Column(name = "barcode")
    private String barcode;

    @Column(name = "active")
    private Boolean active;

    @Size(max = 100)
    @Column(name = "external_ref")
    private String externalRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_fields")
    private Map<String, Object> customFields;
}
