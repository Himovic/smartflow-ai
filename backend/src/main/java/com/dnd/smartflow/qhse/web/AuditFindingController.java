package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.inventory.domain.BatchRepository;
import com.dnd.smartflow.notification.NotificationService;
import com.dnd.smartflow.qhse.domain.*;
import com.dnd.smartflow.qhse.service.NcRules;
import com.dnd.smartflow.workflow.WorkflowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/audit-findings")
@Tag(name = "Audits")
public class AuditFindingController extends CrudController<AuditFinding> {
    private final AuditRepository audits;
    private final NonConformityRepository ncs;
    private final WorkflowService workflow;
    private final NotificationService notifications;
    private final BatchRepository batches;

    public AuditFindingController(CrudSupport support, AuditFindingRepository repository, AuditRepository audits, NonConformityRepository ncs,
                                  WorkflowService workflow, NotificationService notifications, BatchRepository batches) {
        super(support, repository, AuditFinding.class, "audits");
        this.audits = audits;
        this.ncs = ncs;
        this.workflow = workflow;
        this.notifications = notifications;
        this.batches = batches;
    }

    @Override
    protected String referencePrefix() {
        return "AF";
    }

    @Override
    protected void beforeCreate(AuditFinding f) {
        if (f.getStatus() == null) f.setStatus("OPEN");
    }

    /** Major and minor non-conformities found during audits automatically open a tracked NC. */
    @Override
    protected void afterCreate(AuditFinding f) {
        if (!"MAJOR_NC".equals(f.getFindingType()) && !"MINOR_NC".equals(f.getFindingType())) return;
        Audit a = audits.findById(f.getAuditId()).orElseThrow(() -> ApiException.notFound("Audit"));
        NonConformity nc = new NonConformity();
        nc.setReference(support.getReferences().next("NC"));
        nc.setTitle("Audit finding " + f.getReference() + (f.getClause() != null ? " — clause " + f.getClause() : ""));
        nc.setDescription(f.getDescription() + (f.getEvidence() != null ? "\nEvidence: " + f.getEvidence() : ""));
        nc.setType("SUPPLIER".equals(a.getAuditType()) ? "SUPPLIER" : "AUDIT");
        nc.setCategory(a.getStandard());
        nc.setSeverity("MAJOR_NC".equals(f.getFindingType()) ? "MAJOR" : "MINOR");
        nc.setSource("AUDIT");
        nc.setAuditId(a.getId());
        nc.setSiteId(a.getSiteId());
        nc.setSupplierId(a.getSupplierId());
        nc.setDetectedAt(Instant.now());
        nc.setReportedByName(a.getLeadAuditor());
        nc.setDueDate(NcRules.defaultDueDate(nc.getSeverity(), LocalDate.now()));
        nc.setStatus(workflow.initialState("NonConformity", nc));
        NonConformity saved = ncs.save(nc);
        workflow.recordCreation("NonConformity", saved.getId(), saved.getStatus());
        NonConformityController.onReported(saved, notifications, batches);
        f.setNonConformityId(saved.getId());
        repository.save(f);
    }
}
