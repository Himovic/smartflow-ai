package com.dnd.smartflow.integration.providers;

import com.dnd.smartflow.integration.spi.ConnectorProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Live SAP S/4HANA connector over OData v2 (standard APIs of the SAP Business Accelerator Hub). Entity set
 * paths and the change-timestamp field can be overridden in the connector configuration
 * ({@code paths.PRODUCT}, {@code changedField}). Authentication: BASIC or OAUTH2 client credentials.
 * SAP PM equipment and maintenance orders are read through API_EQUIPMENT and API_MAINTENANCEORDER.
 */
@Component
public class SapODataProvider implements ConnectorProvider {
    static final Map<String, String> DEFAULT_PATHS = Map.of(
            "PRODUCT", "/sap/opu/odata/sap/API_PRODUCT_SRV/A_Product",
            "CUSTOMER", "/sap/opu/odata/sap/API_BUSINESS_PARTNER/A_Customer",
            "SUPPLIER", "/sap/opu/odata/sap/API_BUSINESS_PARTNER/A_Supplier",
            "STOCK", "/sap/opu/odata/sap/API_MATERIAL_STOCK_SRV/A_MatlStkInAcctMod",
            "EQUIPMENT", "/sap/opu/odata/sap/API_EQUIPMENT/Equipment",
            "WORK_ORDER", "/sap/opu/odata/sap/API_MAINTENANCEORDER/MaintenanceOrder");

    @Override public String code() { return "SAP_S4HANA"; }
    @Override public String label() { return "SAP S/4HANA (OData)"; }
    @Override public String category() { return "ERP"; }
    @Override public boolean simulated() { return false; }
    @Override public List<String> supportedEntities() { return List.of("PRODUCT", "CUSTOMER", "SUPPLIER", "STOCK", "EQUIPMENT", "WORK_ORDER"); }
    @Override public List<String> configKeys() { return List.of("sapClient", "paths.<ENTITY> (optional)", "tokenUrl (OAuth2)", "username/password or clientId/clientSecret (credentials)"); }

    @Override
    public TestResult test(ConnectorContext ctx) {
        long t = System.currentTimeMillis();
        try {
            HttpSupport.get(url(ctx, "PRODUCT", null, 1), headers(ctx));
            return new TestResult(true, "OData service reachable", System.currentTimeMillis() - t);
        } catch (Exception e) {
            return new TestResult(false, e.getMessage(), System.currentTimeMillis() - t);
        }
    }

    @Override
    public FetchResult fetch(ConnectorContext ctx, String entityType, String cursor) {
        try {
            JsonNode res = HttpSupport.get(url(ctx, entityType, cursor, 500), headers(ctx));
            JsonNode items = res.path("d").path("results").isArray() ? res.path("d").path("results") : res.path("value");
            List<ExternalRecord> out = new ArrayList<>();
            String next = cursor;
            for (JsonNode n : items) {
                Instant updated = parseSapDate(n.path(changedField(ctx, entityType)).asText(null));
                Map<String, Object> payload = canonical(entityType, n);
                out.add(new ExternalRecord(String.valueOf(payload.getOrDefault("_key", UUID.randomUUID())), updated, payload));
                if (updated != null && (next == null || updated.isAfter(Instant.parse(next)))) next = updated.toString();
            }
            return new FetchResult(out, next);
        } catch (Exception e) {
            throw new IllegalStateException("SAP OData fetch failed: " + e.getMessage(), e);
        }
    }

