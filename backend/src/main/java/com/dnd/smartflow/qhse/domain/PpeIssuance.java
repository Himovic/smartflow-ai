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

/** Generated from the SmartFlow domain specification — table ppe_issuances. */
@Entity
@Table(name = "ppe_issuances")
@Getter
@Setter
public class PpeIssuance extends TenantEntity {

    @NotNull
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @NotNull
    @Column(name = "ppe_item_id", nullable = false)
    private UUID ppeItemId;

    @NotNull
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "replacement_date")
    private LocalDate replacementDate;

    @Size(max = 255)
    @Column(name = "issued_by")
    private String issuedBy;
}
