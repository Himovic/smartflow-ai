package com.dnd.smartflow.workflow.domain;

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

/** Generated from the SmartFlow domain specification — table workflow_events. */
@Entity
@Table(name = "workflow_events")
@Getter
@Setter
public class WorkflowEvent extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @NotNull
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Size(max = 50)
    @Column(name = "from_status")
    private String fromStatus;

    @NotBlank
    @Size(max = 50)
    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Size(max = 50)
    @Column(name = "transition")
    private String transition;

    @Column(name = "comment")
    private String comment;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Size(max = 255)
    @Column(name = "actor_name")
    private String actorName;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
