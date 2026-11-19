package com.dnd.smartflow.integration.sync;

import com.dnd.smartflow.assets.domain.*;
import com.dnd.smartflow.catalog.domain.*;
import com.dnd.smartflow.common.persistence.TenantEntity;
import com.dnd.smartflow.common.security.Role;
import com.dnd.smartflow.fleet.domain.*;
import com.dnd.smartflow.inventory.domain.*;
import com.dnd.smartflow.inventory.service.StockService;
import com.dnd.smartflow.notification.NotificationService;
import com.dnd.smartflow.qhse.domain.EnvironmentalReading;
import com.dnd.smartflow.qhse.domain.EnvironmentalReadingRepository;
import com.dnd.smartflow.qhse.web.EnvironmentalReadingController;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Maps canonical external records onto platform entities with validation. Natural keys (SKU, codes,
 * plate numbers) are used to match records that were not synchronized before.
 */
@Component
@RequiredArgsConstructor
public class RecordMapper {
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final WarehouseRepository warehouses;
    private final BatchRepository batches;
    private final StockLevelRepository levels;
    private final StockService stock;
    private final EquipmentRepository equipment;
    private final MaintenanceWorkOrderRepository workOrders;
    private final VehicleRepository vehicles;
    private final VehiclePositionRepository positions;
    private final EnvironmentalReadingRepository readings;
    private final NotificationService notifications;

    public static class ValidationException extends RuntimeException {
        public ValidationException(String m) {
            super(m);
        }
    }

    public record Outcome(UUID localId, boolean created, Instant localUpdatedAt) {
    }

    /** Finds the local record previously linked or matched by natural key (for conflict detection). */
    public Optional<? extends TenantEntity> findExisting(String entityType, UUID mappedId, Map<String, Object> p) {
        return switch (entityType) {
            case "PRODUCT" -> mappedId != null ? products.findById(mappedId) : products.findOne((r, q, cb) -> cb.equal(r.get("sku"), str(p, "sku")));
            case "CUSTOMER" -> mappedId != null ? customers.findById(mappedId) : customers.findOne((r, q, cb) -> cb.equal(r.get("code"), str(p, "code")));
            case "SUPPLIER" -> mappedId != null ? suppliers.findById(mappedId) : suppliers.findOne((r, q, cb) -> cb.equal(r.get("code"), str(p, "code")));
            case "EQUIPMENT" -> mappedId != null ? equipment.findById(mappedId) : equipment.findOne((r, q, cb) -> cb.equal(r.get("code"), str(p, "code")));
            default -> Optional.empty();
        };
    }

    public Outcome apply(String entityType, UUID mappedId, Map<String, Object> p) {
        return switch (entityType) {
            case "PRODUCT" -> product(mappedId, p);
            case "CUSTOMER" -> customer(mappedId, p);
            case "SUPPLIER" -> supplier(mappedId, p);
            case "STOCK" -> stock(p);
            case "EQUIPMENT" -> equipment(mappedId, p);
            case "WORK_ORDER" -> workOrder(p);
            case "VEHICLE_POSITION" -> position(p);
            case "SENSOR_READING" -> reading(p);
            default -> throw new ValidationException("Unsupported entity type " + entityType);
        };
    }

    private Outcome product(UUID id, Map<String, Object> p) {
        require(p, "sku", "name");
        Product e = findExisting("PRODUCT", id, p).map(x -> (Product) x).orElse(null);
        boolean created = e == null;
        if (created) {
            e = new Product();
            e.setActive(true);
        }
        e.setSku(str(p, "sku"));
        e.setName(str(p, "name"));
        if (p.get("category") != null) e.setCategory(str(p, "category"));
        if (p.get("brand") != null) e.setBrand(str(p, "brand"));
        if (p.get("unit") != null) e.setUnit(str(p, "unit"));
        if (p.get("unitWeightKg") != null) e.setUnitWeightKg(dbl(p, "unitWeightKg"));
        if (p.get("barcode") != null) e.setBarcode(str(p, "barcode"));
        if (p.get("unitPrice") != null) {
            double price = dbl(p, "unitPrice");
            if (price < 0) throw new ValidationException("unitPrice must be >= 0 (received " + price + ")");
            e.setUnitPrice(BigDecimal.valueOf(price));
        }
        e = products.save(e);
        return new Outcome(e.getId(), created, e.getUpdatedAt());
    }

