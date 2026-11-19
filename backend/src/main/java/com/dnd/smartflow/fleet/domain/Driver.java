package com.dnd.smartflow.fleet.domain;

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

/** Generated from the SmartFlow domain specification — table drivers. */
@Entity
@Table(name = "drivers")
@Getter
@Setter
public class Driver extends TenantEntity {

    @Column(name = "employee_id")
    private UUID employeeId;

    @NotBlank
    @Size(max = 100)
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Size(max = 50)
    @Column(name = "license_number")
    private String licenseNumber;

    @Size(max = 20)
    @Column(name = "license_category")
    private String licenseCategory;

    @Column(name = "license_expiry")
    private LocalDate licenseExpiry;

    @Size(max = 50)
    @Column(name = "phone")
    private String phone;

    @Size(max = 30)
    @OneOf({"AVAILABLE", "ON_DUTY", "OFF_DUTY", "SUSPENDED"})
    @Column(name = "status")
    private String status;

    @Column(name = "safety_score")
    private Double safetyScore;

    @Column(name = "home_site_id")
    private UUID homeSiteId;

    @Column(name = "harsh_braking_events")
    private Integer harshBrakingEvents;

    @Column(name = "speeding_events")
    private Integer speedingEvents;
}
