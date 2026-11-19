package com.dnd.smartflow.inventory.domain;

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

/** Generated from the SmartFlow domain specification — table stock_movements. */
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
public class StockMovement extends TenantEntity {

    @NotBlank
    @Size(max = 30)
    @OneOf({"RECEIPT", "PRODUCTION", "SHIPMENT", "TRANSFER", "ADJUSTMENT", "RETURN", "RECALL"})
    @Column(name = "type", nullable = false)
    private String type;

    @NotNull
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "from_warehouse_id")
    private UUID fromWarehouseId;

    @Column(name = "to_warehouse_id")
    private UUID toWarehouseId;

    @NotNull
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Size(max = 50)
    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Size(max = 500)
    @Column(name = "notes")
    private String notes;
}
