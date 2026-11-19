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

/** Generated from the SmartFlow domain specification — table training_records. */
@Entity
@Table(name = "training_records")
@Getter
@Setter
public class TrainingRecord extends TenantEntity {

    @NotNull
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @NotNull
    @Column(name = "training_id", nullable = false)
    private UUID trainingId;

    @Column(name = "session_date")
    private LocalDate sessionDate;

    @Size(max = 30)
    @OneOf({"PLANNED", "COMPLETED", "FAILED", "EXPIRED"})
    @Column(name = "status")
    private String status;

    @Column(name = "score")
    private Double score;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Size(max = 255)
    @Column(name = "trainer")
    private String trainer;

    @Size(max = 100)
    @Column(name = "certificate_number")
    private String certificateNumber;
}
