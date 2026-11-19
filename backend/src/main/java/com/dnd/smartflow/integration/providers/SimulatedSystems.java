package com.dnd.smartflow.integration.providers;

import com.dnd.smartflow.assets.domain.Equipment;
import com.dnd.smartflow.assets.domain.EquipmentRepository;
import com.dnd.smartflow.catalog.domain.*;
import com.dnd.smartflow.fleet.domain.Vehicle;
import com.dnd.smartflow.fleet.domain.VehicleRepository;
import com.dnd.smartflow.integration.spi.ConnectorProvider;
import com.dnd.smartflow.inventory.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * DEMO / SIMULATED external systems. They emulate what a real SAP, Odoo, CMMS, GPS tracker or IoT platform
 * would send, derived from the tenant's own reference data so that synchronized records are coherent.
 * They never claim to be live systems: every record carries {@code _simulated=true} and connectors using
 * them are flagged SIMULATED in the UI. A small share of records is deliberately invalid to demonstrate
 * validation, failed-record handling and retries.
 */
public final class SimulatedSystems {
    private SimulatedSystems() {
    }

    static Random rnd(String cursor, String salt) {
        return new Random(Objects.hash(cursor == null ? "init" : cursor, salt, Instant.now().getEpochSecond() / 60));
    }

    static ConnectorProvider.TestResult ok(String what) {
        return new ConnectorProvider.TestResult(true, "SIMULATED " + what + " reachable (demo connector, no external system contacted)", 12);
    }

    static Map<String, Object> mark(Map<String, Object> m) {
        m.put("_simulated", true);
        return m;
    }

    @Component
    @RequiredArgsConstructor
    public static class Erp implements ConnectorProvider {
        private final ProductRepository products;
        private final CustomerRepository customers;
        private final SupplierRepository suppliers;
        private final WarehouseRepository warehouses;
        private final BatchRepository batches;
        private final StockLevelRepository levels;

        @Override public String code() { return "SIM_ERP"; }
        @Override public String label() { return "Simulated ERP (SAP S/4HANA / Odoo demo)"; }
        @Override public String category() { return "ERP"; }
        @Override public boolean simulated() { return true; }
        @Override public List<String> supportedEntities() { return List.of("PRODUCT", "CUSTOMER", "SUPPLIER", "STOCK"); }
        @Override public List<String> configKeys() { return List.of("flavour (SAP or ODOO)"); }
        @Override public TestResult test(ConnectorContext ctx) { return ok("ERP"); }

