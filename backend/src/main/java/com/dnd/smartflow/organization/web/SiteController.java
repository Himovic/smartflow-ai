package com.dnd.smartflow.organization.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.organization.domain.Site;
import com.dnd.smartflow.organization.domain.SiteRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sites")
@Tag(name = "Organization")
public class SiteController extends CrudController<Site> {

    public SiteController(CrudSupport support, SiteRepository repository) {
        super(support, repository, Site.class, "organization");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "name");
    }
}
