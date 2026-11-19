package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.qhse.domain.PpeIssuance;
import com.dnd.smartflow.qhse.domain.PpeIssuanceRepository;
import com.dnd.smartflow.qhse.domain.PpeItem;
import com.dnd.smartflow.qhse.domain.PpeItemRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/ppe-issuances")
@Tag(name = "Safety")
public class PpeIssuanceController extends CrudController<PpeIssuance> {
    private final PpeItemRepository items;

    public PpeIssuanceController(CrudSupport support, PpeIssuanceRepository repository, PpeItemRepository items) {
        super(support, repository, PpeIssuance.class, "safety");
        this.items = items;
    }

    @Override
    protected void beforeCreate(PpeIssuance p) {
        PpeItem item = items.findById(p.getPpeItemId()).orElseThrow(() -> ApiException.notFound("PPE item"));
        int stock = item.getStockQuantity() == null ? 0 : item.getStockQuantity();
        if (stock < p.getQuantity()) throw ApiException.conflict("INSUFFICIENT_PPE_STOCK", "Only " + stock + " " + item.getName() + " in stock");
        item.setStockQuantity(stock - p.getQuantity());
        items.save(item);
        if (p.getIssuedDate() == null) p.setIssuedDate(LocalDate.now());
        if (p.getReplacementDate() == null && item.getReplacementMonths() != null) p.setReplacementDate(p.getIssuedDate().plusMonths(item.getReplacementMonths()));
        if (p.getIssuedBy() == null) p.setIssuedBy(CurrentUser.require().fullName());
    }
}
