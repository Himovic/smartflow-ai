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

/** Generated from the SmartFlow domain specification — table notification_rules. */
@Entity
@Table(name = "notification_rules")
@Getter
@Setter
public class NotificationRule extends TenantEntity {

    @NotBlank
    @Size(max = 100)
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "roles")
    private List<String> roles;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "channels")
    private List<String> channels;

    @Size(max = 20)
    @Column(name = "min_severity")
    private String minSeverity;

    @Column(name = "active")
    private Boolean active;

    @Size(max = 500)
    @Column(name = "description")
    private String description;
}
