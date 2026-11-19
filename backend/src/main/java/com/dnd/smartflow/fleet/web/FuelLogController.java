package com.dnd.smartflow.fleet.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.fleet.domain.FuelLog;
import com.dnd.smartflow.fleet.domain.FuelLogRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fuel-logs")
@Tag(name = "Fleet")
public class FuelLogController extends CrudController<FuelLog> {

    public FuelLogController(CrudSupport support, FuelLogRepository repository) {
        super(support, repository, FuelLog.class, "fleet");
    }
}
