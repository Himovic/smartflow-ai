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

/** Generated from the SmartFlow domain specification — table certifications. */
@Entity
@Table(name = "certifications")
@Getter
@Setter
public class Certification extends TenantEntity {

    @NotNull
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 30)
    @OneOf({"HABILITATION", "LICENSE", "CERTIFICATE", "MEDICAL_FITNESS"})
    @Column(name = "cert_type")
    private String certType;

    @Size(max = 255)
    @Column(name = "issuer")
    private String issuer;

    @Size(max = 100)
    @Column(name = "number")
    private String number;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Size(max = 30)
    @OneOf({"VALID", "EXPIRING", "EXPIRED", "SUSPENDED"})
    @Column(name = "status")
    private String status;
}
