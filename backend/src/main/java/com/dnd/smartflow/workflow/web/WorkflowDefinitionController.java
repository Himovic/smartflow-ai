package com.dnd.smartflow.workflow.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.workflow.domain.WorkflowDefinition;
import com.dnd.smartflow.workflow.domain.WorkflowDefinitionRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow-definitions")
@Tag(name = "Workflows")
public class WorkflowDefinitionController extends CrudController<WorkflowDefinition> {

    public WorkflowDefinitionController(CrudSupport support, WorkflowDefinitionRepository repository) {
        super(support, repository, WorkflowDefinition.class, "workflows");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "workflowKey");
    }
}
