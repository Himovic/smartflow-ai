package com.dnd.smartflow.inventory.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.inventory.domain.StockLevel;
import com.dnd.smartflow.inventory.domain.StockLevelRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stock-levels")
@Tag(name = "Inventory")
public class StockLevelController extends CrudController<StockLevel> {

    public StockLevelController(CrudSupport support, StockLevelRepository repository) {
        super(support, repository, StockLevel.class, "inventory");
    }
}
