package com.dnd.smartflow.integration.domain;

import com.dnd.smartflow.common.persistence.TenantRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncJobRepository extends TenantRepository<SyncJob> {
}
