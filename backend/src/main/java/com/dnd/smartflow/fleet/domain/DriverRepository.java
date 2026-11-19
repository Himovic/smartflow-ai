package com.dnd.smartflow.fleet.domain;

import com.dnd.smartflow.common.persistence.TenantRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverRepository extends TenantRepository<Driver> {
}
