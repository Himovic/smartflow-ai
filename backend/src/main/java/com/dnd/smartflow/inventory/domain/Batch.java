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

/** Generated from the SmartFlow domain specification — table batches. */
@Entity
@Table(name = "batches")
@Getter
@Setter
public class Batch extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "batch_number", nullable = false)
    private String batchNumber;

    @NotNull
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "production_line_id")
    private UUID productionLineId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "production_date")
    private LocalDate productionDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "quantity_produced")
    private Integer quantityProduced;

    @Size(max = 30)
    @OneOf({"RELEASED", "QUARANTINE", "BLOCKED", "RECALLED", "EXPIRED", "CONSUMED"})
    @Column(name = "status")
    private String status;

    @Size(max = 30)
    @OneOf({"PENDING", "APPROVED", "REJECTED"})
    @Column(name = "quality_status")
    private String qualityStatus;

    @Size(max = 500)
    @Column(name = "raw_material_lots")
    private String rawMaterialLots;

    @Size(max = 100)
    @Column(name = "external_ref")
    private String externalRef;
}