    private String url(ConnectorContext ctx, String entity, String cursor, int top) {
        @SuppressWarnings("unchecked")
        Map<String, Object> paths = (Map<String, Object>) ctx.config().getOrDefault("paths", Map.of());
        String path = String.valueOf(paths.getOrDefault(entity, DEFAULT_PATHS.get(entity)));
        StringBuilder sb = new StringBuilder(ctx.baseUrl().replaceAll("/$", "")).append(path).append("?$format=json&$top=").append(top);
        if (ctx.config().get("sapClient") != null) sb.append("&sap-client=").append(ctx.config().get("sapClient"));
        if (cursor != null) {
            String filter = changedField(ctx, entity) + " gt datetimeoffset'" + cursor + "'";
            sb.append("&$filter=").append(URLEncoder.encode(filter, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private Map<String, String> headers(ConnectorContext ctx) throws Exception {
        Map<String, String> h = new HashMap<>();
        if ("OAUTH2".equals(ctx.authType())) {
            String form = "grant_type=client_credentials&client_id=" + URLEncoder.encode(ctx.credentials().getOrDefault("clientId", ""), StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(ctx.credentials().getOrDefault("clientSecret", ""), StandardCharsets.UTF_8);
            JsonNode tok = HttpSupport.postForm(String.valueOf(ctx.config().get("tokenUrl")), form, Map.of());
            h.put("Authorization", "Bearer " + tok.path("access_token").asText());
        } else {
            h.put("Authorization", HttpSupport.basic(ctx.credentials().getOrDefault("username", ""), ctx.credentials().getOrDefault("password", "")));
        }
        return h;
    }

    private static String changedField(ConnectorContext ctx, String entity) {
        Object f = ctx.config().get("changedField");
        if (f != null) return f.toString();
        return switch (entity) {
            case "STOCK" -> "LastChangeDateTime";
            case "EQUIPMENT", "WORK_ORDER" -> "LastChangeDateTime";
            default -> "LastChangeDateTime";
        };
    }

    static Instant parseSapDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.startsWith("/Date(")) {
            String ms = raw.replaceAll("[^0-9-]", "");
            return Instant.ofEpochMilli(Long.parseLong(ms.length() > 13 ? ms.substring(0, 13) : ms));
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> canonical(String entity, JsonNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (entity) {
            case "PRODUCT" -> {
                m.put("_key", n.path("Product").asText());
                m.put("sku", n.path("Product").asText());
                m.put("name", n.path("ProductDescription").asText(n.path("Product").asText()));
                m.put("category", n.path("ProductGroup").asText(null));
                m.put("unit", n.path("BaseUnit").asText(null));
                m.put("unitWeightKg", n.path("GrossWeight").asDouble(0));
            }
            case "CUSTOMER" -> {
                m.put("_key", n.path("Customer").asText());
                m.put("code", n.path("Customer").asText());
                m.put("name", n.path("CustomerName").asText(n.path("CustomerFullName").asText()));
                m.put("city", n.path("CityName").asText(null));
            }
            case "SUPPLIER" -> {
                m.put("_key", n.path("Supplier").asText());
                m.put("code", n.path("Supplier").asText());
                m.put("name", n.path("SupplierName").asText());
                m.put("city", n.path("CityName").asText(null));
                m.put("country", n.path("Country").asText(null));
            }
            case "STOCK" -> {
                m.put("_key", n.path("Material").asText() + "|" + n.path("Plant").asText() + "|" + n.path("StorageLocation").asText() + "|" + n.path("Batch").asText());
                m.put("sku", n.path("Material").asText());
                m.put("warehouseCode", n.path("StorageLocation").asText(n.path("Plant").asText()));
                m.put("batchNumber", n.path("Batch").asText(null));
                m.put("quantity", (int) Math.round(n.path("MatlWrhsStkQtyInMatlBaseUnit").asDouble()));
            }
            case "EQUIPMENT" -> {
                m.put("_key", n.path("Equipment").asText());
                m.put("code", n.path("Equipment").asText());
                m.put("name", n.path("EquipmentName").asText());
                m.put("category", n.path("EquipmentCategory").asText(null));
                m.put("manufacturer", n.path("AssetManufacturerName").asText(null));
                m.put("serialNumber", n.path("SerialNumber").asText(null));
            }
            case "WORK_ORDER" -> {
                m.put("_key", n.path("MaintenanceOrder").asText());
                m.put("reference", "SAP-" + n.path("MaintenanceOrder").asText());
                m.put("description", n.path("MaintenanceOrderDesc").asText());
                m.put("equipmentCode", n.path("Equipment").asText(null));
                m.put("type", "PM01".equals(n.path("MaintenanceOrderType").asText()) ? "CORRECTIVE" : "PREVENTIVE");
            }
            default -> { }
        }
        return m;
    }
}
