package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.security.Role;
import com.dnd.smartflow.notification.NotificationService;
import com.dnd.smartflow.qhse.domain.*;
import com.dnd.smartflow.qhse.service.QhseReports;
import com.dnd.smartflow.qhse.service.RiskMatrix;
import com.dnd.smartflow.reporting.ReportExporter.Column;
import com.dnd.smartflow.reporting.ReportExporter.Section;
import com.dnd.smartflow.workflow.WorkflowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incidents", description = "Accidents, near misses, environmental and sanitary incidents")
public class IncidentController extends CrudController<Incident> {
    private final WorkflowService workflow;
    private final NotificationService notifications;
    private final CapaActionRepository capas;
    private final CapaActionController capaController;
    private final QhseReports reports;

    public IncidentController(CrudSupport support, IncidentRepository repository, WorkflowService workflow, NotificationService notifications,
                              CapaActionRepository capas, CapaActionController capaController, QhseReports reports) {
        super(support, repository, Incident.class, "incidents");
        this.workflow = workflow;
        this.notifications = notifications;
        this.capas = capas;
        this.capaController = capaController;
        this.reports = reports;
    }

    @Override
    protected String referencePrefix() {
        return "INC";
    }

    @Override
    protected void beforeCreate(Incident i) {
        CurrentUser u = CurrentUser.require();
        i.setStatus(workflow.initialState("Incident", i));
        if (i.getOccurredAt() == null) i.setOccurredAt(Instant.now());
        if (i.getReportedByUserId() == null) {
            i.setReportedByUserId(u.userId());
            i.setReportedByName(u.fullName());
        }
        if (i.getSeverity() == null) i.setSeverity("NEAR_MISS".equals(i.getType()) ? 2 : 3);
        if (i.getProbability() == null) i.setProbability(3);
        if (i.getDueDate() == null) i.setDueDate(LocalDate.now().plusDays(i.getSeverity() >= 4 ? 7 : 21));
    }

    @Override
    protected void beforeSave(Incident i) {
        i.setRiskScore(RiskMatrix.score(i.getProbability(), i.getSeverity()));
    }

    @Override
    protected void afterCreate(Incident i) {
        workflow.recordCreation("Incident", i.getId(), i.getStatus());
        Role primary = switch (i.getDomain()) {
            case "ENVIRONMENT" -> Role.ENVIRONMENT_MANAGER;
            case "HYGIENE" -> Role.HYGIENE_MANAGER;
            default -> Role.SAFETY_MANAGER;
        };
        boolean serious = i.getSeverity() != null && i.getSeverity() >= 4;
        List<Role> roles = new ArrayList<>(List.of(primary, Role.QHSE_DIRECTOR));
        if (serious) {
            roles.add(Role.GENERAL_MANAGER);
            roles.add(Role.SITE_MANAGER);
        }
        notifications.notifyRoles(new NotificationService.Message("INCIDENT_REPORTED",
                (serious ? "Serious " : "") + i.getType().replace('_', ' ').toLowerCase() + " reported: " + i.getReference(), i.getTitle(),
                serious ? "CRITICAL" : "WARNING", i.getDomain(), "Incident", i.getId()), roles.toArray(Role[]::new));
    }

    @GetMapping("/{id}/capa")
    public List<CapaAction> capa(@PathVariable UUID id) {
        support.getPermissions().require("capa", "read");
        load(id);
        return capas.findAll((r, q, cb) -> cb.and(cb.equal(r.get("sourceType"), "INCIDENT"), cb.equal(r.get("sourceId"), id)), Sort.by("createdAt"));
    }

    @PostMapping("/{id}/capa")
    public CapaAction createCapa(@PathVariable UUID id, @Valid @RequestBody CapaAction body) {
        Incident i = load(id);
        body.setSourceType("INCIDENT");
        body.setSourceId(i.getId());
        if (body.getSiteId() == null) body.setSiteId(i.getSiteId());
        return capaController.createLinked(body);
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<byte[]> report(@PathVariable UUID id) {
        support.getPermissions().require("incidents", "read");
        Incident i = load(id);
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("Reference", i.getReference());
        f.put("Title", i.getTitle());
        f.put("Domain / type", i.getDomain() + " / " + i.getType());
        if (i.getEnvCategory() != null) f.put("Environmental category", i.getEnvCategory());
        f.put("Occurred at", i.getOccurredAt());
        f.put("Location", i.getLocationDetail());
        f.put("Severity × probability", i.getSeverity() + " × " + i.getProbability() + " = " + i.getRiskScore() + " (" + RiskMatrix.level(i.getRiskScore()) + ")");
        f.put("Injured persons / lost days", i.getInjuredPersons() + " / " + i.getLostTimeDays());
        f.put("Status", i.getStatus());
        f.put("Reported by", i.getReportedByName());
        f.put("Investigator", i.getInvestigatorName());
        f.put("Description", i.getDescription());
        f.put("Immediate actions", i.getImmediateActions());
        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("Impact assessment", i.getImpactAssessment());
        inv.put("Investigation findings", i.getInvestigationFindings());
        inv.put("Root cause", i.getRootCause());
        List<Map<String, Object>> actions = capa(id).stream().map(c -> Map.<String, Object>of("ref", c.getReference(), "title", c.getTitle(),
                "owner", Objects.toString(c.getOwnerName(), ""), "due", Objects.toString(c.getDueDate(), ""), "status", c.getStatus())).toList();
        return reports.pdf(i.getReference(), "Incident report " + i.getReference(), List.of(Section.fields("Incident", f),
                Section.fields("Investigation", inv),
                Section.table("Corrective measures", List.of(new Column("ref", "Ref"), new Column("title", "Measure"), new Column("owner", "Owner"),
                        new Column("due", "Due"), new Column("status", "Status")), actions),
                reports.history("Incident", id)));
    }
}
