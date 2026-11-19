package com.dnd.smartflow.inventory.service;

import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.inventory.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Single entry point for stock changes: every quantity change is a persisted, traceable movement. */
@Service
@RequiredArgsConstructor
public class StockService {
    private final StockLevelRepository levels;
    private final StockMovementRepository movements;
    private final BatchRepository batches;

    @Transactional
    public StockMovement post(StockMovement m) {
        if (m.getQuantity() == null || m.getQuantity() <= 0) throw ApiException.badRequest("INVALID_QUANTITY", "Quantity must be positive");
        if (m.getBatchId() != null) {
            Batch b = batches.findById(m.getBatchId()).orElseThrow(() -> ApiException.notFound("Batch"));
            if (!b.getProductId().equals(m.getProductId())) throw ApiException.badRequest("BATCH_PRODUCT_MISMATCH", "Batch belongs to another product");
            if (("SHIPMENT".equals(m.getType()) || "TRANSFER".equals(m.getType())) && !"RELEASED".equals(b.getStatus())) {
                throw ApiException.conflict("BATCH_NOT_RELEASED", "Batch " + b.getBatchNumber() + " is " + b.getStatus() + " and cannot be shipped");
            }
        }
        switch (m.getType()) {
            case "RECEIPT", "PRODUCTION", "RETURN" -> add(require(m.getToWarehouseId(), "destination"), m, m.getQuantity());
            case "SHIPMENT", "RECALL" -> add(require(m.getFromWarehouseId(), "source"), m, -m.getQuantity());
            case "TRANSFER" -> {
                add(require(m.getFromWarehouseId(), "source"), m, -m.getQuantity());
                add(require(m.getToWarehouseId(), "destination"), m, m.getQuantity());
            }
            case "ADJUSTMENT" -> {
                if (m.getToWarehouseId() != null) add(m.getToWarehouseId(), m, m.getQuantity());
                else add(require(m.getFromWarehouseId(), "warehouse"), m, -m.getQuantity());
            }
            default -> throw ApiException.badRequest("INVALID_MOVEMENT", "Unknown movement type");
        }
        if (m.getOccurredAt() == null) m.setOccurredAt(Instant.now());
        return movements.save(m);
    }

    private void add(UUID warehouseId, StockMovement m, int delta) {
        StockLevel level = levels.findOne((r, q, cb) -> cb.and(cb.equal(r.get("warehouseId"), warehouseId), cb.equal(r.get("productId"), m.getProductId()),
                m.getBatchId() == null ? cb.isNull(r.get("batchId")) : cb.equal(r.get("batchId"), m.getBatchId()))).orElseGet(() -> {
            StockLevel l = new StockLevel();
            l.setWarehouseId(warehouseId);
            l.setProductId(m.getProductId());
            l.setBatchId(m.getBatchId());
            l.setQuantity(0);
            l.setReservedQuantity(0);
            return l;
        });
        int next = level.getQuantity() + delta;
        if (next < 0) {
            throw ApiException.conflict("INSUFFICIENT_STOCK", "Insufficient stock: " + level.getQuantity() + " available, " + (-delta) + " requested");
        }
        level.setQuantity(next);
        levels.save(level);
    }

    private static UUID require(UUID id, String what) {
        if (id == null) throw ApiException.badRequest("WAREHOUSE_REQUIRED", "A " + what + " warehouse is required");
        return id;
    }
}
