package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.security.Role;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.inventory.domain.Batch;
import com.dnd.smartflow.inventory.domain.BatchRepository;
import com.dnd.smartflow.notification.NotificationService;
import com.dnd.smartflow.qhse.domain.*;
import com.dnd.smartflow.qhse.service.NcRules;
import com.dnd.smartflow.qhse.service.QhseReports;
import com.dnd.smartflow.reporting.ReportExporter.Column;
import com.dnd.smartflow.reporting.ReportExporter.Section;
import com.dnd.smartflow.workflow.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/non-conformities")
@Tag(name = "Non-conformities")
public class NonConformityController extends CrudController<NonConformity> {
    private final WorkflowService workflow;
    private final NotificationService notifications;
    private final CapaActionRepository capas;
    private final BatchRepository batches;
    private final QhseReports reports;
    private final CapaActionController capaController;

    public NonConformityController(CrudSupport support, NonConformityRepository repository, WorkflowService workflow,
                                   NotificationService notifications, CapaActionRepository capas, BatchRepository batches, QhseReports reports,
                                   CapaActionController capaController) {
        super(support, repository, NonConformity.class, "nc");
        this.workflow = workflow;
        this.notifications = notifications;
        this.capas = capas;
        this.batches = batches;
        this.reports = reports;
        this.capaController = capaController;
    }

    @Override
    protected String referencePrefix() {
        return "NC";
    }

    @Override
    protected void beforeCreate(NonConformity nc) {
        CurrentUser u = CurrentUser.require();
        String suggested = NcRules.suggestSeverity(nc);
        nc.setSeverity(nc.getSeverity() == null ? suggested : NcRules.escalate(nc.getSeverity(), suggested));
        nc.setStatus(workflow.initialState("NonConformity", nc));
        if (nc.getDetectedAt() == null) nc.setDetectedAt(Instant.now());
        if (nc.getReportedByUserId() == null) {
            nc.setReportedByUserId(u.userId());
            nc.setReportedByName(u.fullName());
        }
        if (nc.getDueDate() == null) nc.setDueDate(NcRules.defaultDueDate(nc.getSeverity(), LocalDate.now()));
        if (nc.getSource() == null) nc.setSource("EMPLOYEE");
    }

    @Override
    protected void afterCreate(NonConformity nc) {
        workflow.recordCreation("NonConformity", nc.getId(), nc.getStatus());
        onReported(nc, notifications, batches);
    }

    /** Shared by manual reports, inspections and audits: notifications and batch quarantine. */
    public static void onReported(NonConformity nc, NotificationService notifications, BatchRepository batches) {
        boolean hygiene = "HYGIENE".equals(nc.getType());
        notifications.notifyRoles(new NotificationService.Message("NC_REPORTED",
                        nc.getSeverity() + " non-conformity " + nc.getReference(), nc.getTitle(),
                        "CRITICAL".equals(nc.getSeverity()) ? "CRITICAL" : "MAJOR".equals(nc.getSeverity()) ? "WARNING" : "INFO",
                        "QUALITY", "NonConformity", nc.getId()),
                hygiene ? new Role[]{Role.HYGIENE_MANAGER, Role.QHSE_DIRECTOR} : new Role[]{Role.QUALITY_MANAGER, Role.QHSE_DIRECTOR});
        if ("CRITICAL".equals(nc.getSeverity()) && nc.getBatchId() != null) {
            batches.findById(nc.getBatchId()).ifPresent(b -> {
                if ("RELEASED".equals(b.getStatus())) {
                    b.setStatus("QUARANTINE");
                    batches.save(b);
                    notifications.notifyRoles(new NotificationService.Message("BATCH_QUARANTINED",
                            "Batch " + b.getBatchNumber() + " placed in quarantine",
                            "Automatic quarantine after critical non-conformity " + nc.getReference(), "CRITICAL", "TRACEABILITY",
                            "Batch", b.getId()), Role.QUALITY_MANAGER, Role.LOGISTICS_MANAGER, Role.WAREHOUSE_MANAGER);
                }
            });
        }
    }

    @GetMapping("/{id}/capa")
    public List<CapaAction> capa(@PathVariable UUID id) {
        support.getPermissions().require("capa", "read");
        load(id);
        return capas.findAll((r, q, cb) -> cb.and(cb.equal(r.get("sourceType"), "NON_CONFORMITY"), cb.equal(r.get("sourceId"), id)),
                Sort.by("createdAt"));
    }

    @PostMapping("/{id}/capa")
    @Operation(summary = "Create a corrective or preventive action linked to this non-conformity")
    public CapaAction createCapa(@PathVariable UUID id, @Valid @RequestBody CapaAction body) {
        support.getPermissions().require("capa", "write");
        NonConformity nc = load(id);
        body.setSourceType("NON_CONFORMITY");
        body.setSourceId(nc.getId());
        if (body.getSiteId() == null) body.setSiteId(nc.getSiteId());
        return capaController.createLinked(body);
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<byte[]> report(@PathVariable UUID id) {
        support.getPermissions().require("nc", "read");
        NonConformity nc = load(id);
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("Reference", nc.getReference());
        f.put("Title", nc.getTitle());
        f.put("Type / category", nc.getType() + " / " + Objects.toString(nc.getCategory(), "—"));
        f.put("Severity", nc.getSeverity());
        f.put("Status", nc.getStatus());
        f.put("Detected at", nc.getDetectedAt());
        f.put("Reported by", nc.getReportedByName());
        f.put("Assignee", nc.getAssigneeName());
        f.put("Due date", nc.getDueDate());
        f.put("Quantity affected", nc.getQuantityAffected());
        f.put("Description", nc.getDescription());
        f.put("Immediate action", nc.getImmediateAction());
        Map<String, Object> rca = new LinkedHashMap<>();
        rca.put("Method", nc.getRootCauseMethod());
        if (nc.getFiveWhys() != null) for (int i = 0; i < nc.getFiveWhys().size(); i++) rca.put("Why " + (i + 1), nc.getFiveWhys().get(i));
        rca.put("Root cause", nc.getRootCause());
        List<Map<String, Object>> actions = new ArrayList<>();
        for (CapaAction c : capa(id)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ref", c.getReference());
            m.put("type", c.getType());
            m.put("title", c.getTitle());
            m.put("owner", c.getOwnerName());
            m.put("due", c.getDueDate());
            m.put("status", c.getStatus());
            actions.add(m);
        }
        return reports.pdf(nc.getReference(), "Non-conformity report " + nc.getReference(), List.of(
                Section.fields("Identification", f), Section.fields("Root cause analysis", rca),
                Section.table("Corrective & preventive actions", List.of(new Column("ref", "Ref"), new Column("type", "Type"),
                        new Column("title", "Action"), new Column("owner", "Owner"), new Column("due", "Due"), new Column("status", "Status")), actions),
                reports.history("NonConformity", id)));
    }

    @Override
    protected void beforeDelete(NonConformity nc) {
        if (!"REPORTED".equals(nc.getStatus()) && !"CANCELLED".equals(nc.getStatus())) {
            throw ApiException.conflict("NC_IN_PROGRESS", "Only reported or cancelled non-conformities can be deleted");
        }
    }
}
