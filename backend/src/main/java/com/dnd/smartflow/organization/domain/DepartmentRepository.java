package com.dnd.smartflow.organization.domain;

import com.dnd.smartflow.common.persistence.TenantRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DepartmentRepository extends TenantRepository<Department> {
}
