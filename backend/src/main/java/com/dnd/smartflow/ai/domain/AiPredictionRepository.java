package com.dnd.smartflow.ai.domain;

import com.dnd.smartflow.common.persistence.TenantRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiPredictionRepository extends TenantRepository<AiPrediction> {
}
