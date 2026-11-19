package com.dnd.smartflow.integration.providers;

import com.dnd.smartflow.integration.spi.ConnectorProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Configurable REST connector for CMMS/GMAO, GPS trackers, IoT platforms, LIMS or custom systems.
 * Configuration: {@code endpoints.<ENTITY>} (path, {@code {since}} placeholder), {@code itemsPath},
 * {@code idField}, {@code updatedAtField}, {@code fieldMap.<ENTITY>.<canonical>=<remote>}; auth BEARER,
 * API_KEY (header name in {@code apiKeyHeader}) or BASIC.
 */
@Component
public class GenericRestProvider implements ConnectorProvider {
    @Override public String code() { return "GENERIC_REST"; }
    @Override public String label() { return "Generic REST (CMMS / GPS / IoT / custom)"; }
    @Override public String category() { return "CUSTOM"; }
    @Override public boolean simulated() { return false; }
    @Override public List<String> supportedEntities() { return List.of("PRODUCT", "CUSTOMER", "SUPPLIER", "STOCK", "EQUIPMENT", "WORK_ORDER", "VEHICLE_POSITION", "SENSOR_READING"); }
    @Override public List<String> configKeys() { return List.of("endpoints.<ENTITY>", "itemsPath", "idField", "updatedAtField", "fieldMap.<ENTITY>", "healthPath", "apiKeyHeader"); }

    @Override
    public TestResult test(ConnectorContext ctx) {
        long t = System.currentTimeMillis();
        try {
            HttpSupport.get(ctx.baseUrl().replaceAll("/$", "") + ctx.config().getOrDefault("healthPath", "/"), headers(ctx));
            return new TestResult(true, "Endpoint reachable", System.currentTimeMillis() - t);
        } catch (Exception e) {
            return new TestResult(false, e.getMessage(), System.currentTimeMillis() - t);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public FetchResult fetch(ConnectorContext ctx, String entityType, String cursor) {
        Map<String, Object> endpoints = (Map<String, Object>) ctx.config().getOrDefault("endpoints", Map.of());
        Object ep = endpoints.get(entityType);
        if (ep == null) throw new IllegalArgumentException("No endpoint configured for " + entityType);
        String path = ep.toString().replace("{since}", URLEncoder.encode(cursor == null ? "1970-01-01T00:00:00Z" : cursor, StandardCharsets.UTF_8));
        try {
            JsonNode root = HttpSupport.get(ctx.baseUrl().replaceAll("/$", "") + path, headers(ctx));
            JsonNode items = HttpSupport.path(root, (String) ctx.config().getOrDefault("itemsPath", ""));
            String idField = (String) ctx.config().getOrDefault("idField", "id");
            String updField = (String) ctx.config().getOrDefault("updatedAtField", "updatedAt");
            Map<String, Object> fieldMap = (Map<String, Object>) ((Map<String, Object>) ctx.config().getOrDefault("fieldMap", Map.of())).getOrDefault(entityType, Map.of());
            List<ExternalRecord> out = new ArrayList<>();
            String next = cursor;
            for (JsonNode n : items) {
                Map<String, Object> raw = HttpSupport.mapper().convertValue(n, Map.class);
                Map<String, Object> payload = new LinkedHashMap<>(raw);
                fieldMap.forEach((canonical, remote) -> payload.put(canonical, HttpSupport.mapper().convertValue(HttpSupport.path(n, remote.toString()), Object.class)));
                Instant updated = null;
                try {
                    updated = Instant.parse(n.path(updField).asText());
                } catch (Exception ignored) {
                    // records without timestamp are treated as changed
                }
                out.add(new ExternalRecord(n.path(idField).asText(), updated, payload));
                if (updated != null && (next == null || updated.isAfter(Instant.parse(next)))) next = updated.toString();
            }
            return new FetchResult(out, next);
        } catch (Exception e) {
            throw new IllegalStateException("REST fetch failed: " + e.getMessage(), e);
        }
    }

    private Map<String, String> headers(ConnectorContext ctx) {
        Map<String, String> h = new HashMap<>();
        switch (Objects.toString(ctx.authType(), "NONE")) {
            case "BEARER" -> h.put("Authorization", "Bearer " + ctx.credentials().getOrDefault("token", ""));
            case "API_KEY" -> h.put(String.valueOf(ctx.config().getOrDefault("apiKeyHeader", "X-API-Key")), ctx.credentials().getOrDefault("apiKey", ""));
            case "BASIC" -> h.put("Authorization", HttpSupport.basic(ctx.credentials().getOrDefault("username", ""), ctx.credentials().getOrDefault("password", "")));
            default -> { }
        }
        return h;
    }
}
