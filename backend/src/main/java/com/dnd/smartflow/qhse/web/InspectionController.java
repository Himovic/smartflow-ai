package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.common.crud.ReferenceGenerator;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.security.Role;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.inventory.domain.BatchRepository;
import com.dnd.smartflow.inventory.domain.Warehouse;
import com.dnd.smartflow.inventory.domain.WarehouseRepository;
import com.dnd.smartflow.notification.NotificationService;
import com.dnd.smartflow.qhse.domain.*;
import com.dnd.smartflow.qhse.service.ChecklistScorer;
import com.dnd.smartflow.qhse.service.NcRules;
import com.dnd.smartflow.qhse.service.QhseReports;
import com.dnd.smartflow.reporting.ReportExporter.Column;
import com.dnd.smartflow.reporting.ReportExporter.Section;
import com.dnd.smartflow.workflow.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/inspections")
@Tag(name = "Inspections", description = "Quality, hygiene, safety and environmental inspections with digital checklists")
public class InspectionController extends CrudController<Inspection> {
    private final InspectionTemplateRepository templates;
    private final NonConformityRepository ncs;
    private final EnvironmentalReadingRepository readings;
    private final WarehouseRepository warehouses;
    private final BatchRepository batches;
    private final ReferenceGenerator references;
    private final WorkflowService workflow;
    private final NotificationService notifications;
    private final QhseReports reports;

    public InspectionController(CrudSupport support, InspectionRepository repository, InspectionTemplateRepository templates,
                                NonConformityRepository ncs, EnvironmentalReadingRepository readings, WarehouseRepository warehouses,
                                BatchRepository batches, ReferenceGenerator references, WorkflowService workflow,
                                NotificationService notifications, QhseReports reports) {
        super(support, repository, Inspection.class, "inspections");
        this.templates = templates;
        this.ncs = ncs;
        this.readings = readings;
        this.warehouses = warehouses;
        this.batches = batches;
        this.references = references;
        this.workflow = workflow;
        this.notifications = notifications;
        this.reports = reports;
    }

    public record SubmitRequest(List<Map<String, Object>> answers, Double temperature, Double humidity, String signature, String notes) {
    }

    @Override
    protected String referencePrefix() {
        return "INS";
    }

    @Override
    protected void beforeCreate(Inspection i) {
        i.setStatus("SCHEDULED");
        if (i.getTemplateId() != null) {
            InspectionTemplate t = templates.findById(i.getTemplateId()).orElseThrow(() -> ApiException.notFound("Inspection template"));
            if (i.getDomain() == null) i.setDomain(t.getDomain());
            if (i.getInspectionType() == null) i.setInspectionType(t.getInspectionType());
        }
        if (i.getScheduledDate() == null) i.setScheduledDate(LocalDate.now());
    }

    @Override
    protected void afterCreate(Inspection i) {
        if (i.getInspectorUserId() != null) {
            notifications.notifyUser(i.getInspectorUserId(), new NotificationService.Message("INSPECTION_SCHEDULED",
                    "Inspection scheduled: " + i.getReference(), i.getTitle() + " — " + i.getScheduledDate(), "INFO",
                    i.getDomain(), "Inspection", i.getId()));
        }
    }

