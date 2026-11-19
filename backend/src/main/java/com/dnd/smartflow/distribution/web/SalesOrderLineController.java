package com.dnd.smartflow.distribution.web;

import com.dnd.smartflow.catalog.domain.Product;
import com.dnd.smartflow.catalog.domain.ProductRepository;
import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.distribution.domain.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/order-lines")
@Tag(name = "Orders & deliveries")
public class SalesOrderLineController extends CrudController<SalesOrderLine> {
    private final SalesOrderRepository orders;
    private final ProductRepository products;

    public SalesOrderLineController(CrudSupport support, SalesOrderLineRepository repository, SalesOrderRepository orders, ProductRepository products) {
        super(support, repository, SalesOrderLine.class, "distribution");
        this.orders = orders;
        this.products = products;
    }

    @Override
    protected void beforeSave(SalesOrderLine l) {
        SalesOrder o = orders.findById(l.getOrderId()).orElseThrow(() -> ApiException.notFound("Order"));
        if (!"DRAFT".equals(o.getStatus())) throw ApiException.conflict("ORDER_LOCKED", "Lines can only be changed on draft orders");
        if (l.getQuantity() <= 0) throw ApiException.badRequest("INVALID_QUANTITY", "Quantity must be positive");
        Product p = products.findById(l.getProductId()).orElseThrow(() -> ApiException.notFound("Product"));
        if (l.getUnitPrice() == null) l.setUnitPrice(p.getUnitPrice());
        l.setLineTotal(l.getUnitPrice() == null ? null : l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())));
    }

    @Override
    protected void afterCreate(SalesOrderLine l) {
        recompute(l.getOrderId());
    }

    @Override
    protected void afterUpdate(SalesOrderLine before, SalesOrderLine l) {
        recompute(l.getOrderId());
    }

    @Override
    protected void beforeDelete(SalesOrderLine l) {
        SalesOrder o = orders.findById(l.getOrderId()).orElseThrow();
        if (!"DRAFT".equals(o.getStatus())) throw ApiException.conflict("ORDER_LOCKED", "Lines can only be changed on draft orders");
        repository.delete(l);
        repository.flush();
        recompute(l.getOrderId());
    }

    private void recompute(UUID orderId) {
        SalesOrder o = orders.findById(orderId).orElseThrow();
        BigDecimal total = BigDecimal.ZERO;
        double weight = 0;
        for (SalesOrderLine x : repository.findAll((r, q, cb) -> cb.equal(r.get("orderId"), orderId))) {
            if (x.getLineTotal() != null) total = total.add(x.getLineTotal());
            Product p = products.findById(x.getProductId()).orElse(null);
            if (p != null && p.getUnitWeightKg() != null) weight += p.getUnitWeightKg() * x.getQuantity();
        }
        o.setTotalAmount(total);
        o.setTotalWeightKg(Math.round(weight * 10) / 10.0);
        orders.save(o);
    }
}
