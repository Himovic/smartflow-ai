package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.qhse.domain.PestControlVisit;
import com.dnd.smartflow.qhse.domain.PestControlVisitRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pest-control-visits")
@Tag(name = "Hygiene")
public class PestControlVisitController extends CrudController<PestControlVisit> {

    public PestControlVisitController(CrudSupport support, PestControlVisitRepository repository) {
        super(support, repository, PestControlVisit.class, "hygiene");
    }
}
