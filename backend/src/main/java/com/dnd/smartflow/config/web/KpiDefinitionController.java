package com.dnd.smartflow.config.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.config.domain.KpiDefinition;
import com.dnd.smartflow.config.domain.KpiDefinitionRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kpi-definitions")
@Tag(name = "Configuration")
public class KpiDefinitionController extends CrudController<KpiDefinition> {

    public KpiDefinitionController(CrudSupport support, KpiDefinitionRepository repository) {
        super(support, repository, KpiDefinition.class, "analytics");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "code");
    }
}
