package com.dnd.smartflow.organization.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.organization.domain.Department;
import com.dnd.smartflow.organization.domain.DepartmentRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/departments")
@Tag(name = "Organization")
public class DepartmentController extends CrudController<Department> {

    public DepartmentController(CrudSupport support, DepartmentRepository repository) {
        super(support, repository, Department.class, "organization");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "name");
    }
}
