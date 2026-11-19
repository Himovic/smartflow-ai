package com.dnd.smartflow.common.crud;

import com.dnd.smartflow.audit.AuditService;
import com.dnd.smartflow.common.security.PermissionService;
import com.dnd.smartflow.reporting.ReportExporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared collaborators of every {@link CrudController}. */
@Getter
@Component
@RequiredArgsConstructor
public class CrudSupport {
    private final EntityManager entityManager;
    private final PermissionService permissions;
    private final AuditService audit;
    private final ReportExporter exporter;
    private final ReferenceGenerator references;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final EntityCopier copier;
}
