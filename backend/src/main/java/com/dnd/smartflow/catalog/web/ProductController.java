package com.dnd.smartflow.catalog.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.catalog.domain.Product;
import com.dnd.smartflow.catalog.domain.ProductRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Catalog")
public class ProductController extends CrudController<Product> {

    public ProductController(CrudSupport support, ProductRepository repository) {
        super(support, repository, Product.class, "catalog");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "sku");
    }
}
