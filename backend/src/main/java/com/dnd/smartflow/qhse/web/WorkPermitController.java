package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.qhse.domain.WorkPermit;
import com.dnd.smartflow.qhse.domain.WorkPermitRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-permits")
@Tag(name = "Safety")
public class WorkPermitController extends CrudController<WorkPermit> {

    public WorkPermitController(CrudSupport support, WorkPermitRepository repository) {
        super(support, repository, WorkPermit.class, "safety");
    }

    @Override
    protected String referencePrefix() {
        return "PTW";
    }
}
