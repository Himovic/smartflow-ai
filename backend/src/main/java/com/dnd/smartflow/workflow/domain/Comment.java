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

/** Generated from the SmartFlow domain specification — table comments. */
@Entity
@Table(name = "comments")
@Getter
@Setter
public class Comment extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @NotNull
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @NotBlank
    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "author_user_id")
    private UUID authorUserId;

    @Size(max = 255)
    @Column(name = "author_name")
    private String authorName;
}
