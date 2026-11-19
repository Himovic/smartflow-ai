package com.dnd.smartflow.integration.providers;

import com.dnd.smartflow.integration.spi.ConnectorProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Live Odoo connector over the external JSON-RPC API ({@code /jsonrpc}, services {@code common} and
 * {@code object}). Works with Odoo 13+ (customer_rank/supplier_rank); set config {@code odooVersion < 13}
 * to use the legacy customer/supplier flags. Incremental reads use {@code write_date}.
 */
@Component
public class OdooJsonRpcProvider implements ConnectorProvider {
    private static final DateTimeFormatter ODOO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override public String code() { return "ODOO"; }
    @Override public String label() { return "Odoo (JSON-RPC)"; }
    @Override public String category() { return "ERP"; }
    @Override public boolean simulated() { return false; }
    @Override public List<String> supportedEntities() { return List.of("PRODUCT", "CUSTOMER", "SUPPLIER", "STOCK", "EQUIPMENT", "WORK_ORDER"); }
    @Override public List<String> configKeys() { return List.of("database", "odooVersion", "username (credential)", "password or API key (credential)"); }

    @Override
    public TestResult test(ConnectorContext ctx) {
        long t = System.currentTimeMillis();
        try {
            int uid = login(ctx);
            return new TestResult(uid > 0, uid > 0 ? "Authenticated as uid " + uid : "Authentication refused", System.currentTimeMillis() - t);
        } catch (Exception e) {
            return new TestResult(false, e.getMessage(), System.currentTimeMillis() - t);
        }
    }

