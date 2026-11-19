package com.dnd.smartflow.distribution.domain;

import com.dnd.smartflow.common.persistence.TenantRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryRepository extends TenantRepository<Delivery> {
}
