package com.dnd.smartflow.inventory.domain;

import com.dnd.smartflow.common.persistence.TenantRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockLevelRepository extends TenantRepository<StockLevel> {
}
