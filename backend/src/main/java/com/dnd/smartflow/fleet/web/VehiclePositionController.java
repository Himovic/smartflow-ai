package com.dnd.smartflow.fleet.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.fleet.domain.VehiclePosition;
import com.dnd.smartflow.fleet.domain.VehiclePositionRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicle-positions")
@Tag(name = "Fleet")
public class VehiclePositionController extends CrudController<VehiclePosition> {

    public VehiclePositionController(CrudSupport support, VehiclePositionRepository repository) {
        super(support, repository, VehiclePosition.class, "fleet");
    }
}
