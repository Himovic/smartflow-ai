package com.dnd.smartflow.inventory.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.inventory.domain.StockMovement;
import com.dnd.smartflow.inventory.domain.StockMovementRepository;
import com.dnd.smartflow.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-movements")
@Tag(name = "Inventory")
public class StockMovementController extends CrudController<StockMovement> {
    private final StockService stock;

    public StockMovementController(CrudSupport support, StockMovementRepository repository, StockService stock) {
        super(support, repository, StockMovement.class, "inventory");
        this.stock = stock;
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.DESC, "occurredAt");
    }

    @Override
    @PostMapping
    @Operation(summary = "Post a stock movement; stock levels are updated atomically")
    public StockMovement create(@Valid @RequestBody StockMovement body) {
        support.getPermissions().require("inventory", "write");
        return support.getTx().execute(s -> {
            StockMovement m = newInstance();
            support.getCopier().copy(body, m);
            StockMovement saved = stock.post(m);
            support.getAudit().log("STOCK_MOVEMENT", "StockMovement", saved.getId(), Map.of("type", saved.getType(), "quantity", saved.getQuantity()));
            return saved;
        });
    }

    @Override
    @PutMapping("/{id}")
    public StockMovement update(@PathVariable UUID id, @RequestBody StockMovement body) {
        throw ApiException.conflict("IMMUTABLE", "Stock movements are immutable; post a correcting adjustment instead");
    }

    @Override
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable UUID id) {
        throw ApiException.conflict("IMMUTABLE", "Stock movements are immutable; post a correcting adjustment instead");
    }
}
