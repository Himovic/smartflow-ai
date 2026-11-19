package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.qhse.domain.InspectionTemplate;
import com.dnd.smartflow.qhse.domain.InspectionTemplateRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspection-templates")
@Tag(name = "Inspections")
public class InspectionTemplateController extends CrudController<InspectionTemplate> {

    public InspectionTemplateController(CrudSupport support, InspectionTemplateRepository repository) {
        super(support, repository, InspectionTemplate.class, "inspections");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "code");
    }
}
