package com.dnd.smartflow.iam;

import com.dnd.smartflow.common.persistence.TenantEntity;
import com.dnd.smartflow.common.security.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends TenantEntity {
    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Role role;

    @Column(name = "job_title")
    private String jobTitle;

    private String phone;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(name = "all_sites")
    private boolean allSites;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "site_ids", columnDefinition = "jsonb")
    private List<UUID> siteIds = new ArrayList<>();

    @Column(length = 5)
    private String language = "fr";

    private boolean active = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_attempts")
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "password_reset_hash")
    private String passwordResetHash;

    @Column(name = "password_reset_expires")
    private Instant passwordResetExpires;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    public String fullName() {
        return firstName + " " + lastName;
    }
}
