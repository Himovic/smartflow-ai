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

/** Generated from the SmartFlow domain specification — table work_permits. */
@Entity
@Table(name = "work_permits")
@Getter
@Setter
public class WorkPermit extends TenantEntity {

    @Size(max = 50)
    @Column(name = "reference")
    private String reference;

    @NotBlank
    @Size(max = 50)
    @OneOf({"HOT_WORK", "CONFINED_SPACE", "WORK_AT_HEIGHT", "ELECTRICAL", "EXCAVATION", "LOCKOUT_TAGOUT", "CHEMICAL"})
    @Column(name = "permit_type", nullable = false)
    private String permitType;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "description")
    private String description;

    @Size(max = 255)
    @Column(name = "requested_by")
    private String requestedBy;

    @Size(max = 255)
    @Column(name = "contractor")
    private String contractor;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Size(max = 30)
    @OneOf({"REQUESTED", "APPROVED", "ACTIVE", "CLOSED", "REJECTED"})
    @Column(name = "status")
    private String status;

    @Size(max = 255)
    @Column(name = "approved_by")
    private String approvedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "precautions")
    private List<String> precautions;

    @Size(max = 100)
    @Column(name = "gas_test_result")
    private String gasTestResult;
}
