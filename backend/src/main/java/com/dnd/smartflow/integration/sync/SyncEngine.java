package com.dnd.smartflow.integration.sync;

import com.dnd.smartflow.common.persistence.TenantEntity;
import com.dnd.smartflow.common.security.CredentialCipher;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.integration.domain.*;
import com.dnd.smartflow.integration.spi.ConnectorProvider;
import com.dnd.smartflow.integration.spi.ConnectorProvider.ExternalRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Synchronization engine: incremental pulls (per-entity cursors), idempotency through payload hashes,
 * per-record transactions (one bad record never blocks the others), validation, conflict policy
 * (REMOTE_WINS / LOCAL_WINS / MANUAL), failed-record log with retry, and run throttling per connector.
 */
@Slf4j
@Service
public class SyncEngine {
    private static final Set<String> TELEMETRY = Set.of("VEHICLE_POSITION", "SENSOR_READING");
    private static final Duration MIN_MANUAL_INTERVAL = Duration.ofSeconds(15);

    private final Map<String, ConnectorProvider> providers = new LinkedHashMap<>();
    private final IntegrationConnectorRepository connectors;
    private final SyncJobRepository jobs;
    private final SyncRecordRepository records;
    private final IntegrationIdMapRepository idMap;
    private final RecordMapper mapper;
    private final CredentialCipher cipher;
    private final TransactionTemplate perRecord;
    private final TransactionTemplate tx;
    private final ObjectMapper canonicalJson;
    private final Map<UUID, Instant> lastRuns = new ConcurrentHashMap<>();

