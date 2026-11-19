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

/** Generated from the SmartFlow domain specification — table sync_records. */
@Entity
@Table(name = "sync_records")
@Getter
@Setter
public class SyncRecord extends TenantEntity {

    @NotNull
    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @NotNull
    @Column(name = "connector_id", nullable = false)
    private UUID connectorId;

    @NotBlank
    @Size(max = 50)
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @NotBlank
    @Size(max = 100)
    @Column(name = "external_id", nullable = false)
    private String externalId;

    @NotBlank
    @Size(max = 30)
    @Column(name = "status", nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private Map<String, Object> payload;

    @Column(name = "error")
    private String error;

    @Column(name = "attempts")
    private Integer attempts;

    @Column(name = "local_id")
    private UUID localId;
}
