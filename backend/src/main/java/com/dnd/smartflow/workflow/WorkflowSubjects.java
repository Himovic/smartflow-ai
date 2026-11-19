package com.dnd.smartflow.workflow;

import com.dnd.smartflow.common.persistence.TenantEntity;
import com.dnd.smartflow.common.persistence.TenantRepository;
import com.dnd.smartflow.qhse.domain.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Registry of entity types that are driven by configurable workflow definitions. */
@Component
public class WorkflowSubjects {

    public record Subject(String entityType, Class<? extends TenantEntity> type, TenantRepository<? extends TenantEntity> repository,
                          String module, Function<Object, String> workflowKey) {
    }

    private final Map<String, Subject> subjects = new LinkedHashMap<>();

    public WorkflowSubjects(NonConformityRepository nc, CapaActionRepository capa, IncidentRepository incidents,
                            QhseDocumentRepository documents) {
        register(new Subject("NonConformity", NonConformity.class, nc, "nc", e -> "NON_CONFORMITY"));
        register(new Subject("CapaAction", CapaAction.class, capa, "capa", e -> "CAPA"));
        register(new Subject("Incident", Incident.class, incidents, "incidents",
                e -> "ENVIRONMENT".equals(((Incident) e).getDomain()) ? "ENVIRONMENTAL_INCIDENT" : "SAFETY_INCIDENT"));
        register(new Subject("QhseDocument", QhseDocument.class, documents, "documents", e -> "DOCUMENT_APPROVAL"));
    }

    private void register(Subject s) {
        subjects.put(s.entityType(), s);
    }

    public Optional<Subject> find(String entityType) {
        return Optional.ofNullable(subjects.get(entityType));
    }

    public Map<String, Subject> all() {
        return subjects;
    }
}