    public SyncEngine(List<ConnectorProvider> all, IntegrationConnectorRepository connectors, SyncJobRepository jobs, SyncRecordRepository records,
                      IntegrationIdMapRepository idMap, RecordMapper mapper, CredentialCipher cipher, PlatformTransactionManager tm, ObjectMapper om) {
        all.forEach(p -> providers.put(p.code(), p));
        this.connectors = connectors;
        this.jobs = jobs;
        this.records = records;
        this.idMap = idMap;
        this.mapper = mapper;
        this.cipher = cipher;
        this.perRecord = new TransactionTemplate(tm);
        this.perRecord.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tx = new TransactionTemplate(tm);
        this.canonicalJson = om.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public Collection<ConnectorProvider> providers() {
        return providers.values();
    }

    public ConnectorProvider provider(String code) {
        ConnectorProvider p = providers.get(code);
        if (p == null) throw ApiException.badRequest("UNKNOWN_PROVIDER", "Unknown provider " + code);
        return p;
    }

    public ConnectorProvider.ConnectorContext context(IntegrationConnector c) {
        Map<String, String> creds = new HashMap<>();
        if (c.getCredentialsEncrypted() != null) {
            try {
                creds = canonicalJson.readValue(cipher.decrypt(c.getCredentialsEncrypted()), new TypeReference<>() {});
            } catch (Exception e) {
                throw new IllegalStateException("Stored credentials cannot be decrypted");
            }
        }
        return new ConnectorProvider.ConnectorContext(c.getBaseUrl(), c.getAuthType(), creds, c.getConfig() == null ? Map.of() : c.getConfig());
    }

    public SyncJob run(UUID connectorId, String trigger) {
        IntegrationConnector c = connectors.findById(connectorId).orElseThrow(() -> ApiException.notFound("Connector"));
        if ("MANUAL".equals(trigger)) {
            Instant last = lastRuns.get(connectorId);
            if (last != null && last.plus(MIN_MANUAL_INTERVAL).isAfter(Instant.now())) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "SYNC_THROTTLED", "A synchronization just ran; wait a few seconds");
            }
        }
        if ("MANUAL".equals(trigger)) lastRuns.put(connectorId, Instant.now());
        ConnectorProvider provider = provider(c.getProvider());
        SyncJob job = tx.execute(s -> {
            SyncJob j = new SyncJob();
            j.setConnectorId(c.getId());
            j.setTriggerType(trigger);
            j.setStatus("RUNNING");
            j.setStartedAt(Instant.now());
            j.setRecordsRead(0);
            j.setRecordsCreated(0);
            j.setRecordsUpdated(0);
            j.setRecordsSkipped(0);
            j.setRecordsFailed(0);
            return jobs.save(j);
        });
        int[] counts = new int[5]; // read, created, updated, skipped, failed
        List<String> errors = new ArrayList<>();
        Map<String, String> cursors = new LinkedHashMap<>(c.getCursors() == null ? Map.of() : c.getCursors());
        ConnectorProvider.ConnectorContext ctx = context(c);
        for (String entity : c.getEntityTypes()) {
            if (!provider.supportedEntities().contains(entity)) continue;
            try {
                ConnectorProvider.FetchResult fr = provider.fetch(ctx, entity, cursors.get(entity));
                for (ExternalRecord r : fr.records()) {
                    counts[0]++;
                    process(c, job, entity, r, false, counts);
                }
                if (fr.nextCursor() != null) cursors.put(entity, fr.nextCursor());
            } catch (Exception e) {
                errors.add(entity + ": " + e.getMessage());
                log.warn("Sync of {} for connector {} failed: {}", entity, c.getName(), e.getMessage());
            }
        }
        return tx.execute(s -> {
            SyncJob j = jobs.findById(job.getId()).orElseThrow();
            j.setRecordsRead(counts[0]);
            j.setRecordsCreated(counts[1]);
            j.setRecordsUpdated(counts[2]);
            j.setRecordsSkipped(counts[3]);
            j.setRecordsFailed(counts[4]);
            j.setFinishedAt(Instant.now());
            j.setStatus(!errors.isEmpty() && counts[0] == 0 ? "FAILED" : (counts[4] > 0 || !errors.isEmpty()) ? "PARTIAL" : "SUCCESS");
            j.setMessage(errors.isEmpty() ? null : String.join("\n", errors));
            String cv = cursors.values().stream().max(String::compareTo).orElse(null);
            j.setCursorValue(cv == null || cv.length() <= 100 ? cv : cv.substring(0, 100));
            IntegrationConnector fresh = connectors.findById(c.getId()).orElseThrow();
            fresh.setCursors(cursors);
            fresh.setLastSyncAt(Instant.now());
            fresh.setLastStatus(j.getStatus());
            fresh.setLastError(j.getMessage());
            connectors.save(fresh);
            return jobs.save(j);
        });
    }

    /** Applies pushed webhook records through the same pipeline. */
    public SyncJob ingest(UUID connectorId, String entityType, List<ExternalRecord> pushed) {
        IntegrationConnector c = connectors.findById(connectorId).orElseThrow(() -> ApiException.notFound("Connector"));
        SyncJob job = tx.execute(s -> {
            SyncJob j = new SyncJob();
            j.setConnectorId(c.getId());
            j.setTriggerType("WEBHOOK");
            j.setStatus("RUNNING");
            j.setStartedAt(Instant.now());
            return jobs.save(j);
        });
        int[] counts = new int[5];
        for (ExternalRecord r : pushed) {
            counts[0]++;
            process(c, job, entityType, r, false, counts);
        }
        return tx.execute(s -> {
            SyncJob j = jobs.findById(job.getId()).orElseThrow();
            j.setRecordsRead(counts[0]);
            j.setRecordsCreated(counts[1]);
            j.setRecordsUpdated(counts[2]);
            j.setRecordsSkipped(counts[3]);
            j.setRecordsFailed(counts[4]);
            j.setFinishedAt(Instant.now());
            j.setStatus(counts[4] > 0 ? "PARTIAL" : "SUCCESS");
            return jobs.save(j);
        });
    }

    public SyncRecord retry(UUID recordId) {
        SyncRecord rec = records.findById(recordId).orElseThrow(() -> ApiException.notFound("Sync record"));
        if (!Set.of("FAILED", "CONFLICT").contains(rec.getStatus())) throw ApiException.conflict("NOT_RETRYABLE", "Only failed or conflicting records can be retried");
        IntegrationConnector c = connectors.findById(rec.getConnectorId()).orElseThrow();
        int[] counts = new int[5];
        SyncJob job = jobs.findById(rec.getJobId()).orElseThrow();
        String outcome = process(c, job, rec.getEntityType(), new ExternalRecord(rec.getExternalId(), Instant.now(), rec.getPayload()), true, counts);
        return tx.execute(s -> {
            SyncRecord r = records.findById(recordId).orElseThrow();
            r.setAttempts(Optional.ofNullable(r.getAttempts()).orElse(1) + 1);
            if (!"FAILED".equals(outcome)) {
                r.setStatus("RETRIED");
                r.setError(null);
            } else {
                r.setError(lastError.get());
            }
            return records.save(r);
        });
    }

    private final ThreadLocal<String> lastError = new ThreadLocal<>();

    private String process(IntegrationConnector c, SyncJob job, String entity, ExternalRecord r, boolean force, int[] counts) {
        String hash = hash(r.payload());
        try {
            String outcome = perRecord.execute(s -> {
                Optional<IntegrationIdMap> link = idMap.findOne((root, q, cb) -> cb.and(cb.equal(root.get("connectorId"), c.getId()),
                        cb.equal(root.get("entityType"), entity), cb.equal(root.get("externalId"), r.externalId())));
                if (link.isPresent() && hash.equals(link.get().getPayloadHash()) && !force) return "SKIPPED";
                if (link.isPresent() && !force && !TELEMETRY.contains(entity)) {
                    Optional<? extends TenantEntity> local = mapper.findExisting(entity, link.get().getLocalId(), r.payload());
                    boolean locallyModified = local.isPresent() && local.get().getUpdatedAt() != null && link.get().getSyncedAt() != null
                            && local.get().getUpdatedAt().isAfter(link.get().getSyncedAt().plusSeconds(2));
                    if (locallyModified) {
                        switch (Objects.toString(c.getConflictPolicy(), "REMOTE_WINS")) {
                            case "LOCAL_WINS" -> {
                                return "SKIPPED";
                            }
                            case "MANUAL" -> {
                                throw new ConflictException("Record modified both locally and in the external system");
                            }
                            default -> { /* remote wins */ }
                        }
                    }
                }
                RecordMapper.Outcome o = mapper.apply(entity, link.map(IntegrationIdMap::getLocalId).orElse(null), r.payload());
                if (!TELEMETRY.contains(entity)) {
                    IntegrationIdMap m = link.orElseGet(IntegrationIdMap::new);
                    m.setConnectorId(c.getId());
                    m.setEntityType(entity);
                    m.setExternalId(r.externalId());
                    m.setLocalId(o.localId());
                    m.setPayloadHash(hash);
                    m.setSyncedAt(Instant.now());
                    idMap.save(m);
                    saveRecord(c, job, entity, r, o.created() ? "CREATED" : "UPDATED", null, o.localId());
                }
                return o.created() ? "CREATED" : "UPDATED";
            });
            switch (Objects.requireNonNull(outcome)) {
                case "CREATED" -> counts[1]++;
                case "UPDATED" -> counts[2]++;
                default -> counts[3]++;
            }
            return outcome;
        } catch (Exception e) {
            counts[4]++;
            String msg = e instanceof ConflictException || e instanceof RecordMapper.ValidationException || e instanceof ApiException
                    ? e.getMessage() : e.getClass().getSimpleName() + ": " + e.getMessage();
            lastError.set(msg);
            if (!force) {
                String status = e instanceof ConflictException ? "CONFLICT" : "FAILED";
                tx.executeWithoutResult(s -> saveRecord(c, job, entity, r, status, msg, null));
            }
            return "FAILED";
        }
    }

    private void saveRecord(IntegrationConnector c, SyncJob job, String entity, ExternalRecord r, String status, String error, UUID localId) {
        SyncRecord rec = new SyncRecord();
        rec.setJobId(job.getId());
        rec.setConnectorId(c.getId());
        rec.setEntityType(entity);
        rec.setExternalId(r.externalId().length() > 100 ? r.externalId().substring(0, 100) : r.externalId());
        rec.setStatus(status);
        rec.setPayload(r.payload());
        rec.setError(error);
        rec.setAttempts(1);
        rec.setLocalId(localId);
        records.save(rec);
    }

    private String hash(Map<String, Object> payload) {
        try {
            return CredentialCipher.sha256(canonicalJson.writeValueAsString(payload));
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    static class ConflictException extends RuntimeException {
        ConflictException(String m) {
            super(m);
        }
    }
}
