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

/** Generated from the SmartFlow domain specification — table custom_field_definitions. */
@Entity
@Table(name = "custom_field_definitions")
@Getter
@Setter
public class CustomFieldDefinition extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @NotBlank
    @Size(max = 50)
    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @NotBlank
    @Size(max = 255)
    @Column(name = "label", nullable = false)
    private String label;

    @NotBlank
    @Size(max = 30)
    @OneOf({"TEXT", "NUMBER", "DATE", "BOOLEAN", "SELECT"})
    @Column(name = "field_type", nullable = false)
    private String fieldType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options")
    private List<String> options;

    @Column(name = "required")
    private Boolean required;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
