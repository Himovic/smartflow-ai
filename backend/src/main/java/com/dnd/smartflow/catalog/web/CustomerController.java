package com.dnd.smartflow.catalog.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.catalog.domain.Customer;
import com.dnd.smartflow.catalog.domain.CustomerRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Catalog")
public class CustomerController extends CrudController<Customer> {

    public CustomerController(CrudSupport support, CustomerRepository repository) {
        super(support, repository, Customer.class, "catalog");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "name");
    }
}
