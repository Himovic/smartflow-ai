package com.dnd.smartflow.traceability;

import com.dnd.smartflow.catalog.domain.*;
import com.dnd.smartflow.common.crud.ReferenceGenerator;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.security.Role;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.distribution.domain.*;
import com.dnd.smartflow.inventory.domain.*;
import com.dnd.smartflow.notification.NotificationService;
import com.dnd.smartflow.organization.domain.*;
import com.dnd.smartflow.qhse.domain.*;
import com.dnd.smartflow.qhse.service.NcRules;
import com.dnd.smartflow.workflow.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Backward (supplier, raw material, line, site, inspections) and forward (warehouses, orders, deliveries,
 * customers) traceability of a production batch, and the product recall workflow.
 */
@Service
@RequiredArgsConstructor
public class TraceabilityService {
    private final BatchRepository batches;
    private final ProductRepository products;
    private final SupplierRepository suppliers;
    private final SiteRepository sites;
    private final ProductionLineRepository lines;
    private final StockLevelRepository levels;
    private final StockMovementRepository movements;
    private final WarehouseRepository warehouses;
    private final SalesOrderLineRepository orderLines;
    private final SalesOrderRepository orders;
    private final DeliveryRepository deliveries;
    private final CustomerRepository customers;
    private final InspectionRepository inspections;
    private final NonConformityRepository ncs;
    private final ReferenceGenerator references;
    private final WorkflowService workflow;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public Optional<Batch> findByNumber(String batchNumber) {
        return batches.findOne((r, q, cb) -> cb.equal(cb.upper(r.get("batchNumber")), batchNumber.trim().toUpperCase(Locale.ROOT)));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> trace(UUID batchId) {
        Batch b = batches.findById(batchId).orElseThrow(() -> ApiException.notFound("Batch"));
        if (b.getSiteId() != null && !CurrentUser.require().canAccessSite(b.getSiteId())) throw ApiException.notFound("Batch");
        Product p = products.findById(b.getProductId()).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("batch", b);
        out.put("product", p);

        Map<String, Object> backward = new LinkedHashMap<>();
        backward.put("supplier", b.getSupplierId() == null ? null : suppliers.findById(b.getSupplierId()).orElse(null));
        backward.put("site", b.getSiteId() == null ? null : sites.findById(b.getSiteId()).orElse(null));
        backward.put("productionLine", b.getProductionLineId() == null ? null : lines.findById(b.getProductionLineId()).orElse(null));
        backward.put("rawMaterialLots", b.getRawMaterialLots() == null ? List.of() : Arrays.stream(b.getRawMaterialLots().split("[,;]")).map(String::trim).toList());
        backward.put("inspections", inspections.findAll((r, q, cb) -> cb.equal(r.get("batchId"), batchId), Sort.by("scheduledDate")));
        if (b.getSupplierId() != null && b.getProductionDate() != null) {
            backward.put("incomingInspections", inspections.findAll((r, q, cb) -> cb.and(cb.equal(r.get("supplierId"), b.getSupplierId()),
                    cb.equal(r.get("inspectionType"), "INCOMING"),
                    cb.between(r.get("scheduledDate"), b.getProductionDate().minusDays(30), b.getProductionDate())), Sort.by("scheduledDate")));
        }
        out.put("backward", backward);

        Map<UUID, Warehouse> wh = new HashMap<>();
        warehouses.findAll().forEach(w -> wh.put(w.getId(), w));
        List<Map<String, Object>> stock = new ArrayList<>();
        int remaining = 0;
        for (StockLevel l : levels.findAll((r, q, cb) -> cb.equal(r.get("batchId"), batchId))) {
            if (l.getQuantity() <= 0) continue;
            remaining += l.getQuantity();
            Warehouse w = wh.get(l.getWarehouseId());
            stock.add(Map.of("warehouseId", l.getWarehouseId(), "warehouse", w == null ? "?" : w.getName(), "city", w == null || w.getCity() == null ? "" : w.getCity(),
                    "quantity", l.getQuantity(), "reserved", Optional.ofNullable(l.getReservedQuantity()).orElse(0),
                    "latitude", w == null || w.getLatitude() == null ? 0 : w.getLatitude(), "longitude", w == null || w.getLongitude() == null ? 0 : w.getLongitude()));
        }
        List<SalesOrderLine> ol = orderLines.findAll((r, q, cb) -> cb.equal(r.get("batchId"), batchId));
        Set<UUID> orderIds = ol.stream().map(SalesOrderLine::getOrderId).collect(Collectors.toSet());
        Map<UUID, Integer> qtyByOrder = new HashMap<>();
        ol.forEach(l -> qtyByOrder.merge(l.getOrderId(), l.getQuantity(), Integer::sum));
        List<SalesOrder> relatedOrders = orderIds.isEmpty() ? List.of() : orders.findAllById(orderIds);
        List<Delivery> relatedDeliveries = orderIds.isEmpty() ? List.of() : deliveries.findAll((r, q, cb) -> r.get("orderId").in(orderIds));
        Set<UUID> customerIds = relatedOrders.stream().map(SalesOrder::getCustomerId).collect(Collectors.toSet());
        Map<UUID, Customer> cust = new HashMap<>();
        if (!customerIds.isEmpty()) customers.findAllById(customerIds).forEach(c -> cust.put(c.getId(), c));
        List<Map<String, Object>> affectedCustomers = new ArrayList<>();
        Map<UUID, Integer> qtyByCustomer = new HashMap<>();
        relatedOrders.forEach(o -> qtyByCustomer.merge(o.getCustomerId(), qtyByOrder.getOrDefault(o.getId(), 0), Integer::sum));
        qtyByCustomer.forEach((cid, qty) -> {
            Customer c = cust.get(cid);
            if (c == null) return;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("code", c.getCode());
            m.put("name", c.getName());
            m.put("type", c.getType());
            m.put("city", c.getCity());
            m.put("phone", c.getPhone());
            m.put("latitude", c.getLatitude());
            m.put("longitude", c.getLongitude());
            m.put("quantity", qty);
            affectedCustomers.add(m);
        });
        affectedCustomers.sort(Comparator.comparing(m -> -((Integer) m.get("quantity"))));
        int distributed = ol.stream().filter(l -> relatedOrders.stream().anyMatch(o -> o.getId().equals(l.getOrderId())
                && Set.of("SHIPPED", "DELIVERED").contains(o.getStatus()))).mapToInt(SalesOrderLine::getQuantity).sum();

        Map<String, Object> forward = new LinkedHashMap<>();
        forward.put("stock", stock);
        forward.put("orders", relatedOrders);
        forward.put("deliveries", relatedDeliveries);
        forward.put("customers", affectedCustomers);
        forward.put("movements", movements.findAll((r, q, cb) -> cb.equal(r.get("batchId"), batchId), Sort.by("occurredAt")));
        out.put("forward", forward);
        out.put("nonConformities", ncs.findAll((r, q, cb) -> cb.equal(r.get("batchId"), batchId), Sort.by("detectedAt")));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("quantityProduced", b.getQuantityProduced());
        summary.put("remainingInventory", remaining);
        summary.put("warehousesHoldingBatch", stock.size());
        summary.put("distributedQuantity", distributed);
        summary.put("ordersAffected", relatedOrders.size());
        summary.put("deliveriesAffected", relatedDeliveries.size());
        summary.put("customersAffected", affectedCustomers.size());
        summary.put("relatedNonConformities", ((List<?>) out.get("nonConformities")).size());
        out.put("summary", summary);
        return out;
    }

    @Transactional
    public Map<String, Object> recall(UUID batchId, String reason, String recallClass) {
        Batch b = batches.findById(batchId).orElseThrow(() -> ApiException.notFound("Batch"));
        if ("RECALLED".equals(b.getStatus())) throw ApiException.conflict("ALREADY_RECALLED", "Batch is already recalled");
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("REASON_REQUIRED", "A recall reason is required");
        String previous = b.getStatus();
        b.setStatus("RECALLED");
        b.setQualityStatus("REJECTED");
        batches.save(b);
        // Freeze open allocations: draft/confirmed orders lose this batch reservation.
        for (SalesOrderLine l : orderLines.findAll((r, q, cb) -> cb.equal(r.get("batchId"), batchId))) {
            orders.findById(l.getOrderId()).filter(o -> Set.of("CONFIRMED", "PREPARING").contains(o.getStatus())).ifPresent(o -> {
                o.setStatus("DRAFT");
                o.setNotes((o.getNotes() == null ? "" : o.getNotes() + "\n") + "Batch " + b.getBatchNumber() + " recalled — re-allocation required");
                orders.save(o);
                levels.findOne((r, q, cb) -> cb.and(cb.equal(r.get("warehouseId"), o.getWarehouseId()), cb.equal(r.get("batchId"), batchId)))
                        .ifPresent(lvl -> {
                            lvl.setReservedQuantity(Math.max(0, Optional.ofNullable(lvl.getReservedQuantity()).orElse(0) - l.getQuantity()));
                            levels.save(lvl);
                        });
                l.setBatchId(null);
                orderLines.save(l);
            });
        }
        Product p = products.findById(b.getProductId()).orElse(null);
        NonConformity nc = new NonConformity();
        nc.setReference(references.next("NC"));
        nc.setTitle("Product recall — batch " + b.getBatchNumber());
        nc.setDescription("Recall " + Objects.toString(recallClass, "") + " initiated by " + CurrentUser.require().fullName() + ".\nReason: " + reason);
        nc.setType("PRODUCT");
        nc.setCategory("PRODUCT_SAFETY");
        nc.setSeverity("CRITICAL");
        nc.setSource("RECALL");
        nc.setBatchId(batchId);
        nc.setProductId(b.getProductId());
        nc.setSiteId(b.getSiteId());
        nc.setSupplierId(b.getSupplierId());
        nc.setDetectedAt(Instant.now());
        nc.setReportedByUserId(CurrentUser.require().userId());
        nc.setReportedByName(CurrentUser.require().fullName());
        nc.setDueDate(NcRules.defaultDueDate("CRITICAL", LocalDate.now()));
        nc.setStatus(workflow.initialState("NonConformity", nc));
        nc = ncs.save(nc);
        workflow.recordCreation("NonConformity", nc.getId(), nc.getStatus());
        Map<String, Object> impact = trace(batchId);
        notifications.notifyRoles(new NotificationService.Message("BATCH_RECALLED", "PRODUCT RECALL — batch " + b.getBatchNumber(),
                (p == null ? "" : p.getName() + " — ") + reason, "CRITICAL", "TRACEABILITY", "Batch", batchId),
                Role.QHSE_DIRECTOR, Role.QUALITY_MANAGER, Role.LOGISTICS_MANAGER, Role.WAREHOUSE_MANAGER, Role.GENERAL_MANAGER);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("batchId", batchId);
        out.put("previousStatus", previous);
        out.put("nonConformity", Map.of("id", nc.getId(), "reference", nc.getReference()));
        out.put("impact", impact.get("summary"));
        return out;
    }
}
