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

/** Generated from the SmartFlow domain specification — table cleaning_records. */
@Entity
@Table(name = "cleaning_records")
@Getter
@Setter
public class CleaningRecord extends TenantEntity {

    @NotNull
    @Column(name = "schedule_id", nullable = false)
    private UUID scheduleId;

    @NotNull
    @Column(name = "done_at", nullable = false)
    private Instant doneAt;

    @Size(max = 255)
    @Column(name = "done_by")
    private String doneBy;

    @Column(name = "verified")
    private Boolean verified;

    @Size(max = 255)
    @Column(name = "verified_by")
    private String verifiedBy;

    @Size(max = 500)
    @Column(name = "notes")
    private String notes;
}
