package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.qhse.domain.ChemicalProduct;
import com.dnd.smartflow.qhse.domain.ChemicalProductRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chemical-products")
@Tag(name = "Environment")
public class ChemicalProductController extends CrudController<ChemicalProduct> {

    public ChemicalProductController(CrudSupport support, ChemicalProductRepository repository) {
        super(support, repository, ChemicalProduct.class, "environment");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "name");
    }
}
