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

/** Generated from the SmartFlow domain specification — table audit_findings. */
@Entity
@Table(name = "audit_findings")
@Getter
@Setter
public class AuditFinding extends TenantEntity {

    @NotNull
    @Column(name = "audit_id", nullable = false)
    private UUID auditId;

    @Size(max = 50)
    @Column(name = "reference")
    private String reference;

    @Size(max = 100)
    @Column(name = "clause")
    private String clause;

    @NotBlank
    @Size(max = 30)
    @OneOf({"MAJOR_NC", "MINOR_NC", "OBSERVATION", "OPPORTUNITY", "CONFORMITY"})
    @Column(name = "finding_type", nullable = false)
    private String findingType;

    @NotBlank
    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "evidence")
    private String evidence;

    @Column(name = "non_conformity_id")
    private UUID nonConformityId;

    @Size(max = 30)
    @OneOf({"OPEN", "IN_PROGRESS", "CLOSED"})
    @Column(name = "status")
    private String status;
}