    @Override
    public FetchResult fetch(ConnectorContext ctx, String entityType, String cursor) {
        try {
            int uid = login(ctx);
            int version = Integer.parseInt(String.valueOf(ctx.config().getOrDefault("odooVersion", "16")));
            String since = cursor == null ? "1970-01-01 00:00:00" : LocalDateTime.ofInstant(Instant.parse(cursor), ZoneOffset.UTC).format(ODOO);
            List<Object> domain = new ArrayList<>(List.of(List.of("write_date", ">", since)));
            String model;
            List<String> fields;
            switch (entityType) {
                case "PRODUCT" -> { model = "product.product"; fields = List.of("default_code", "name", "list_price", "weight", "categ_id", "barcode", "uom_id", "write_date"); }
                case "CUSTOMER" -> { model = "res.partner"; fields = List.of("ref", "name", "city", "street", "phone", "email", "partner_latitude", "partner_longitude", "write_date");
                    domain.add(version >= 13 ? List.of("customer_rank", ">", 0) : List.of("customer", "=", true)); }
                case "SUPPLIER" -> { model = "res.partner"; fields = List.of("ref", "name", "city", "country_id", "phone", "email", "write_date");
                    domain.add(version >= 13 ? List.of("supplier_rank", ">", 0) : List.of("supplier", "=", true)); }
                case "STOCK" -> { model = "stock.quant"; fields = List.of("product_id", "location_id", "lot_id", "quantity", "write_date");
                    domain.add(List.of("location_id.usage", "=", "internal")); }
                case "EQUIPMENT" -> { model = "maintenance.equipment"; fields = List.of("serial_no", "name", "category_id", "model", "write_date"); }
                case "WORK_ORDER" -> { model = "maintenance.request"; fields = List.of("name", "equipment_id", "maintenance_type", "stage_id", "priority", "schedule_date", "close_date", "duration", "write_date"); }
                default -> throw new IllegalArgumentException("Unsupported entity " + entityType);
            }
            JsonNode res = call(ctx, "object", "execute_kw", List.of(db(ctx), uid, secret(ctx), model, "search_read", List.of(domain),
                    Map.of("fields", fields, "limit", 500, "order", "write_date asc")));
            List<ExternalRecord> out = new ArrayList<>();
            String next = cursor;
            for (JsonNode n : res) {
                Instant updated = LocalDateTime.parse(n.path("write_date").asText(), ODOO).toInstant(ZoneOffset.UTC);
                out.add(new ExternalRecord(model + ":" + n.path("id").asText(), updated, canonical(entityType, n)));
                next = updated.toString();
            }
            return new FetchResult(out, next);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Odoo fetch failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> canonical(String entity, JsonNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (entity) {
            case "PRODUCT" -> {
                m.put("sku", n.path("default_code").asText(null));
                m.put("name", n.path("name").asText());
                m.put("unitPrice", n.path("list_price").asDouble());
                m.put("unitWeightKg", n.path("weight").asDouble());
                m.put("category", n.path("categ_id").path(1).asText(null));
                m.put("barcode", n.path("barcode").isTextual() ? n.path("barcode").asText() : null);
            }
            case "CUSTOMER", "SUPPLIER" -> {
                m.put("code", n.path("ref").isTextual() ? n.path("ref").asText() : "ODOO-" + n.path("id").asText());
                m.put("name", n.path("name").asText());
                m.put("city", n.path("city").isTextual() ? n.path("city").asText() : null);
                m.put("address", n.path("street").isTextual() ? n.path("street").asText() : null);
                m.put("phone", n.path("phone").isTextual() ? n.path("phone").asText() : null);
                m.put("email", n.path("email").isTextual() ? n.path("email").asText() : null);
                if (n.has("partner_latitude")) { m.put("latitude", n.path("partner_latitude").asDouble()); m.put("longitude", n.path("partner_longitude").asDouble()); }
                if (n.has("country_id")) m.put("country", n.path("country_id").path(1).asText(null));
            }
            case "STOCK" -> {
                m.put("sku", n.path("product_id").path(1).asText().replaceAll("^\\[(.*?)\\].*", "$1"));
                m.put("warehouseCode", n.path("location_id").path(1).asText().split("/")[0]);
                m.put("batchNumber", n.path("lot_id").isArray() ? n.path("lot_id").path(1).asText() : null);
                m.put("quantity", n.path("quantity").asInt());
            }
            case "EQUIPMENT" -> {
                m.put("code", n.path("serial_no").isTextual() ? n.path("serial_no").asText() : "ODOO-EQ-" + n.path("id").asText());
                m.put("name", n.path("name").asText());
                m.put("category", n.path("category_id").path(1).asText(null));
                m.put("model", n.path("model").isTextual() ? n.path("model").asText() : null);
            }
            case "WORK_ORDER" -> {
                m.put("reference", "ODOO-MR-" + n.path("id").asText());
                m.put("description", n.path("name").asText());
                m.put("equipmentCode", n.path("equipment_id").path(1).asText(null));
                m.put("type", "preventive".equals(n.path("maintenance_type").asText()) ? "PREVENTIVE" : "CORRECTIVE");
                m.put("status", n.path("close_date").isTextual() ? "COMPLETED" : "PLANNED");
                m.put("plannedDate", n.path("schedule_date").isTextual() ? n.path("schedule_date").asText().substring(0, 10) : null);
                m.put("completedDate", n.path("close_date").isTextual() ? n.path("close_date").asText().substring(0, 10) : null);
                m.put("downtimeHours", n.path("duration").asDouble());
            }
            default -> { }
        }
        return m;
    }

    private int login(ConnectorContext ctx) throws Exception {
        JsonNode r = call(ctx, "common", "login", List.of(db(ctx), ctx.credentials().getOrDefault("username", ""), secret(ctx)));
        return r.isInt() ? r.asInt() : 0;
    }

    private JsonNode call(ConnectorContext ctx, String service, String method, List<Object> args) throws Exception {
        Map<String, Object> body = Map.of("jsonrpc", "2.0", "method", "call", "id", 1,
                "params", Map.of("service", service, "method", method, "args", args));
        JsonNode r = HttpSupport.postJson(ctx.baseUrl().replaceAll("/$", "") + "/jsonrpc", body, Map.of());
        if (r.has("error")) throw new IllegalStateException("Odoo error: " + r.path("error").path("data").path("message").asText(r.path("error").toString()));
        return r.path("result");
    }

    private static String db(ConnectorContext ctx) {
        return String.valueOf(ctx.config().getOrDefault("database", ""));
    }

    private static String secret(ConnectorContext ctx) {
        return ctx.credentials().getOrDefault("apiKey", ctx.credentials().getOrDefault("password", ""));
    }
}
