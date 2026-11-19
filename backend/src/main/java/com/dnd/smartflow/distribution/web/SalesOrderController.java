package com.dnd.smartflow.distribution.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.distribution.domain.*;
import com.dnd.smartflow.inventory.domain.*;
import com.dnd.smartflow.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders & deliveries")
public class SalesOrderController extends CrudController<SalesOrder> {
    private final SalesOrderLineRepository lines;
    private final StockLevelRepository levels;
    private final BatchRepository batches;
    private final StockService stock;
    private final DeliveryRepository deliveries;

    public SalesOrderController(CrudSupport support, SalesOrderRepository repository, SalesOrderLineRepository lines, StockLevelRepository levels,
                                BatchRepository batches, StockService stock, DeliveryRepository deliveries) {
        super(support, repository, SalesOrder.class, "distribution");
        this.lines = lines;
        this.levels = levels;
        this.batches = batches;
        this.stock = stock;
        this.deliveries = deliveries;
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.DESC, "orderDate").and(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Override
    protected void beforeCreate(SalesOrder o) {
        o.setOrderNumber(support.getReferences().next("SO"));
        o.setStatus("DRAFT");
        if (o.getOrderDate() == null) o.setOrderDate(LocalDate.now());
        if (o.getPriority() == null) o.setPriority("NORMAL");
    }

    @GetMapping("/{id}/lines")
    public List<SalesOrderLine> lines(@PathVariable UUID id) {
        support.getPermissions().require("distribution", "read");
        load(id);
        return lines.findAll((r, q, cb) -> cb.equal(r.get("orderId"), id));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm the order and allocate released batches first-expired-first-out (FEFO)")
    public Map<String, Object> confirm(@PathVariable UUID id) {
        support.getPermissions().require("distribution", "write");
        return support.getTx().execute(s -> {
            SalesOrder o = load(id);
            if (!"DRAFT".equals(o.getStatus())) throw ApiException.conflict("INVALID_STATE", "Only draft orders can be confirmed");
            if (o.getWarehouseId() == null) throw ApiException.badRequest("WAREHOUSE_REQUIRED", "Select the shipping warehouse");
            List<SalesOrderLine> orderLines = lines(id);
            if (orderLines.isEmpty()) throw ApiException.badRequest("EMPTY_ORDER", "Add at least one line");
            List<String> shortages = new ArrayList<>();
            List<SalesOrderLine> allocated = new ArrayList<>();
            for (SalesOrderLine line : orderLines) {
                if (line.getBatchId() != null) {
                    allocated.add(line);
                    reserve(o.getWarehouseId(), line.getProductId(), line.getBatchId(), line.getQuantity(), shortages);
                    continue;
                }
                int remaining = line.getQuantity();
                boolean first = true;
                for (StockLevel lvl : fefo(o.getWarehouseId(), line.getProductId())) {
                    int free = lvl.getQuantity() - Optional.ofNullable(lvl.getReservedQuantity()).orElse(0);
                    if (free <= 0) continue;
                    int take = Math.min(free, remaining);
                    SalesOrderLine target = line;
                    if (!first) {
                        target = new SalesOrderLine();
                        target.setOrderId(id);
                        target.setProductId(line.getProductId());
                        target.setUnitPrice(line.getUnitPrice());
                    }
                    target.setBatchId(lvl.getBatchId());
                    target.setQuantity(take);
                    if (target.getUnitPrice() != null) target.setLineTotal(target.getUnitPrice().multiply(java.math.BigDecimal.valueOf(take)));
                    lvl.setReservedQuantity(Optional.ofNullable(lvl.getReservedQuantity()).orElse(0) + take);
                    levels.save(lvl);
                    allocated.add(lines.save(target));
                    remaining -= take;
                    first = false;
                    if (remaining == 0) break;
                }
                if (remaining > 0) shortages.add(line.getProductId() + " short by " + remaining);
            }
            if (!shortages.isEmpty()) {
                s.setRollbackOnly();
                throw ApiException.conflict("STOCK_SHORTAGE", "Insufficient released stock: " + String.join("; ", shortages));
            }
            o.setStatus("CONFIRMED");
            repository.save(o);
            support.getAudit().log("ORDER_CONFIRMED", "SalesOrder", id, Map.of("lines", allocated.size()));
            return Map.of("order", o, "lines", allocated);
        });
    }

    @PostMapping("/{id}/ship")
    @Operation(summary = "Ship a confirmed order: posts shipment movements and creates the delivery")
    public Map<String, Object> ship(@PathVariable UUID id) {
        support.getPermissions().require("distribution", "write");
        return support.getTx().execute(s -> {
            SalesOrder o = load(id);
            if (!Set.of("CONFIRMED", "PREPARING").contains(o.getStatus())) throw ApiException.conflict("INVALID_STATE", "Order must be confirmed");
            for (SalesOrderLine l : lines(id)) {
                levels.findOne((r, q, cb) -> cb.and(cb.equal(r.get("warehouseId"), o.getWarehouseId()), cb.equal(r.get("productId"), l.getProductId()),
                        l.getBatchId() == null ? cb.isNull(r.get("batchId")) : cb.equal(r.get("batchId"), l.getBatchId()))).ifPresent(lvl -> {
                    lvl.setReservedQuantity(Math.max(0, Optional.ofNullable(lvl.getReservedQuantity()).orElse(0) - l.getQuantity()));
                    levels.save(lvl);
                });
                StockMovement m = new StockMovement();
                m.setType("SHIPMENT");
                m.setProductId(l.getProductId());
                m.setBatchId(l.getBatchId());
                m.setFromWarehouseId(o.getWarehouseId());
                m.setQuantity(l.getQuantity());
                m.setReferenceType("SalesOrder");
                m.setReferenceId(o.getId());
                m.setOccurredAt(Instant.now());
                stock.post(m);
            }
            o.setStatus("SHIPPED");
            repository.save(o);
            Delivery d = deliveries.findOne((r, q, cb) -> cb.equal(r.get("orderId"), id)).orElseGet(Delivery::new);
            if (d.getId() == null) {
                d.setDeliveryNumber(support.getReferences().next("DLV"));
                d.setOrderId(id);
                d.setCustomerId(o.getCustomerId());
                d.setWarehouseId(o.getWarehouseId());
                d.setPlannedDate(o.getRequestedDate() == null ? LocalDate.now() : o.getRequestedDate());
                d.setStatus("PLANNED");
                d = deliveries.save(d);
            }
            support.getAudit().log("ORDER_SHIPPED", "SalesOrder", id, Map.of("delivery", d.getDeliveryNumber()));
            return Map.of("order", o, "delivery", d);
        });
    }

    @PostMapping("/{id}/cancel")
    public SalesOrder cancel(@PathVariable UUID id) {
        support.getPermissions().require("distribution", "write");
        return support.getTx().execute(s -> {
            SalesOrder o = load(id);
            if (!Set.of("DRAFT", "CONFIRMED", "PREPARING").contains(o.getStatus())) throw ApiException.conflict("INVALID_STATE", "Order already shipped");
            if (!"DRAFT".equals(o.getStatus())) {
                for (SalesOrderLine l : lines(id)) {
                    if (l.getBatchId() == null) continue;
                    levels.findOne((r, q, cb) -> cb.and(cb.equal(r.get("warehouseId"), o.getWarehouseId()), cb.equal(r.get("batchId"), l.getBatchId())))
                            .ifPresent(lvl -> {
                                lvl.setReservedQuantity(Math.max(0, Optional.ofNullable(lvl.getReservedQuantity()).orElse(0) - l.getQuantity()));
                                levels.save(lvl);
                            });
                }
            }
            o.setStatus("CANCELLED");
            return repository.save(o);
        });
    }

    private void reserve(UUID warehouseId, UUID productId, UUID batchId, int qty, List<String> shortages) {
        Batch b = batches.findById(batchId).orElseThrow(() -> ApiException.notFound("Batch"));
        if (!"RELEASED".equals(b.getStatus())) {
            shortages.add("batch " + b.getBatchNumber() + " is " + b.getStatus());
            return;
        }
        Optional<StockLevel> lvl = levels.findOne((r, q, cb) -> cb.and(cb.equal(r.get("warehouseId"), warehouseId),
                cb.equal(r.get("productId"), productId), cb.equal(r.get("batchId"), batchId)));
        int free = lvl.map(l -> l.getQuantity() - Optional.ofNullable(l.getReservedQuantity()).orElse(0)).orElse(0);
        if (free < qty) {
            shortages.add("batch " + b.getBatchNumber() + " short by " + (qty - free));
            return;
        }
        lvl.get().setReservedQuantity(Optional.ofNullable(lvl.get().getReservedQuantity()).orElse(0) + qty);
        levels.save(lvl.get());
    }

    /** Stock of released, non-expired batches ordered by expiry date (first expired, first out). */
    private List<StockLevel> fefo(UUID warehouseId, UUID productId) {
        List<StockLevel> candidates = levels.findAll((r, q, cb) -> cb.and(cb.equal(r.get("warehouseId"), warehouseId),
                cb.equal(r.get("productId"), productId), cb.greaterThan(r.get("quantity"), 0), cb.isNotNull(r.get("batchId"))));
        Map<UUID, Batch> byId = new HashMap<>();
        batches.findAllById(candidates.stream().map(StockLevel::getBatchId).toList()).forEach(b -> byId.put(b.getId(), b));
        LocalDate today = LocalDate.now();
        return candidates.stream()
                .filter(l -> byId.containsKey(l.getBatchId()) && "RELEASED".equals(byId.get(l.getBatchId()).getStatus()))
                .filter(l -> byId.get(l.getBatchId()).getExpiryDate() == null || !byId.get(l.getBatchId()).getExpiryDate().isBefore(today))
                .sorted(Comparator.comparing(l -> Optional.ofNullable(byId.get(l.getBatchId()).getExpiryDate()).orElse(LocalDate.MAX)))
                .toList();
    }
}
