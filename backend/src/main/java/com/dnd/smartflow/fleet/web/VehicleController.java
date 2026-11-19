package com.dnd.smartflow.fleet.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.fleet.domain.Vehicle;
import com.dnd.smartflow.fleet.domain.VehicleRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicles")
@Tag(name = "Fleet")
public class VehicleController extends CrudController<Vehicle> {

    public VehicleController(CrudSupport support, VehicleRepository repository) {
        super(support, repository, Vehicle.class, "fleet");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "plateNumber");
    }
}
