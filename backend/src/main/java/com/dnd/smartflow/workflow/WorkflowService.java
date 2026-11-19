package com.dnd.smartflow.workflow;

import com.dnd.smartflow.audit.AuditService;
import com.dnd.smartflow.common.persistence.TenantEntity;
import com.dnd.smartflow.common.persistence.TenantRepository;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.security.PermissionService;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.workflow.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Configurable workflow engine. A {@link WorkflowDefinition} lists states and transitions; each transition
 * declares the source states, target state, required permission, editable fields, mandatory fields,
 * whether a comment or evidence is required and optional named guards. Every transition is persisted as a
 * {@link WorkflowEvent} (complete history) and in the audit trail.
 */
@Service
@RequiredArgsConstructor
public class WorkflowService {
    private final WorkflowSubjects subjects;
    private final WorkflowDefinitionRepository definitions;
    private final WorkflowEventRepository events;
    private final CommentRepository comments;
    private final AttachmentRepository attachments;
    private final PermissionService perm;
    private final AuditService audit;
    private final ObjectMapper mapper;
    private final List<WorkflowListener> listeners;

    public WorkflowSubjects.Subject subject(String entityType) {
        return subjects.find(entityType).orElseThrow(() -> ApiException.badRequest("UNKNOWN_WORKFLOW_SUBJECT", entityType + " has no workflow"));
    }

    @SuppressWarnings("unchecked")
    public TenantEntity load(WorkflowSubjects.Subject s, UUID id) {
        TenantRepository<TenantEntity> repo = (TenantRepository<TenantEntity>) s.repository();
        TenantEntity e = repo.findById(id).orElseThrow(() -> ApiException.notFound(s.entityType()));
        Object site = new BeanWrapperImpl(e).isReadableProperty("siteId") ? new BeanWrapperImpl(e).getPropertyValue("siteId") : null;
        if (site instanceof UUID sid && !CurrentUser.require().canAccessSite(sid)) throw ApiException.notFound(s.entityType());
        return e;
    }

    public WorkflowDefinition definition(String workflowKey) {
        return definitions.findOne((r, q, cb) -> cb.and(cb.equal(r.get("workflowKey"), workflowKey), cb.isTrue(r.get("active"))))
                .orElseThrow(() -> ApiException.badRequest("WORKFLOW_NOT_CONFIGURED", "Workflow " + workflowKey + " is not configured"));
    }

