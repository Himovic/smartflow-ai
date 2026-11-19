package com.dnd.smartflow.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** An independent customer organization. Every business record belongs to exactly one tenant. */
@Entity
@Table(name = "tenants")
@Getter
@Setter
public class Tenant {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "industry_profile", nullable = false, length = 50)
    private String industryProfile;

    private String country;
    private String timezone;

    @Column(name = "default_language", length = 5)
    private String defaultLanguage;

    private boolean demo;
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private TenantSettings settings = new TenantSettings();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