    private Outcome customer(UUID id, Map<String, Object> p) {
        require(p, "code", "name");
        Customer e = findExisting("CUSTOMER", id, p).map(x -> (Customer) x).orElse(null);
        boolean created = e == null;
        if (created) {
            e = new Customer();
            e.setActive(true);
            e.setType("RETAILER");
        }
        e.setCode(str(p, "code"));
        e.setName(str(p, "name"));
        if (p.get("type") != null) e.setType(str(p, "type"));
        if (p.get("city") != null) e.setCity(str(p, "city"));
        if (p.get("region") != null) e.setRegion(str(p, "region"));
        if (p.get("address") != null) e.setAddress(str(p, "address"));
        if (p.get("phone") != null) e.setPhone(str(p, "phone"));
        if (p.get("email") != null) e.setEmail(str(p, "email"));
        if (p.get("latitude") != null && dbl(p, "latitude") != 0) {
            e.setLatitude(dbl(p, "latitude"));
            e.setLongitude(dbl(p, "longitude"));
        }
        e = customers.save(e);
        return new Outcome(e.getId(), created, e.getUpdatedAt());
    }

    private Outcome supplier(UUID id, Map<String, Object> p) {
        require(p, "code", "name");
        Supplier e = findExisting("SUPPLIER", id, p).map(x -> (Supplier) x).orElse(null);
        boolean created = e == null;
        if (created) {
            e = new Supplier();
            e.setStatus("PENDING");
        }
        e.setCode(str(p, "code"));
        e.setName(str(p, "name"));
        if (p.get("category") != null) e.setCategory(str(p, "category"));
        if (p.get("country") != null) e.setCountry(str(p, "country"));
        if (p.get("city") != null) e.setCity(str(p, "city"));
        if (p.get("email") != null) e.setEmail(str(p, "email"));
        if (p.get("phone") != null) e.setPhone(str(p, "phone"));
        e = suppliers.save(e);
        return new Outcome(e.getId(), created, e.getUpdatedAt());
    }

    /** Stock snapshots are applied as traceable ADJUSTMENT movements for the difference. */
    private Outcome stock(Map<String, Object> p) {
        require(p, "sku", "warehouseCode", "quantity");
        Product prod = products.findOne((r, q, cb) -> cb.equal(r.get("sku"), str(p, "sku")))
                .orElseThrow(() -> new ValidationException("Unknown product SKU " + str(p, "sku")));
        Warehouse wh = warehouses.findOne((r, q, cb) -> cb.equal(r.get("code"), str(p, "warehouseCode")))
                .orElseThrow(() -> new ValidationException("Unknown warehouse code " + str(p, "warehouseCode") + " — create it or fix the ERP mapping"));
        UUID batchId = null;
        if (p.get("batchNumber") != null && !str(p, "batchNumber").isBlank()) {
            batchId = batches.findOne((r, q, cb) -> cb.equal(r.get("batchNumber"), str(p, "batchNumber")))
                    .orElseThrow(() -> new ValidationException("Unknown batch " + str(p, "batchNumber"))).getId();
        }
        int target = (int) dbl(p, "quantity");
        if (target < 0) throw new ValidationException("Negative stock quantity");
        UUID b = batchId;
        int current = levels.findOne((r, q, cb) -> cb.and(cb.equal(r.get("warehouseId"), wh.getId()), cb.equal(r.get("productId"), prod.getId()),
                b == null ? cb.isNull(r.get("batchId")) : cb.equal(r.get("batchId"), b))).map(StockLevel::getQuantity).orElse(0);
        int delta = target - current;
        if (delta != 0) {
            StockMovement m = new StockMovement();
            m.setType("ADJUSTMENT");
            m.setProductId(prod.getId());
            m.setBatchId(batchId);
            if (delta > 0) m.setToWarehouseId(wh.getId());
            else m.setFromWarehouseId(wh.getId());
            m.setQuantity(Math.abs(delta));
            m.setReferenceType("IntegrationSync");
            m.setNotes("Stock alignment from external system");
            stock.post(m);
        }
        return new Outcome(prod.getId(), false, null);
    }

