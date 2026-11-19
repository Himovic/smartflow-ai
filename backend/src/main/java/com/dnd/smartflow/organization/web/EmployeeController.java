package com.dnd.smartflow.organization.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.organization.domain.Employee;
import com.dnd.smartflow.organization.domain.EmployeeRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
@Tag(name = "Organization")
public class EmployeeController extends CrudController<Employee> {

    public EmployeeController(CrudSupport support, EmployeeRepository repository) {
        super(support, repository, Employee.class, "organization");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "lastName");
    }
}
