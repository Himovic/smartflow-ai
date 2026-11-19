package com.dnd.smartflow.inventory.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.inventory.domain.Warehouse;
import com.dnd.smartflow.inventory.domain.WarehouseRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouses")
@Tag(name = "Inventory")
public class WarehouseController extends CrudController<Warehouse> {

    public WarehouseController(CrudSupport support, WarehouseRepository repository) {
        super(support, repository, Warehouse.class, "inventory");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "code");
    }
}
