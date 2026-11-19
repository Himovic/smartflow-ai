package com.dnd.smartflow.organization.domain;

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

/** Generated from the SmartFlow domain specification — table employees. */
@Entity
@Table(name = "employees")
@Getter
@Setter
public class Employee extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "matricule", nullable = false)
    private String matricule;

    @NotBlank
    @Size(max = 100)
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Size(max = 255)
    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Size(max = 255)
    @Column(name = "email")
    private String email;

    @Size(max = 50)
    @Column(name = "phone")
    private String phone;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Size(max = 50)
    @OneOf({"ACTIVE", "ON_LEAVE", "INACTIVE"})
    @Column(name = "status")
    private String status;

    @Column(name = "user_id")
    private UUID userId;
}
