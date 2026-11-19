package com.dnd.smartflow.notification.domain;

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

/** Generated from the SmartFlow domain specification — table notifications. */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification extends TenantEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Size(max = 50)
    @Column(name = "target_role")
    private String targetRole;

    @NotBlank
    @Size(max = 255)
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message")
    private String message;

    @Size(max = 20)
    @Column(name = "severity")
    private String severity;

    @Size(max = 50)
    @Column(name = "category")
    private String category;

    @Size(max = 50)
    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "read_at")
    private Instant readAt;
}
