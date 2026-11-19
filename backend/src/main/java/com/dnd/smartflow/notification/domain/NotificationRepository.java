package com.dnd.smartflow.notification.domain;

import com.dnd.smartflow.common.persistence.TenantRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends TenantRepository<Notification> {
}
