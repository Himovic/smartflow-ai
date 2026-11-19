package com.dnd.smartflow.integration.web;

import com.dnd.smartflow.audit.AuditService;
import com.dnd.smartflow.common.security.CredentialCipher;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.security.PermissionService;
import com.dnd.smartflow.common.tenant.TenantContext;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.common.web.PageResponse;
import com.dnd.smartflow.integration.domain.*;
import com.dnd.smartflow.integration.spi.ConnectorProvider;
import com.dnd.smartflow.integration.sync.SyncEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
@Tag(name = "Integration hub", description = "ERP (SAP, Odoo), CMMS/GMAO, GPS, IoT connectors, synchronization and webhooks")
public class IntegrationController {
    private final IntegrationConnectorRepository connectors;
    private final SyncJobRepository jobs;
    private final SyncRecordRepository records;
    private final SyncEngine engine;
    private final CredentialCipher cipher;
    private final PermissionService perm;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public record ConnectorRequest(@NotBlank String name, @NotBlank String provider, String baseUrl, String authType,
                                   Map<String, String> credentials, Map<String, Object> config, List<String> entityTypes, Boolean enabled,
                                   Integer syncFrequencyMinutes, String conflictPolicy) {
    }

    public record WebhookPayload(String entityType, List<Map<String, Object>> records) {
    }

    @GetMapping("/providers")
    public List<Map<String, Object>> providers() {
        perm.require("integrations", "read");
        return engine.providers().stream().map(p -> Map.<String, Object>of("code", p.code(), "label", p.label(), "category", p.category(),
                "simulated", p.simulated(), "entities", p.supportedEntities(), "configKeys", p.configKeys())).toList();
    }

    @GetMapping("/connectors")
    public List<Map<String, Object>> list() {
        perm.require("integrations", "read");
        return connectors.findAll(Sort.by("name")).stream().map(this::view).toList();
    }

    @GetMapping("/connectors/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        perm.require("integrations", "read");
        return view(load(id));
    }

    @PostMapping("/connectors")
    @Transactional
    public Map<String, Object> create(@Valid @RequestBody ConnectorRequest req) {
        perm.require("integrations", "admin");
        ConnectorProvider p = engine.provider(req.provider());
        guardDemo(p);
        IntegrationConnector c = new IntegrationConnector();
        apply(c, req, p);
        String secret = cipher.randomToken(24);
        c.setWebhookSecretEncrypted(cipher.encrypt(secret));
        c = connectors.save(c);
        audit.log("CONNECTOR_CREATED", "IntegrationConnector", c.getId(), Map.of("provider", c.getProvider()));
        Map<String, Object> v = new LinkedHashMap<>(view(c));
        v.put("webhookSecret", secret);
        v.put("webhookUrl", "/api/v1/integrations/webhooks/" + c.getId());
        return v;
    }

    @PutMapping("/connectors/{id}")
    @Transactional
    public Map<String, Object> update(@PathVariable UUID id, @Valid @RequestBody ConnectorRequest req) {
        perm.require("integrations", "admin");
        IntegrationConnector c = load(id);
        ConnectorProvider p = engine.provider(req.provider());
        guardDemo(p);
        apply(c, req, p);
        audit.log("CONNECTOR_UPDATED", "IntegrationConnector", id, Map.of("credentialsChanged", req.credentials() != null && !req.credentials().isEmpty()));
        return view(connectors.save(c));
    }

    @DeleteMapping("/connectors/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable UUID id) {
        perm.require("integrations", "admin");
        connectors.delete(load(id));
        audit.log("CONNECTOR_DELETED", "IntegrationConnector", id, Map.of());
        return Map.of("deleted", id);
    }

    @PostMapping("/connectors/{id}/toggle")
    @Transactional
    public Map<String, Object> toggle(@PathVariable UUID id) {
        perm.require("integrations", "admin");
        IntegrationConnector c = load(id);
        c.setEnabled(!c.isEnabled());
        return view(connectors.save(c));
    }

    @PostMapping("/connectors/{id}/test")
    @Operation(summary = "Test connectivity and authentication")
    public ConnectorProvider.TestResult test(@PathVariable UUID id) {
        perm.require("integrations", "write");
        IntegrationConnector c = load(id);
        return engine.provider(c.getProvider()).test(engine.context(c));
    }

    @PostMapping("/connectors/{id}/run")
    @Operation(summary = "Start a manual incremental synchronization")
    public SyncJob run(@PathVariable UUID id) {
        perm.require("integrations", "write");
        SyncJob job = engine.run(load(id).getId(), "MANUAL");
        audit.log("SYNC_RUN", "IntegrationConnector", id, Map.of("status", job.getStatus(), "read", job.getRecordsRead()));
        return job;
    }

