package com.dnd.smartflow.fleet.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.fleet.domain.Driver;
import com.dnd.smartflow.fleet.domain.DriverRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/drivers")
@Tag(name = "Fleet")
public class DriverController extends CrudController<Driver> {

    public DriverController(CrudSupport support, DriverRepository repository) {
        super(support, repository, Driver.class, "fleet");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "lastName");
    }
}
