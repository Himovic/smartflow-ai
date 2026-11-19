package com.dnd.smartflow.config.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.config.domain.CustomFieldDefinition;
import com.dnd.smartflow.config.domain.CustomFieldDefinitionRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/custom-fields")
@Tag(name = "Configuration")
public class CustomFieldDefinitionController extends CrudController<CustomFieldDefinition> {

    public CustomFieldDefinitionController(CrudSupport support, CustomFieldDefinitionRepository repository) {
        super(support, repository, CustomFieldDefinition.class, "tenant");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "sortOrder");
    }
}
