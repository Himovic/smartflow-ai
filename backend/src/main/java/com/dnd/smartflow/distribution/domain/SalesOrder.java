package com.dnd.smartflow.distribution.domain;

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

/** Generated from the SmartFlow domain specification — table sales_orders. */
@Entity
@Table(name = "sales_orders")
@Getter
@Setter
public class SalesOrder extends TenantEntity {

    @Size(max = 50)
    @Column(name = "order_number")
    private String orderNumber;

    @NotNull
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "requested_date")
    private LocalDate requestedDate;

    @Size(max = 30)
    @OneOf({"DRAFT", "CONFIRMED", "PREPARING", "SHIPPED", "DELIVERED", "CANCELLED"})
    @Column(name = "status")
    private String status;

    @Size(max = 20)
    @OneOf({"LOW", "NORMAL", "HIGH", "URGENT"})
    @Column(name = "priority")
    private String priority;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(name = "total_weight_kg")
    private Double totalWeightKg;

    @Size(max = 500)
    @Column(name = "notes")
    private String notes;

    @Size(max = 100)
    @Column(name = "external_ref")
    private String externalRef;
}