        @Override
        public FetchResult fetch(ConnectorContext ctx, String entityType, String cursor) {
            String prefix = "ODOO".equals(ctx.config().get("flavour")) ? "ODOO" : "SAP";
            Random r = rnd(cursor, entityType);
            Instant now = Instant.now();
            List<ExternalRecord> out = new ArrayList<>();
            switch (entityType) {
                case "PRODUCT" -> {
                    List<Product> all = products.findAll();
                    Collections.shuffle(all, r);
                    for (Product p : all.stream().limit(6).toList()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("sku", p.getSku());
                        m.put("name", p.getName());
                        m.put("category", p.getCategory());
                        m.put("brand", p.getBrand());
                        m.put("unit", p.getUnit());
                        m.put("unitWeightKg", p.getUnitWeightKg());
                        double base = p.getUnitPrice() == null ? 100 : p.getUnitPrice().doubleValue();
                        m.put("unitPrice", Math.round(base * (1 + (r.nextDouble() - 0.5) * 0.04) * 100) / 100.0);
                        if (r.nextInt(20) == 0) m.put("unitPrice", -1); // invalid on purpose
                        out.add(new ExternalRecord(prefix + "-MAT-" + p.getSku(), now, mark(m)));
                    }
                    if (r.nextInt(3) == 0) {
                        int n = 900 + r.nextInt(99);
                        out.add(new ExternalRecord(prefix + "-MAT-NEW" + n, now, mark(new LinkedHashMap<>(Map.of("sku", "ATL-NEW-" + n,
                                "name", "Nouveau produit ERP " + n, "category", "Biscuits", "brand", "Atlas", "unit", "CARTON", "unitWeightKg", 6.0, "unitPrice", 980.0)))));
                    }
                }
                case "CUSTOMER" -> customers.findAll().stream().limit(4 + r.nextInt(4)).forEach(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", c.getCode());
                    m.put("name", c.getName());
                    m.put("type", c.getType());
                    m.put("city", c.getCity());
                    m.put("region", c.getRegion());
                    m.put("phone", c.getPhone());
                    m.put("latitude", c.getLatitude());
                    m.put("longitude", c.getLongitude());
                    out.add(new ExternalRecord(prefix + "-BP-" + c.getCode(), now, mark(m)));
                });
                case "SUPPLIER" -> suppliers.findAll().stream().limit(3).forEach(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", s.getCode());
                    m.put("name", s.getName());
                    m.put("category", s.getCategory());
                    m.put("country", s.getCountry());
                    m.put("city", s.getCity());
                    out.add(new ExternalRecord(prefix + "-SUP-" + s.getCode(), now, mark(m)));
                });
                case "STOCK" -> {
                    Map<UUID, Warehouse> wh = new HashMap<>();
                    warehouses.findAll().forEach(w -> wh.put(w.getId(), w));
                    Map<UUID, Product> pr = new HashMap<>();
                    products.findAll().forEach(p -> pr.put(p.getId(), p));
                    Map<UUID, Batch> ba = new HashMap<>();
                    batches.findAll().forEach(b -> ba.put(b.getId(), b));
                    List<StockLevel> lv = new ArrayList<>(levels.findAll());
                    Collections.shuffle(lv, r);
                    for (StockLevel l : lv.stream().filter(x -> x.getQuantity() > 50).limit(8).toList()) {
                        Batch b = l.getBatchId() == null ? null : ba.get(l.getBatchId());
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("sku", pr.get(l.getProductId()).getSku());
                        m.put("warehouseCode", wh.get(l.getWarehouseId()).getCode());
                        m.put("batchNumber", b == null ? null : b.getBatchNumber());
                        m.put("quantity", Math.max(0, l.getQuantity() + r.nextInt(41) - 20));
                        out.add(new ExternalRecord(prefix + "-STK-" + m.get("sku") + "-" + m.get("warehouseCode") + "-" + m.get("batchNumber"), now, mark(m)));
                    }
                    if (r.nextInt(2) == 0 && !pr.isEmpty()) {
                        Product any = pr.values().iterator().next();
                        out.add(new ExternalRecord(prefix + "-STK-" + any.getSku() + "-WH-BLIDA", now, mark(new LinkedHashMap<>(Map.of(
                                "sku", any.getSku(), "warehouseCode", "WH-BLIDA", "quantity", 120)))));
                    }
                }
                default -> throw new IllegalArgumentException("Unsupported entity " + entityType);
            }
            return new FetchResult(out, now.toString());
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class Cmms implements ConnectorProvider {
        private final EquipmentRepository equipment;

        @Override public String code() { return "SIM_CMMS"; }
        @Override public String label() { return "Simulated CMMS / GMAO (SAP PM / Odoo Maintenance demo)"; }
        @Override public String category() { return "CMMS"; }
        @Override public boolean simulated() { return true; }
        @Override public List<String> supportedEntities() { return List.of("EQUIPMENT", "WORK_ORDER"); }
        @Override public List<String> configKeys() { return List.of(); }
        @Override public TestResult test(ConnectorContext ctx) { return ok("CMMS"); }

        @Override
        public FetchResult fetch(ConnectorContext ctx, String entityType, String cursor) {
            Random r = rnd(cursor, entityType);
            Instant now = Instant.now();
            List<Equipment> all = new ArrayList<>(equipment.findAll());
            Collections.shuffle(all, r);
            List<ExternalRecord> out = new ArrayList<>();
            for (Equipment e : all.stream().limit(4).toList()) {
                if ("EQUIPMENT".equals(entityType)) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("code", e.getCode());
                    m.put("name", e.getName());
                    m.put("category", e.getCategory());
                    m.put("runningHours", Optional.ofNullable(e.getRunningHours()).orElse(0) + 8 + r.nextInt(40));
                    m.put("status", r.nextInt(25) == 0 ? "MAINTENANCE" : e.getStatus());
                    out.add(new ExternalRecord("CMMS-EQ-" + e.getCode(), now, mark(m)));
                } else {
                    boolean corrective = r.nextInt(4) == 0;
                    Map<String, Object> m = new LinkedHashMap<>();
                    String ref = "CMMS-WO-" + (100000 + r.nextInt(899999));
                    m.put("reference", ref);
                    m.put("equipmentCode", e.getCode());
                    m.put("type", corrective ? "CORRECTIVE" : "PREVENTIVE");
                    m.put("status", r.nextBoolean() ? "COMPLETED" : "PLANNED");
                    m.put("priority", corrective ? "HIGH" : "MEDIUM");
                    m.put("description", corrective ? "Breakdown repair reported by CMMS" : "Planned preventive maintenance");
                    m.put("plannedDate", LocalDate.now().plusDays(r.nextInt(10) - 3).toString());
                    if ("COMPLETED".equals(m.get("status"))) {
                        m.put("completedDate", LocalDate.now().toString());
                        m.put("downtimeHours", corrective ? 2 + r.nextInt(10) : 1 + r.nextInt(3));
                    }
                    m.put("technician", List.of("K. Benali", "S. Haddad", "M. Boudiaf", "R. Cherif").get(r.nextInt(4)));
                    out.add(new ExternalRecord(ref, now, mark(m)));
                }
            }
            return new FetchResult(out, now.toString());
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class Gps implements ConnectorProvider {
        private final VehicleRepository vehicles;

        @Override public String code() { return "SIM_GPS"; }
        @Override public String label() { return "Simulated GPS fleet tracking"; }
        @Override public String category() { return "GPS"; }
        @Override public boolean simulated() { return true; }
        @Override public List<String> supportedEntities() { return List.of("VEHICLE_POSITION"); }
        @Override public List<String> configKeys() { return List.of(); }
        @Override public TestResult test(ConnectorContext ctx) { return ok("GPS gateway"); }

        @Override
        public FetchResult fetch(ConnectorContext ctx, String entityType, String cursor) {
            Random r = rnd(cursor, entityType);
            Instant now = Instant.now();
            List<ExternalRecord> out = new ArrayList<>();
            for (Vehicle v : vehicles.findAll()) {
                if (v.getCurrentLatitude() == null || "OUT_OF_SERVICE".equals(v.getStatus())) continue;
                boolean moving = "ON_ROUTE".equals(v.getStatus());
                double step = moving ? 0.01 + r.nextDouble() * 0.02 : 0;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("plateNumber", v.getPlateNumber());
                m.put("latitude", Math.round((v.getCurrentLatitude() + (r.nextDouble() - 0.5) * step) * 1e5) / 1e5);
                m.put("longitude", Math.round((v.getCurrentLongitude() + (r.nextDouble() - 0.5) * step) * 1e5) / 1e5);
                m.put("speedKmh", moving ? 35 + r.nextInt(55) : 0);
                m.put("recordedAt", now.toString());
                out.add(new ExternalRecord("GPS-" + v.getPlateNumber() + "-" + now.getEpochSecond(), now, mark(m)));
            }
            return new FetchResult(out, now.toString());
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class Iot implements ConnectorProvider {
        private final WarehouseRepository warehouses;

        @Override public String code() { return "SIM_IOT"; }
        @Override public String label() { return "Simulated IoT sensors (temperature / humidity)"; }
        @Override public String category() { return "IOT"; }
        @Override public boolean simulated() { return true; }
        @Override public List<String> supportedEntities() { return List.of("SENSOR_READING"); }
        @Override public List<String> configKeys() { return List.of(); }
        @Override public TestResult test(ConnectorContext ctx) { return ok("IoT broker"); }

        @Override
        public FetchResult fetch(ConnectorContext ctx, String entityType, String cursor) {
            Random r = rnd(cursor, entityType);
            Instant now = Instant.now();
            List<ExternalRecord> out = new ArrayList<>();
            for (Warehouse w : warehouses.findAll()) {
                boolean cold = Boolean.TRUE.equals(w.getTemperatureControlled());
                double target = cold ? (Optional.ofNullable(w.getMinTemp()).orElse(2.0) + Optional.ofNullable(w.getMaxTemp()).orElse(8.0)) / 2 : 21;
                double temp = target + r.nextGaussian() * (cold ? 1.2 : 2.0) + (r.nextInt(30) == 0 ? 6 : 0);
                out.add(new ExternalRecord("IOT-T-" + w.getCode() + "-" + now.getEpochSecond(), now, mark(new LinkedHashMap<>(Map.of(
                        "sensorId", "TH-" + w.getCode() + "-01", "readingType", "TEMPERATURE", "warehouseCode", w.getCode(),
                        "value", Math.round(temp * 10) / 10.0, "unit", "°C", "recordedAt", now.toString())))));
                out.add(new ExternalRecord("IOT-H-" + w.getCode() + "-" + now.getEpochSecond(), now, mark(new LinkedHashMap<>(Map.of(
                        "sensorId", "TH-" + w.getCode() + "-01", "readingType", "HUMIDITY", "warehouseCode", w.getCode(),
                        "value", Math.round((50 + r.nextGaussian() * 6) * 10) / 10.0, "unit", "%", "recordedAt", now.toString())))));
            }
            return new FetchResult(out, now.toString());
        }
    }
}
