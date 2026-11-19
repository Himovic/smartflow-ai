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

/** Generated from the SmartFlow domain specification — table cleaning_schedules. */
@Entity
@Table(name = "cleaning_schedules")
@Getter
@Setter
public class CleaningSchedule extends TenantEntity {

    @NotBlank
    @Size(max = 255)
    @Column(name = "area", nullable = false)
    private String area;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Size(max = 30)
    @OneOf({"DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY", "QUARTERLY"})
    @Column(name = "frequency")
    private String frequency;

    @Column(name = "method")
    private String method;

    @Size(max = 500)
    @Column(name = "products_used")
    private String productsUsed;

    @Size(max = 255)
    @Column(name = "responsible_name")
    private String responsibleName;

    @Column(name = "last_done_at")
    private Instant lastDoneAt;

    @Column(name = "next_due_at")
    private Instant nextDueAt;

    @Column(name = "active")
    private Boolean active;
}
