package com.dnd.smartflow.integration.domain;

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

/** Generated from the SmartFlow domain specification — table sync_jobs. */
@Entity
@Table(name = "sync_jobs")
@Getter
@Setter
public class SyncJob extends TenantEntity {

    @NotNull
    @Column(name = "connector_id", nullable = false)
    private UUID connectorId;

    @Size(max = 30)
    @Column(name = "trigger_type")
    private String triggerType;

    @Size(max = 30)
    @Column(name = "status")
    private String status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "records_read")
    private Integer recordsRead;

    @Column(name = "records_created")
    private Integer recordsCreated;

    @Column(name = "records_updated")
    private Integer recordsUpdated;

    @Column(name = "records_skipped")
    private Integer recordsSkipped;

    @Column(name = "records_failed")
    private Integer recordsFailed;

    @Size(max = 100)
    @Column(name = "cursor_value")
    private String cursorValue;

    @Column(name = "message")
    private String message;
}
