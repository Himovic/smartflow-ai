package com.dnd.smartflow.assets.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.assets.domain.MaintenanceWorkOrder;
import com.dnd.smartflow.assets.domain.MaintenanceWorkOrderRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maintenance-work-orders")
@Tag(name = "Assets & maintenance")
public class MaintenanceWorkOrderController extends CrudController<MaintenanceWorkOrder> {

    public MaintenanceWorkOrderController(CrudSupport support, MaintenanceWorkOrderRepository repository) {
        super(support, repository, MaintenanceWorkOrder.class, "assets");
    }

    @Override
    protected String referencePrefix() {
        return "WO";
    }
}