    private Outcome equipment(UUID id, Map<String, Object> p) {
        require(p, "code", "name");
        Equipment e = findExisting("EQUIPMENT", id, p).map(x -> (Equipment) x).orElse(null);
        boolean created = e == null;
        if (created) {
            e = new Equipment();
            e.setStatus("OPERATIONAL");
            e.setCriticality("MEDIUM");
        }
        e.setCode(str(p, "code"));
        e.setName(str(p, "name"));
        if (p.get("category") != null) e.setCategory(str(p, "category"));
        if (p.get("manufacturer") != null) e.setManufacturer(str(p, "manufacturer"));
        if (p.get("model") != null) e.setModel(str(p, "model"));
        if (p.get("serialNumber") != null) e.setSerialNumber(str(p, "serialNumber"));
        if (p.get("runningHours") != null) e.setRunningHours((int) dbl(p, "runningHours"));
        if (p.get("status") != null) {
            String s = str(p, "status");
            if (!Set.of("OPERATIONAL", "MAINTENANCE", "DOWN", "RETIRED").contains(s)) throw new ValidationException("Invalid equipment status " + s);
            e.setStatus(s);
        }
        e = equipment.save(e);
        return new Outcome(e.getId(), created, e.getUpdatedAt());
    }

    private Outcome workOrder(Map<String, Object> p) {
        require(p, "reference", "equipmentCode", "type");
        Equipment eq = equipment.findOne((r, q, cb) -> cb.equal(r.get("code"), str(p, "equipmentCode")))
                .orElseThrow(() -> new ValidationException("Unknown equipment " + str(p, "equipmentCode")));
        MaintenanceWorkOrder w = workOrders.findOne((r, q, cb) -> cb.equal(r.get("reference"), str(p, "reference"))).orElse(null);
        boolean created = w == null;
        if (created) w = new MaintenanceWorkOrder();
        w.setReference(str(p, "reference"));
        w.setEquipmentId(eq.getId());
        w.setType(str(p, "type"));
        w.setStatus(p.get("status") == null ? "PLANNED" : str(p, "status"));
        w.setPriority(p.get("priority") == null ? "MEDIUM" : str(p, "priority"));
        w.setDescription(str(p, "description"));
        if (p.get("plannedDate") != null) w.setPlannedDate(LocalDate.parse(str(p, "plannedDate")));
        if (p.get("completedDate") != null) w.setCompletedDate(LocalDate.parse(str(p, "completedDate")));
        if (p.get("downtimeHours") != null) w.setDowntimeHours(dbl(p, "downtimeHours"));
        if (p.get("technician") != null) w.setTechnician(str(p, "technician"));
        w.setSource("CMMS");
        w = workOrders.save(w);
        if ("COMPLETED".equals(w.getStatus()) && w.getCompletedDate() != null) {
            if ("PREVENTIVE".equals(w.getType())) {
                eq.setLastMaintenanceDate(w.getCompletedDate());
                eq.setNextMaintenanceDate(w.getCompletedDate().plusDays(90));
            } else {
                eq.setFailureCount(Optional.ofNullable(eq.getFailureCount()).orElse(0) + (created ? 1 : 0));
            }
            equipment.save(eq);
        }
        return new Outcome(w.getId(), created, w.getUpdatedAt());
    }