    public String initialState(String entityType, Object entity) {
        return definition(subject(entityType).workflowKey().apply(entity)).getInitialState();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> describe(String entityType, UUID id) {
        WorkflowSubjects.Subject s = subject(entityType);
        perm.require(s.module(), "read");
        TenantEntity e = load(s, id);
        WorkflowDefinition def = definition(s.workflowKey().apply(e));
        String status = status(e);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("workflowKey", def.getWorkflowKey());
        out.put("name", def.getName());
        out.put("states", def.getStates());
        out.put("currentState", status);
        out.put("availableTransitions", def.getTransitions().stream()
                .filter(t -> fromStates(t).contains(status) && permitted(s, t)).toList());
        out.put("history", events.findAll((r, q, cb) -> cb.and(cb.equal(r.get("entityType"), entityType), cb.equal(r.get("entityId"), id)),
                org.springframework.data.domain.Sort.by("occurredAt")));
        return out;
    }

    @Transactional
    public TenantEntity transition(String entityType, UUID id, String code, String comment, Map<String, Object> fields) {
        WorkflowSubjects.Subject s = subject(entityType);
        TenantEntity e = load(s, id);
        WorkflowDefinition def = definition(s.workflowKey().apply(e));
        String from = status(e);
        Map<String, Object> t = def.getTransitions().stream().filter(x -> code.equals(x.get("code"))).findFirst()
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_TRANSITION", "Transition " + code + " does not exist"));
        if (!fromStates(t).contains(from)) {
            throw ApiException.conflict("INVALID_TRANSITION", "Transition '" + t.get("label") + "' is not allowed from state " + from);
        }
        if (!permitted(s, t)) throw ApiException.forbidden("You are not allowed to perform '" + t.get("label") + "'");
        if (Boolean.TRUE.equals(t.get("requiresComment")) && (comment == null || comment.isBlank())) {
            throw ApiException.badRequest("COMMENT_REQUIRED", "A comment is required for this step");
        }
        applyFields(e, t, fields);
        for (String required : list(t.get("requiredFields"))) {
            Object v = new BeanWrapperImpl(e).getPropertyValue(required);
            if (v == null || (v instanceof String str && str.isBlank()) || (v instanceof Collection<?> c && c.isEmpty())) {
                throw ApiException.badRequest("FIELD_REQUIRED", "Field '" + required + "' must be completed for this step");
            }
        }
        if (Boolean.TRUE.equals(t.get("requiresEvidence"))) {
            long count = attachments.count((r, q, cb) -> cb.and(cb.equal(r.get("entityType"), entityType), cb.equal(r.get("entityId"), id)));
            Object note = new BeanWrapperImpl(e).isReadableProperty("evidenceNote") ? new BeanWrapperImpl(e).getPropertyValue("evidenceNote") : null;
            if (count == 0 && (note == null || note.toString().isBlank())) {
                throw ApiException.badRequest("EVIDENCE_REQUIRED", "Attach evidence (photo, document) before this step");
            }
        }
        List<WorkflowListener> relevant = listeners.stream().filter(l -> l.supports(entityType)).toList();
        for (String guard : list(t.get("guards"))) relevant.forEach(l -> l.checkGuard(guard, e));

        String to = (String) t.get("to");
        new BeanWrapperImpl(e).setPropertyValue("status", to);
        @SuppressWarnings("unchecked")
        TenantRepository<TenantEntity> repo = (TenantRepository<TenantEntity>) s.repository();
        TenantEntity saved = repo.save(e);

        CurrentUser u = CurrentUser.require();
        WorkflowEvent ev = new WorkflowEvent();
        ev.setEntityType(entityType);
        ev.setEntityId(id);
        ev.setFromStatus(from);
        ev.setToStatus(to);
        ev.setTransition(code);
        ev.setComment(comment);
        ev.setActorUserId(u.userId());
        ev.setActorName(u.fullName());
        ev.setOccurredAt(Instant.now());
        events.save(ev);
        if (comment != null && !comment.isBlank()) {
            Comment c = new Comment();
            c.setEntityType(entityType);
            c.setEntityId(id);
            c.setBody("[" + t.get("label") + "] " + comment);
            c.setAuthorUserId(u.userId());
            c.setAuthorName(u.fullName());
            comments.save(c);
        }
        relevant.forEach(l -> l.onTransition(saved, code, from, to, t));
        audit.log("WORKFLOW_TRANSITION", entityType, id, Map.of("transition", code, "from", from, "to", to));
        return saved;
    }

    /** Records the initial workflow event when a subject is created. */
    @Transactional
    public void recordCreation(String entityType, UUID id, String state) {
        CurrentUser u = CurrentUser.require();
        WorkflowEvent ev = new WorkflowEvent();
        ev.setEntityType(entityType);
        ev.setEntityId(id);
        ev.setToStatus(state);
        ev.setTransition("create");
        ev.setActorUserId(u.userId());
        ev.setActorName(u.fullName());
        ev.setOccurredAt(Instant.now());
        events.save(ev);
    }

    private void applyFields(TenantEntity e, Map<String, Object> t, Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) return;
        Set<String> editable = new HashSet<>(list(t.get("fields")));
        Map<String, Object> allowed = new HashMap<>();
        fields.forEach((k, v) -> {
            if (editable.contains(k)) allowed.put(k, v);
        });
        if (allowed.isEmpty()) return;
        try {
            mapper.updateValue(e, allowed);
        } catch (Exception ex) {
            throw ApiException.badRequest("INVALID_FIELDS", "Invalid workflow field values: " + ex.getMessage());
        }
    }

    private boolean permitted(WorkflowSubjects.Subject s, Map<String, Object> t) {
        String p = (String) t.getOrDefault("permission", s.module() + ":write");
        String[] parts = p.split(":");
        return perm.can(parts[0], parts[1]);
    }

    private static String status(TenantEntity e) {
        return (String) new BeanWrapperImpl(e).getPropertyValue("status");
    }

    private static List<String> fromStates(Map<String, Object> t) {
        Object f = t.get("from");
        return f instanceof String str ? List.of(str) : list(f);
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Object o) {
        return o == null ? List.of() : (List<String>) o;
    }
}
