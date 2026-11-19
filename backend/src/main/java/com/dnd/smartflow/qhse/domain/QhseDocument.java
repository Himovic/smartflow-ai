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

/** Generated from the SmartFlow domain specification — table qhse_documents. */
@Entity
@Table(name = "qhse_documents")
@Getter
@Setter
public class QhseDocument extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "reference", nullable = false)
    private String reference;

    @NotBlank
    @Size(max = 255)
    @Column(name = "title", nullable = false)
    private String title;

    @NotBlank
    @Size(max = 50)
    @OneOf({"POLICY", "PROCEDURE", "WORK_INSTRUCTION", "FORM", "CERTIFICATE", "SAFETY_DATA_SHEET", "REPORT", "MANUAL"})
    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Size(max = 30)
    @Column(name = "domain")
    private String domain;

    @Size(max = 20)
    @Column(name = "doc_version")
    private String docVersion;

    @Size(max = 30)
    @OneOf({"DRAFT", "IN_REVIEW", "APPROVED", "OBSOLETE"})
    @Column(name = "status")
    private String status;

    @Size(max = 255)
    @Column(name = "owner_name")
    private String ownerName;

    @Size(max = 255)
    @Column(name = "approver_name")
    private String approverName;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "site_id")
    private UUID siteId;

    @Size(max = 30)
    @OneOf({"PUBLIC", "INTERNAL", "RESTRICTED"})
    @Column(name = "access_level")
    private String accessLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_roles")
    private List<String> allowedRoles;

    @Size(max = 100)
    @Column(name = "standard_clause")
    private String standardClause;

    @Column(name = "previous_version_id")
    private UUID previousVersionId;

    @Column(name = "description")
    private String description;
}