    private Outcome position(Map<String, Object> p) {
        require(p, "plateNumber", "latitude", "longitude");
        Vehicle v = vehicles.findOne((r, q, cb) -> cb.equal(r.get("plateNumber"), str(p, "plateNumber")))
                .orElseThrow(() -> new ValidationException("Unknown vehicle " + str(p, "plateNumber")));
        double lat = dbl(p, "latitude"), lon = dbl(p, "longitude");
        if (lat < 18 || lat > 38 || lon < -9 || lon > 12) throw new ValidationException("GPS position outside the operating area");
        Instant at = p.get("recordedAt") == null ? Instant.now() : Instant.parse(str(p, "recordedAt"));
        VehiclePosition pos = new VehiclePosition();
        pos.setVehicleId(v.getId());
        pos.setLatitude(lat);
        pos.setLongitude(lon);
        pos.setSpeedKmh(p.get("speedKmh") == null ? null : dbl(p, "speedKmh"));
        pos.setRecordedAt(at);
        pos.setSource("GPS");
        positions.save(pos);
        v.setCurrentLatitude(lat);
        v.setCurrentLongitude(lon);
        v.setLastGpsAt(at);
        vehicles.save(v);
        return new Outcome(pos.getId(), true, null);
    }

    private Outcome reading(Map<String, Object> p) {
        require(p, "readingType", "value");
        EnvironmentalReading r = new EnvironmentalReading();
        r.setReadingType(str(p, "readingType"));
        r.setReadingValue(dbl(p, "value"));
        r.setUnit(str(p, "unit"));
        r.setSensorId(str(p, "sensorId"));
        r.setSource("IOT");
        r.setRecordedAt(p.get("recordedAt") == null ? Instant.now() : Instant.parse(str(p, "recordedAt")));
        if (p.get("warehouseCode") != null) {
            Warehouse w = warehouses.findOne((x, q, cb) -> cb.equal(x.get("code"), str(p, "warehouseCode")))
                    .orElseThrow(() -> new ValidationException("Unknown warehouse " + str(p, "warehouseCode")));
            r.setWarehouseId(w.getId());
            r.setSiteId(w.getSiteId());
            r.setLocation(w.getName());
            if ("TEMPERATURE".equals(r.getReadingType())) {
                if (Boolean.TRUE.equals(w.getTemperatureControlled())) {
                    r.setMinThreshold(w.getMinTemp());
                    r.setMaxThreshold(w.getMaxTemp());
                } else {
                    r.setMaxThreshold(25.0);
                }
            } else if ("HUMIDITY".equals(r.getReadingType())) {
                r.setMinThreshold(30.0);
                r.setMaxThreshold(65.0);
            }
        }
        EnvironmentalReadingController.evaluate(r);
        r = readings.save(r);
        if (Boolean.FALSE.equals(r.getWithinLimits())) {
            notifications.notifyRoles(new NotificationService.Message("READING_OUT_OF_LIMITS", "Sensor alert: " + r.getReadingType().toLowerCase()
                    + " " + r.getReadingValue() + " " + r.getUnit(), Objects.toString(r.getLocation(), "") + " (" + r.getSensorId() + ")", "WARNING",
                    "HYGIENE", "EnvironmentalReading", r.getId()), Role.HYGIENE_MANAGER, Role.WAREHOUSE_MANAGER);
        }
        return new Outcome(r.getId(), true, null);
    }

    private static void require(Map<String, Object> p, String... keys) {
        for (String k : keys) {
            Object v = p.get(k);
            if (v == null || (v instanceof String s && s.isBlank())) throw new ValidationException("Missing required field '" + k + "'");
        }
    }

    private static String str(Map<String, Object> p, String k) {
        Object v = p.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private static double dbl(Map<String, Object> p, String k) {
        Object v = p.get(k);
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new ValidationException("Field '" + k + "' is not numeric");
        }
    }
}
