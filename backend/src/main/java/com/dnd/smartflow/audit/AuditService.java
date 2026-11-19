package com.dnd.smartflow.audit;

import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(String action, String entityType, UUID entityId, Map<String, Object> details) {
        AuditLog log = new AuditLog();
        UUID tenant = TenantContext.get();
        log.setTenantId(tenant == null || TenantContext.ROOT.equals(tenant) ? null : tenant);
        CurrentUser.find().ifPresent(u -> {
            log.setUserId(u.userId());
            log.setUserEmail(u.email());
            if (log.getTenantId() == null) log.setTenantId(u.tenantId());
        });
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        repository.save(log);
    }
}
