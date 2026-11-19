package com.dnd.smartflow.assets.domain;

import com.dnd.smartflow.common.persistence.TenantRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintenanceWorkOrderRepository extends TenantRepository<MaintenanceWorkOrder> {
}
