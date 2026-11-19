package com.dnd.smartflow.analytics;

import com.dnd.smartflow.assets.domain.*;
import com.dnd.smartflow.catalog.domain.*;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.fleet.domain.*;
import com.dnd.smartflow.inventory.domain.*;
import com.dnd.smartflow.qhse.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Operational alerts computed live from the current data (never stored, so they are always up to date). */
@Service
@RequiredArgsConstructor
public class AlertService {
    private final ProductRepository products;
    private final StockLevelRepository levels;
    private final BatchRepository batches;
    private final CapaActionRepository capas;
    private final NonConformityRepository ncs;
    private final CertificationRepository certifications;
    private final EnvironmentalReadingRepository readings;
    private final EquipmentRepository equipment;
    private final VehicleRepository vehicles;
    private final QhseDocumentRepository documents;
    private final IncidentRepository incidents;

    public record Alert(String severity, String category, String module, String title, String detail, String entityType, UUID entityId, String route) {
    }

    @Transactional(readOnly = true)
    public List<Alert> alerts() {
        CurrentUser u = CurrentUser.require();
        List<Alert> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        if (u.can("inventory", "read")) {
            Map<UUID, Integer> stock = new HashMap<>();
            Map<UUID, Batch> batchById = new HashMap<>();
            batches.findAll().forEach(b -> batchById.put(b.getId(), b));
            for (StockLevel l : levels.findAll()) {
                Batch b = l.getBatchId() == null ? null : batchById.get(l.getBatchId());
                if (b == null || "RELEASED".equals(b.getStatus())) stock.merge(l.getProductId(), l.getQuantity() - Optional.ofNullable(l.getReservedQuantity()).orElse(0), Integer::sum);
            }
            for (Product p : products.findAll()) {
                if (Boolean.FALSE.equals(p.getActive()) || p.getReorderPoint() == null) continue;
                int s = stock.getOrDefault(p.getId(), 0);
                if (s <= p.getReorderPoint()) {
                    out.add(new Alert(s <= Optional.ofNullable(p.getMinStock()).orElse(0) ? "CRITICAL" : "WARNING", "STOCK", "inventory",
                            "Low stock: " + p.getName(), s + " units available (reorder point " + p.getReorderPoint() + ")", "Product", p.getId(), "/inventory"));
                }
            }
            Map<UUID, Integer> byBatch = new HashMap<>();
            levels.findAll().forEach(l -> { if (l.getBatchId() != null) byBatch.merge(l.getBatchId(), l.getQuantity(), Integer::sum); });
            for (Batch b : batchById.values()) {
                if (b.getExpiryDate() == null || !"RELEASED".equals(b.getStatus())) continue;
                long days = ChronoUnit.DAYS.between(today, b.getExpiryDate());
                if (days <= 14 && byBatch.getOrDefault(b.getId(), 0) > 0) {
                    out.add(new Alert(days < 0 ? "CRITICAL" : "WARNING", "EXPIRY", "inventory", "Batch " + b.getBatchNumber() + (days < 0 ? " expired" : " expiring"),
                            byBatch.get(b.getId()) + " units in stock, expiry " + b.getExpiryDate(), "Batch", b.getId(), "/traceability/" + b.getId()));
                }
            }
        }
        if (u.can("capa", "read")) {
            for (CapaAction c : capas.findAll()) {
                if (!u.canAccessSite(c.getSiteId())) continue;
                if (c.getDueDate() != null && c.getDueDate().isBefore(today) && !Set.of("CLOSED", "CANCELLED", "PENDING_VERIFICATION").contains(c.getStatus())) {
                    out.add(new Alert(ChronoUnit.DAYS.between(c.getDueDate(), today) > 14 ? "CRITICAL" : "WARNING", "CAPA", "capa",
                            "Overdue action " + c.getReference(), c.getTitle() + " — due " + c.getDueDate(), "CapaAction", c.getId(), "/capa/" + c.getId()));
                }
            }
        }
        if (u.can("nc", "read")) {
            for (NonConformity n : ncs.findAll()) {
                if (Set.of("CLOSED", "CANCELLED").contains(n.getStatus()) || !u.canAccessSite(n.getSiteId())) continue;
                if ("CRITICAL".equals(n.getSeverity())) {
                    out.add(new Alert("CRITICAL", "QUALITY", "nc", "Critical NC open: " + n.getReference(), n.getTitle(), "NonConformity", n.getId(), "/non-conformities/" + n.getId()));
                } else if (n.getDueDate() != null && n.getDueDate().isBefore(today)) {
                    out.add(new Alert("WARNING", "QUALITY", "nc", "Overdue NC " + n.getReference(), n.getTitle(), "NonConformity", n.getId(), "/non-conformities/" + n.getId()));
                }
            }
        }
        if (u.can("incidents", "read")) {
            for (Incident i : incidents.findAll()) {
                if (!u.canAccessSite(i.getSiteId())) continue;
                if ("REPORTED".equals(i.getStatus()) && i.getSeverity() != null && i.getSeverity() >= 4) {
                    out.add(new Alert("CRITICAL", i.getDomain(), "incidents", "Serious incident awaiting investigation: " + i.getReference(), i.getTitle(),
                            "Incident", i.getId(), "/incidents/" + i.getId()));
                }
            }
        }
        if (u.can("training", "read")) {
            for (Certification c : certifications.findAll()) {
                if (c.getExpiryDate() == null || "SUSPENDED".equals(c.getStatus())) continue;
                long days = ChronoUnit.DAYS.between(today, c.getExpiryDate());
                if (days <= 30) {
                    out.add(new Alert(days < 0 ? "CRITICAL" : "WARNING", "TRAINING", "training", (days < 0 ? "Expired: " : "Expiring: ") + c.getName(),
                            "Certification " + Objects.toString(c.getNumber(), "") + " — " + c.getExpiryDate(), "Certification", c.getId(), "/training"));
                }
            }
        }
        if (u.can("environment", "read") || u.can("hygiene", "read")) {
            Instant since = Instant.now().minus(Duration.ofHours(48));
            for (EnvironmentalReading r : readings.findAll((root, q, cb) -> cb.and(cb.isFalse(root.get("withinLimits")), cb.greaterThan(root.get("recordedAt"), since)))) {
                out.add(new Alert("WARNING", "MONITORING", "environment", r.getReadingType().replace('_', ' ') + " out of limits",
                        r.getReadingValue() + " " + Objects.toString(r.getUnit(), "") + " at " + Objects.toString(r.getLocation(), "?"), "EnvironmentalReading", r.getId(), "/environment"));
            }
        }
        if (u.can("assets", "read")) {
            for (Equipment e : equipment.findAll()) {
                if (!u.canAccessSite(e.getSiteId())) continue;
                if ("DOWN".equals(e.getStatus())) {
                    out.add(new Alert("CRITICAL", "MAINTENANCE", "assets", "Equipment down: " + e.getName(), e.getCode(), "Equipment", e.getId(), "/equipment"));
                } else if (e.getNextMaintenanceDate() != null && e.getNextMaintenanceDate().isBefore(today) && !"RETIRED".equals(e.getStatus())) {
                    out.add(new Alert("WARNING", "MAINTENANCE", "assets", "Preventive maintenance overdue: " + e.getName(), "Planned " + e.getNextMaintenanceDate(),
                            "Equipment", e.getId(), "/equipment"));
                }
            }
        }
        if (u.can("fleet", "read")) {
            for (Vehicle v : vehicles.findAll()) {
                if (v.getOdometerKm() != null && v.getNextServiceKm() != null && v.getNextServiceKm() - v.getOdometerKm() < 1000) {
                    out.add(new Alert("WARNING", "FLEET", "fleet", "Vehicle service due: " + v.getPlateNumber(),
                            (v.getNextServiceKm() - v.getOdometerKm()) + " km remaining", "Vehicle", v.getId(), "/fleet"));
                }
            }
        }
        if (u.can("documents", "read")) {
            for (QhseDocument d : documents.findAll()) {
                if ("APPROVED".equals(d.getStatus()) && d.getReviewDate() != null && d.getReviewDate().isBefore(today)) {
                    out.add(new Alert("INFO", "DOCUMENTS", "documents", "Document review overdue: " + d.getReference(), d.getTitle(), "QhseDocument", d.getId(), "/documents"));
                }
            }
        }
        Map<String, Integer> rank = Map.of("CRITICAL", 0, "WARNING", 1, "INFO", 2);
        out.sort(Comparator.comparing(a -> rank.getOrDefault(a.severity(), 3)));
        return out;
    }
}
