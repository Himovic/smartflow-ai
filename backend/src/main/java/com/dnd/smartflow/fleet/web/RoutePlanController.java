package com.dnd.smartflow.fleet.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.fleet.domain.RoutePlan;
import com.dnd.smartflow.fleet.domain.RoutePlanRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/route-plans")
@Tag(name = "Routes")
public class RoutePlanController extends CrudController<RoutePlan> {

    public RoutePlanController(CrudSupport support, RoutePlanRepository repository) {
        super(support, repository, RoutePlan.class, "routes");
    }
}
