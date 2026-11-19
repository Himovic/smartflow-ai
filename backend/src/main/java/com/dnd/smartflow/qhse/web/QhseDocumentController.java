package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.qhse.domain.QhseDocument;
import com.dnd.smartflow.qhse.domain.QhseDocumentRepository;
import com.dnd.smartflow.workflow.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Document management", description = "Controlled documents with versions, approval workflow and access levels")
public class QhseDocumentController extends CrudController<QhseDocument> {
    private final WorkflowService workflow;

    public QhseDocumentController(CrudSupport support, QhseDocumentRepository repository, WorkflowService workflow) {
        super(support, repository, QhseDocument.class, "documents");
        this.workflow = workflow;
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by("reference");
    }

    /** RESTRICTED documents are only visible to their allowed roles and to document approvers. */
    @Override
    protected Specification<QhseDocument> extraScope() {
        CurrentUser u = CurrentUser.require();
        if (u.can("documents", "approve")) return null;
        String role = "%\"" + u.role().name() + "\"%";
        return (r, q, cb) -> cb.or(cb.notEqual(cb.coalesce(r.get("accessLevel"), "INTERNAL"), "RESTRICTED"),
                cb.like(r.get("allowedRoles").as(String.class), role));
    }

    @Override
    protected QhseDocument load(UUID id) {
        QhseDocument d = super.load(id);
        Specification<QhseDocument> scope = extraScope();
        if (scope != null && repository.count(scope.and((r, q, cb) -> cb.equal(r.get("id"), id))) == 0) {
            throw com.dnd.smartflow.common.web.ApiException.notFound("Document");
        }
        return d;
    }

    @Override
    protected void beforeCreate(QhseDocument d) {
        d.setStatus(workflow.initialState("QhseDocument", d));
        if (d.getDocVersion() == null) d.setDocVersion("1.0");
        if (d.getAccessLevel() == null) d.setAccessLevel("INTERNAL");
    }

    @Override
    protected void afterCreate(QhseDocument d) {
        workflow.recordCreation("QhseDocument", d.getId(), d.getStatus());
    }

    @PostMapping("/{id}/new-version")
    @Operation(summary = "Create a new draft version; the previous version becomes obsolete when the new one is approved")
    public QhseDocument newVersion(@PathVariable UUID id) {
        QhseDocument prev = load(id);
        QhseDocument next = new QhseDocument();
        support.getCopier().copy(prev, next);
        next.setPreviousVersionId(prev.getId());
        next.setReference(prev.getReference() + "-v" + bump(prev.getDocVersion()));
        next.setDocVersion(bump(prev.getDocVersion()));
        next.setAllowedRoles(prev.getAllowedRoles() == null ? null : new ArrayList<>(prev.getAllowedRoles()));
        next.setEffectiveDate(null);
        return create(next);
    }

    static String bump(String v) {
        try {
            String[] p = (v == null ? "1.0" : v).split("\\.");
            return (Integer.parseInt(p[0]) + 1) + ".0";
        } catch (NumberFormatException e) {
            return v + ".1";
        }
    }
}
