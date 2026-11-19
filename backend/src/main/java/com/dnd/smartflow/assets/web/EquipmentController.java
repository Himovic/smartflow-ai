package com.dnd.smartflow.assets.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.assets.domain.Equipment;
import com.dnd.smartflow.assets.domain.EquipmentRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/equipment")
@Tag(name = "Assets & maintenance")
public class EquipmentController extends CrudController<Equipment> {

    public EquipmentController(CrudSupport support, EquipmentRepository repository) {
        super(support, repository, Equipment.class, "assets");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "code");
    }
}
