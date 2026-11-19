package com.dnd.smartflow.qhse.domain;

import com.dnd.smartflow.common.persistence.TenantRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CleaningScheduleRepository extends TenantRepository<CleaningSchedule> {
}
