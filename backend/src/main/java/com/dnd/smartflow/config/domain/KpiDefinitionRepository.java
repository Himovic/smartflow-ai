package com.dnd.smartflow.config.domain;

import com.dnd.smartflow.common.persistence.TenantRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KpiDefinitionRepository extends TenantRepository<KpiDefinition> {
}
