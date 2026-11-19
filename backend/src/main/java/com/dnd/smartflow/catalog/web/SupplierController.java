package com.dnd.smartflow.catalog.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.catalog.domain.Supplier;
import com.dnd.smartflow.catalog.domain.SupplierRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/suppliers")
@Tag(name = "Catalog")
public class SupplierController extends CrudController<Supplier> {

    public SupplierController(CrudSupport support, SupplierRepository repository) {
        super(support, repository, Supplier.class, "catalog");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "name");
    }
}
