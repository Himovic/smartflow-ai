package com.dnd.smartflow.qhse.service;

import com.dnd.smartflow.common.persistence.TenantEntity;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.security.Role;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.notification.NotificationService;
import com.dnd.smartflow.notification.NotificationService.Message;
import com.dnd.smartflow.qhse.domain.*;
import com.dnd.smartflow.workflow.WorkflowListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Guards and side effects of the QHSE workflows (NC, CAPA, incidents, document approval). */
@Component
@RequiredArgsConstructor
public class QhseWorkflowListener implements WorkflowListener {
    private static final Set<String> CAPA_DONE = Set.of("PENDING_VERIFICATION", "CLOSED");
    private final CapaActionRepository capas;
    private final QhseDocumentRepository documents;
    private final NotificationService notifications;

    @Override
    public boolean supports(String entityType) {
        return Set.of("NonConformity", "CapaAction", "Incident", "QhseDocument").contains(entityType);
    }

    @Override
    public void checkGuard(String guard, TenantEntity entity) {
        if ("capasCompleted".equals(guard)) {
            String sourceType = entity instanceof NonConformity ? "NON_CONFORMITY" : "INCIDENT";
            List<CapaAction> linked = capas.findAll((r, q, cb) -> cb.and(cb.equal(r.get("sourceType"), sourceType),
                    cb.equal(r.get("sourceId"), entity.getId()), cb.notEqual(r.get("status"), "CANCELLED")));
            if (linked.isEmpty()) {
                throw ApiException.badRequest("CAPA_REQUIRED", "Create at least one corrective action before this step");
            }
            long open = linked.stream().filter(c -> !CAPA_DONE.contains(c.getStatus())).count();
            if (open > 0) {
                throw ApiException.badRequest("CAPA_OPEN", open + " corrective action(s) are not completed yet");
            }
        }
        if ("capasClosed".equals(guard)) {
            String sourceType = entity instanceof NonConformity ? "NON_CONFORMITY" : "INCIDENT";
            long notClosed = capas.findAll((r, q, cb) -> cb.and(cb.equal(r.get("sourceType"), sourceType),
                    cb.equal(r.get("sourceId"), entity.getId()))).stream()
                    .filter(c -> !Set.of("CLOSED", "CANCELLED").contains(c.getStatus())).count();
            if (notClosed > 0) {
                throw ApiException.badRequest("CAPA_NOT_VERIFIED", notClosed + " action(s) still need effectiveness verification");
            }
        }
    }

    @Override
    public void onTransition(TenantEntity entity, String transition, String from, String to, Map<String, Object> def) {
        CurrentUser u = CurrentUser.require();
        switch (entity) {
            case NonConformity nc -> {
                if ("CLOSED".equals(to)) nc.setClosedAt(Instant.now());
                if ("ASSIGNED".equals(to) && nc.getAssigneeUserId() != null) {
                    notifications.notifyUser(nc.getAssigneeUserId(), new Message("NC_ASSIGNED", "Non-conformity assigned: " + nc.getReference(),
                            nc.getTitle() + " — due " + nc.getDueDate(), "WARNING", "QUALITY", "NonConformity", nc.getId()));
                }
                if ("PENDING_VERIFICATION".equals(to)) {
                    notifications.notifyRoles(new Message("NC_VERIFICATION", "Verification requested: " + nc.getReference(), nc.getTitle(),
                            "INFO", "QUALITY", "NonConformity", nc.getId()), Role.QUALITY_MANAGER, Role.QHSE_DIRECTOR);
                }
            }
            case CapaAction c -> {
                if ("PENDING_VERIFICATION".equals(to)) {
                    c.setCompletedAt(Instant.now());
                    notifications.notifyRoles(new Message("CAPA_SUBMITTED", "Action ready for verification: " + c.getReference(), c.getTitle(),
                            "INFO", "CAPA", "CapaAction", c.getId()), Role.QHSE_DIRECTOR, Role.QUALITY_MANAGER, Role.SAFETY_MANAGER);
                }
                if ("CLOSED".equals(to)) {
                    c.setVerifiedAt(Instant.now());
                    c.setVerifiedBy(u.fullName());
                    if (c.getEffectivenessResult() == null) c.setEffectivenessResult("EFFECTIVE");
                }
                if ("IN_PROGRESS".equals(to) && "PENDING_VERIFICATION".equals(from)) {
                    c.setEffectivenessResult("INEFFECTIVE");
                    notifications.notifyUser(c.getOwnerUserId(), new Message("CAPA_REJECTED", "Action reopened: " + c.getReference(),
                            "Verification found the action ineffective", "WARNING", "CAPA", "CapaAction", c.getId()));
                }
            }
            case Incident i -> {
                if ("CLOSED".equals(to)) i.setClosedAt(Instant.now());
                if ("UNDER_INVESTIGATION".equals(to) && i.getInvestigatorUserId() != null) {
                    notifications.notifyUser(i.getInvestigatorUserId(), new Message("INCIDENT_INVESTIGATION", "Investigation assigned: " + i.getReference(),
                            i.getTitle(), "WARNING", i.getDomain(), "Incident", i.getId()));
                }
            }
            case QhseDocument d -> {
                if ("APPROVED".equals(to)) {
                    d.setApprovedAt(Instant.now());
                    d.setApproverName(u.fullName());
                    if (d.getEffectiveDate() == null) d.setEffectiveDate(LocalDate.now());
                    if (d.getPreviousVersionId() != null) {
                        documents.findById(d.getPreviousVersionId()).ifPresent(prev -> {
                            prev.setStatus("OBSOLETE");
                            documents.save(prev);
                        });
                    }
                }
            }
            default -> {
            }
        }
    }
}
