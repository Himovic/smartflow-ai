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

/** Generated from the SmartFlow domain specification — table capa_actions. */
@Entity
@Table(name = "capa_actions")
@Getter
@Setter
public class CapaAction extends TenantEntity {

    @Size(max = 50)
    @Column(name = "reference")
    private String reference;

    @NotBlank
    @Size(max = 255)
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    @NotBlank
    @Size(max = 30)
    @OneOf({"CORRECTIVE", "PREVENTIVE"})
    @Column(name = "type", nullable = false)
    private String type;

    @Size(max = 20)
    @OneOf({"LOW", "MEDIUM", "HIGH", "CRITICAL"})
    @Column(name = "priority")
    private String priority;

    @Size(max = 50)
    @Column(name = "status")
    private String status;

    @Size(max = 50)
    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Size(max = 255)
    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Size(max = 255)
    @Column(name = "verified_by")
    private String verifiedBy;

    @Size(max = 30)
    @Column(name = "effectiveness_result")
    private String effectivenessResult;

    @Column(name = "effectiveness_comment")
    private String effectivenessComment;

    @Column(name = "evidence_note")
    private String evidenceNote;
}
