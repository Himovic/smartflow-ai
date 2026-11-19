package com.dnd.smartflow.catalog.domain;

import com.dnd.smartflow.common.persistence.TenantRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierRepository extends TenantRepository<Supplier> {
}
