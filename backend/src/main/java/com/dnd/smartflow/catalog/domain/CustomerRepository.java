package com.dnd.smartflow.catalog.domain;

import com.dnd.smartflow.common.persistence.TenantRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends TenantRepository<Customer> {
}
