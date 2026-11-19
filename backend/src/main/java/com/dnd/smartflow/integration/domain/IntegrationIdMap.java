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

/** Generated from the SmartFlow domain specification — table integration_id_map. */
@Entity
@Table(name = "integration_id_map")
@Getter
@Setter
public class IntegrationIdMap extends TenantEntity {

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

    @NotNull
    @Column(name = "local_id", nullable = false)
    private UUID localId;

    @Size(max = 100)
    @Column(name = "payload_hash")
    private String payloadHash;

    @Column(name = "synced_at")
    private Instant syncedAt;
}