    @GetMapping("/connectors/{id}/jobs")
    public PageResponse<SyncJob> jobs(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page) {
        perm.require("integrations", "read");
        load(id);
        return PageResponse.of(jobs.findAll((r, q, cb) -> cb.equal(r.get("connectorId"), id), PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "startedAt"))));
    }

    @GetMapping("/records")
    public PageResponse<SyncRecord> records(@RequestParam(required = false) UUID jobId, @RequestParam(required = false) UUID connectorId,
                                            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page) {
        perm.require("integrations", "read");
        return PageResponse.of(records.findAll((r, q, cb) -> cb.and(
                jobId == null ? cb.conjunction() : cb.equal(r.get("jobId"), jobId),
                connectorId == null ? cb.conjunction() : cb.equal(r.get("connectorId"), connectorId),
                status == null ? cb.conjunction() : r.get("status").in(Arrays.asList(status.split(",")))),
                PageRequest.of(page, 50, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PostMapping("/records/{id}/retry")
    public SyncRecord retry(@PathVariable UUID id) {
        perm.require("integrations", "write");
        return engine.retry(id);
    }

    /** Push endpoint for external systems; authenticated with an HMAC-SHA256 signature of the raw body. */
    @PostMapping("/webhooks/{connectorId}")
    public Map<String, Object> webhook(@PathVariable UUID connectorId, @RequestHeader(value = "X-SmartFlow-Signature", required = false) String signature,
                                       @RequestBody String rawBody) throws Exception {
        IntegrationConnector c = TenantContext.callAs(TenantContext.ROOT, () -> connectors.findById(connectorId))
                .orElseThrow(() -> ApiException.notFound("Connector"));
        if (!c.isEnabled()) throw new ApiException(HttpStatus.FORBIDDEN, "CONNECTOR_DISABLED", "Connector is disabled");
        String secret = cipher.decrypt(c.getWebhookSecretEncrypted());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        if (signature == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_SIGNATURE", "Invalid webhook signature");
        }
        WebhookPayload payload = mapper.readValue(rawBody, WebhookPayload.class);
        if (payload.records() == null || payload.records().size() > 1000) throw ApiException.badRequest("INVALID_PAYLOAD", "1 to 1000 records expected");
        List<ConnectorProvider.ExternalRecord> recs = payload.records().stream().map(m -> new ConnectorProvider.ExternalRecord(
                String.valueOf(m.getOrDefault("externalId", UUID.randomUUID())), Instant.now(),
                m.get("payload") instanceof Map<?, ?> p ? castMap(p) : m)).toList();
        SyncJob job = TenantContext.callAs(c.getTenantId(), () -> engine.ingest(connectorId, payload.entityType(), recs));
        return Map.of("jobId", job.getId(), "status", job.getStatus(), "received", recs.size());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private void guardDemo(ConnectorProvider p) {
        if (CurrentUser.require().demo() && !p.simulated()) {
            throw ApiException.forbidden("The public demonstration only allows SIMULATED connectors (no external credentials are stored)");
        }
    }

    private void apply(IntegrationConnector c, ConnectorRequest req, ConnectorProvider p) {
        c.setName(req.name());
        c.setProvider(p.code());
        c.setCategory(req.config() != null && req.config().get("category") != null ? String.valueOf(req.config().get("category")) : p.category());
        c.setMode(p.simulated() ? "SIMULATED" : "LIVE");
        c.setBaseUrl(req.baseUrl());
        c.setAuthType(req.authType());
        if (req.credentials() != null && !req.credentials().isEmpty()) {
            try {
                c.setCredentialsEncrypted(cipher.encrypt(mapper.writeValueAsString(req.credentials())));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        c.setConfig(req.config() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(req.config()));
        List<String> entities = req.entityTypes() == null || req.entityTypes().isEmpty() ? p.supportedEntities()
                : req.entityTypes().stream().filter(p.supportedEntities()::contains).toList();
        c.setEntityTypes(new ArrayList<>(entities));
        c.setEnabled(req.enabled() == null || req.enabled());
        c.setSyncFrequencyMinutes(req.syncFrequencyMinutes() == null ? 60 : Math.max(1, req.syncFrequencyMinutes()));
        c.setConflictPolicy(List.of("REMOTE_WINS", "LOCAL_WINS", "MANUAL").contains(req.conflictPolicy()) ? req.conflictPolicy() : "REMOTE_WINS");
    }

    private IntegrationConnector load(UUID id) {
        return connectors.findById(id).orElseThrow(() -> ApiException.notFound("Connector"));
    }

    private Map<String, Object> view(IntegrationConnector c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("provider", c.getProvider());
        m.put("category", c.getCategory());
        m.put("mode", c.getMode());
        m.put("baseUrl", c.getBaseUrl());
        m.put("authType", c.getAuthType());
        m.put("credentialsConfigured", c.getCredentialsEncrypted() != null);
        m.put("config", c.getConfig());
        m.put("entityTypes", c.getEntityTypes());
        m.put("enabled", c.isEnabled());
        m.put("syncFrequencyMinutes", c.getSyncFrequencyMinutes());
        m.put("conflictPolicy", c.getConflictPolicy());
        m.put("lastSyncAt", c.getLastSyncAt());
        m.put("lastStatus", c.getLastStatus());
        m.put("lastError", c.getLastError());
        m.put("cursors", c.getCursors());
        m.put("webhookUrl", "/api/v1/integrations/webhooks/" + c.getId());
        m.put("failedRecords", records.count((r, q, cb) -> cb.and(cb.equal(r.get("connectorId"), c.getId()), r.get("status").in("FAILED", "CONFLICT"))));
        return m;
    }
}
