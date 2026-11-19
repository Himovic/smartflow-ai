package com.dnd.smartflow.integration.domain;

import com.dnd.smartflow.common.persistence.TenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A configured connection to an external system (ERP, CMMS, GPS, IoT…). Credentials are stored encrypted. */
@Entity
@Table(name = "integration_connectors")
@Getter
@Setter
public class IntegrationConnector extends TenantEntity {
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, length = 50)
    private String provider;
    @Column(nullable = false, length = 30)
    private String category;
    /** LIVE or SIMULATED (demo). */
    @Column(nullable = false, length = 20)
    private String mode;
    @Column(name = "base_url", length = 500)
    private String baseUrl;
    @Column(name = "auth_type", length = 30)
    private String authType;
    @Column(name = "credentials_encrypted", columnDefinition = "text")
    private String credentialsEncrypted;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> config = new LinkedHashMap<>();
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "entity_types", columnDefinition = "jsonb")
    private List<String> entityTypes = new ArrayList<>();
    private boolean enabled;
    @Column(name = "sync_frequency_minutes")
    private Integer syncFrequencyMinutes;
    @Column(name = "conflict_policy", length = 20)
    private String conflictPolicy = "REMOTE_WINS";
    @Column(name = "last_sync_at")
    private Instant lastSyncAt;
    @Column(name = "last_status", length = 20)
    private String lastStatus;
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> cursors = new LinkedHashMap<>();
    @Column(name = "webhook_secret_encrypted", columnDefinition = "text")
    private String webhookSecretEncrypted;
}
