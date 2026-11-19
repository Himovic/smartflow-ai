package com.dnd.smartflow.organization.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.organization.domain.ProductionLine;
import com.dnd.smartflow.organization.domain.ProductionLineRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production-lines")
@Tag(name = "Organization")
public class ProductionLineController extends CrudController<ProductionLine> {

    public ProductionLineController(CrudSupport support, ProductionLineRepository repository) {
        super(support, repository, ProductionLine.class, "organization");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "code");
    }
}
