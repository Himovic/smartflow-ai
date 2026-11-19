package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.qhse.domain.Audit;
import com.dnd.smartflow.qhse.domain.AuditRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audits")
@Tag(name = "Audits")
public class AuditController extends CrudController<Audit> {

    public AuditController(CrudSupport support, AuditRepository repository) {
        super(support, repository, Audit.class, "audits");
    }

    @Override
    protected String referencePrefix() {
        return "AUD";
    }
}