    @PostMapping("/{id}/start")
    public Inspection start(@PathVariable UUID id) {
        support.getPermissions().require("inspections", "write");
        return support.getTx().execute(s -> {
            Inspection i = load(id);
            if (!"SCHEDULED".equals(i.getStatus())) throw ApiException.conflict("INVALID_STATE", "Inspection already started or closed");
            i.setStatus("IN_PROGRESS");
            if (i.getInspectorUserId() == null) {
                i.setInspectorUserId(CurrentUser.require().userId());
                i.setInspectorName(CurrentUser.require().fullName());
            }
            return repository.save(i);
        });
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit checklist answers: computes the score, records readings and raises non-conformities for failed criteria")
    public Map<String, Object> submit(@PathVariable UUID id, @RequestBody SubmitRequest req) {
        support.getPermissions().require("inspections", "write");
        return support.getTx().execute(s -> {
            Inspection i = load(id);
            if ("COMPLETED".equals(i.getStatus()) || "CANCELLED".equals(i.getStatus())) {
                throw ApiException.conflict("INVALID_STATE", "Inspection is already " + i.getStatus().toLowerCase());
            }
            InspectionTemplate t = i.getTemplateId() == null ? null : templates.findById(i.getTemplateId()).orElse(null);
            List<Map<String, Object>> questions = t == null ? List.of() : t.getQuestions();
            double passing = t == null || t.getPassingScore() == null ? 80 : t.getPassingScore();
            ChecklistScorer.Evaluation ev = ChecklistScorer.evaluate(questions, req.answers(), passing);
            if (!ev.missing().isEmpty()) {
                throw ApiException.badRequest("MISSING_ANSWERS", "Required questions not answered: " + String.join("; ", ev.missing()));
            }
            CurrentUser u = CurrentUser.require();
            i.setAnswers(ev.answers());
            i.setScore(ev.score());
            i.setResult(ev.result());
            i.setTemperature(req.temperature());
            i.setHumidity(req.humidity());
            i.setSignature(req.signature());
            i.setNotes(req.notes());
            i.setCompletedAt(Instant.now());
            i.setStatus("COMPLETED");
            if (i.getInspectorUserId() == null) {
                i.setInspectorUserId(u.userId());
                i.setInspectorName(u.fullName());
            }
            recordReadings(i);
            List<NonConformity> raised = new ArrayList<>();
            for (ChecklistScorer.Failure f : ev.failures()) {
                if (!f.createNc()) continue;
                raised.add(raiseNc(i, f));
            }
            i.setGeneratedNcCount(raised.size());
            repository.save(i);
            if ("FAIL".equals(i.getResult())) {
                notifications.notifyRoles(new NotificationService.Message("INSPECTION_FAILED", "Inspection failed: " + i.getReference(),
                        i.getTitle() + " — score " + i.getScore() + "%", "WARNING", i.getDomain(), "Inspection", i.getId()),
                        domainManager(i.getDomain()), Role.QHSE_DIRECTOR);
            }
            support.getAudit().log("INSPECTION_SUBMITTED", "Inspection", i.getId(), Map.of("score", i.getScore(), "result", i.getResult()));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("inspection", i);
            out.put("failures", ev.failures());
            out.put("nonConformities", raised.stream().map(n -> Map.of("id", n.getId(), "reference", n.getReference(), "severity", n.getSeverity())).toList());
            return out;
        });
    }

    private NonConformity raiseNc(Inspection i, ChecklistScorer.Failure f) {
        NonConformity nc = new NonConformity();
        nc.setReference(references.next("NC"));
        nc.setTitle("Failed criterion: " + truncate(f.text(), 200));
        nc.setDescription("Raised automatically by inspection " + i.getReference() + " (" + i.getTitle() + ").\nObserved value: " + f.value()
                + (f.comment() != null ? "\nInspector comment: " + f.comment() : ""));
        nc.setType(switch (i.getDomain()) {
            case "HYGIENE" -> "HYGIENE";
            case "SAFETY" -> "SAFETY";
            case "ENVIRONMENT" -> "ENVIRONMENT";
            default -> "INCOMING".equals(i.getInspectionType()) ? "SUPPLIER" : "PRODUCT";
        });
        nc.setCategory("INSPECTION_FINDING");
        nc.setSeverity(f.critical() ? "MAJOR" : "MINOR");
        nc.setSource("INSPECTION");
        nc.setInspectionId(i.getId());
        nc.setSiteId(i.getSiteId());
        nc.setDepartmentId(i.getDepartmentId());
        nc.setProductionLineId(i.getProductionLineId());
        nc.setProductId(i.getProductId());
        nc.setBatchId(i.getBatchId());
        nc.setSupplierId(i.getSupplierId());
        nc.setDetectedAt(Instant.now());
        nc.setReportedByUserId(i.getInspectorUserId());
        nc.setReportedByName(i.getInspectorName());
        nc.setDueDate(NcRules.defaultDueDate(nc.getSeverity(), LocalDate.now()));
        nc.setStatus(workflow.initialState("NonConformity", nc));
        NonConformity saved = ncs.save(nc);
        workflow.recordCreation("NonConformity", saved.getId(), saved.getStatus());
        NonConformityController.onReported(saved, notifications, batches);
        return saved;
    }

    private void recordReadings(Inspection i) {
        Warehouse w = i.getWarehouseId() == null ? null : warehouses.findById(i.getWarehouseId()).orElse(null);
        if (i.getTemperature() != null) {
            EnvironmentalReading r = reading(i, "TEMPERATURE", i.getTemperature(), "°C");
            if (w != null && Boolean.TRUE.equals(w.getTemperatureControlled())) {
                r.setMinThreshold(w.getMinTemp());
                r.setMaxThreshold(w.getMaxTemp());
            } else {
                r.setMaxThreshold(25.0);
            }
            EnvironmentalReadingController.evaluate(r);
            readings.save(r);
        }
        if (i.getHumidity() != null) {
            EnvironmentalReading r = reading(i, "HUMIDITY", i.getHumidity(), "%");
            r.setMinThreshold(30.0);
            r.setMaxThreshold(65.0);
            EnvironmentalReadingController.evaluate(r);
            readings.save(r);
        }
    }

    private EnvironmentalReading reading(Inspection i, String type, double value, String unit) {
        EnvironmentalReading r = new EnvironmentalReading();
        r.setReadingType(type);
        r.setReadingValue(value);
        r.setUnit(unit);
        r.setSiteId(i.getSiteId());
        r.setWarehouseId(i.getWarehouseId());
        r.setVehicleId(i.getVehicleId());
        r.setLocation("Inspection " + i.getReference());
        r.setRecordedAt(Instant.now());
        r.setSource("INSPECTION");
        return r;
    }

    @GetMapping("/{id}/report")
    @SuppressWarnings("unchecked")
    public ResponseEntity<byte[]> report(@PathVariable UUID id) {
        support.getPermissions().require("inspections", "read");
        Inspection i = load(id);
        InspectionTemplate t = i.getTemplateId() == null ? null : templates.findById(i.getTemplateId()).orElse(null);
        Map<String, String> texts = new HashMap<>();
        if (t != null && t.getQuestions() != null) t.getQuestions().forEach(q -> texts.put(String.valueOf(q.get("id")), String.valueOf(q.get("text"))));
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("Reference", i.getReference());
        f.put("Title", i.getTitle());
        f.put("Checklist", t == null ? "—" : t.getName() + " (v" + t.getTemplateVersion() + ")");
        f.put("Domain / type", i.getDomain() + " / " + Objects.toString(i.getInspectionType(), "—"));
        f.put("Scheduled / completed", i.getScheduledDate() + " / " + Objects.toString(i.getCompletedAt(), "—"));
        f.put("Inspector", i.getInspectorName());
        f.put("Score", i.getScore() == null ? "—" : i.getScore() + " %");
        f.put("Result", i.getResult());
        f.put("Temperature / humidity", Objects.toString(i.getTemperature(), "—") + " °C / " + Objects.toString(i.getHumidity(), "—") + " %");
        f.put("Non-conformities raised", i.getGeneratedNcCount());
        f.put("Signed", i.getSignature() != null && !i.getSignature().isBlank() ? "Yes" : "No");
        f.put("Notes", i.getNotes());
        List<Map<String, Object>> rows = new ArrayList<>();
        if (i.getAnswers() != null) for (Map<String, Object> a : i.getAnswers()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("q", texts.getOrDefault(String.valueOf(a.get("questionId")), String.valueOf(a.get("questionId"))));
            Object v = a.get("value");
            r.put("value", v instanceof String sv && sv.startsWith("data:") ? "[attachment]" : v);
            r.put("result", a.get("passed") == null ? "—" : Boolean.TRUE.equals(a.get("passed")) ? "OK" : "NOT OK");
            r.put("comment", a.get("comment"));
            rows.add(r);
        }
        return reports.pdf(i.getReference(), "Inspection report " + i.getReference(), List.of(Section.fields("Inspection", f),
                Section.table("Checklist results", List.of(new Column("q", "Criterion"), new Column("value", "Answer"), new Column("result", "Result"),
                        new Column("comment", "Comment")), rows)));
    }

    private static Role domainManager(String domain) {
        return switch (domain) {
            case "HYGIENE" -> Role.HYGIENE_MANAGER;
            case "SAFETY" -> Role.SAFETY_MANAGER;
            case "ENVIRONMENT" -> Role.ENVIRONMENT_MANAGER;
            default -> Role.QUALITY_MANAGER;
        };
    }

    private static String truncate(String s, int n) {
        return s == null ? "" : s.length() > n ? s.substring(0, n) : s;
    }
}
