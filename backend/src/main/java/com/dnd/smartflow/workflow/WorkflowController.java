package com.dnd.smartflow.workflow;

import com.dnd.smartflow.common.persistence.TenantEntity;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.security.PermissionService;
import com.dnd.smartflow.common.tenant.TenantContext;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.files.StorageService;
import com.dnd.smartflow.workflow.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Workflows & collaboration", description = "Workflow transitions, history, comments and evidence attachments")
public class WorkflowController {
    /** Entity types that accept comments/attachments, mapped to the module guarding them. */
    static final Map<String, String> MODULES = Map.ofEntries(
            Map.entry("NonConformity", "nc"), Map.entry("CapaAction", "capa"), Map.entry("Incident", "incidents"),
            Map.entry("QhseDocument", "documents"), Map.entry("Inspection", "inspections"), Map.entry("Audit", "audits"),
            Map.entry("AuditFinding", "audits"), Map.entry("Equipment", "assets"), Map.entry("WasteRecord", "environment"),
            Map.entry("WorkPermit", "safety"), Map.entry("Batch", "inventory"), Map.entry("Delivery", "distribution"),
            Map.entry("Vehicle", "fleet"), Map.entry("RiskAssessment", "risk"), Map.entry("CleaningRecord", "hygiene"),
            Map.entry("PestControlVisit", "hygiene"), Map.entry("Certification", "training"), Map.entry("TrainingRecord", "training"),
            Map.entry("ChemicalProduct", "environment"), Map.entry("Supplier", "catalog"));

    private final WorkflowService workflow;
    private final CommentRepository comments;
    private final AttachmentRepository attachments;
    private final StorageService storage;
    private final PermissionService perm;

    public record TransitionRequest(String comment, Map<String, Object> fields) {
    }

    public record CommentRequest(String body) {
    }

    @GetMapping("/workflow/{entityType}/{id}")
    @Operation(summary = "Workflow definition, current state, allowed transitions and full history of a record")
    public Map<String, Object> describe(@PathVariable String entityType, @PathVariable UUID id) {
        return workflow.describe(entityType, id);
    }

    @PostMapping("/workflow/{entityType}/{id}/transitions/{code}")
    public TenantEntity transition(@PathVariable String entityType, @PathVariable UUID id, @PathVariable String code,
                                   @RequestBody(required = false) TransitionRequest req) {
        return workflow.transition(entityType, id, code, req == null ? null : req.comment(), req == null ? null : req.fields());
    }

    @GetMapping("/collab/{entityType}/{id}/comments")
    public List<Comment> comments(@PathVariable String entityType, @PathVariable UUID id) {
        perm.require(module(entityType), "read");
        return comments.findAll((r, q, cb) -> cb.and(cb.equal(r.get("entityType"), entityType), cb.equal(r.get("entityId"), id)),
                Sort.by("createdAt"));
    }

    @PostMapping("/collab/{entityType}/{id}/comments")
    @Transactional
    public Comment addComment(@PathVariable String entityType, @PathVariable UUID id, @RequestBody CommentRequest req) {
        perm.require(module(entityType), "write");
        if (req.body() == null || req.body().isBlank()) throw ApiException.badRequest("EMPTY_COMMENT", "Comment is empty");
        CurrentUser u = CurrentUser.require();
        Comment c = new Comment();
        c.setEntityType(entityType);
        c.setEntityId(id);
        c.setBody(req.body().length() > 5000 ? req.body().substring(0, 5000) : req.body());
        c.setAuthorUserId(u.userId());
        c.setAuthorName(u.fullName());
        return comments.save(c);
    }

    @GetMapping("/collab/{entityType}/{id}/attachments")
    public List<Attachment> attachments(@PathVariable String entityType, @PathVariable UUID id) {
        perm.require(module(entityType), "read");
        return attachments.findAll((r, q, cb) -> cb.and(cb.equal(r.get("entityType"), entityType), cb.equal(r.get("entityId"), id)),
                Sort.by("createdAt"));
    }

    @PostMapping(value = "/collab/{entityType}/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Attachment upload(@PathVariable String entityType, @PathVariable UUID id, @RequestParam("file") MultipartFile file,
                             @RequestParam(defaultValue = "EVIDENCE") String kind) throws IOException {
        perm.require(module(entityType), "write");
        String key = storage.store(TenantContext.require(), file.getContentType(), file.getInputStream());
        Attachment a = new Attachment();
        a.setEntityType(entityType);
        a.setEntityId(id);
        String name = Objects.toString(file.getOriginalFilename(), "file").replaceAll("[\\\\/:*?\"<>|]", "_");
        a.setFileName(name.length() > 200 ? name.substring(0, 200) : name);
        a.setContentType(file.getContentType());
        a.setSizeBytes(file.getSize());
        a.setStorageKey(key);
        a.setKind(List.of("EVIDENCE", "PHOTO", "DOCUMENT", "SIGNATURE").contains(kind) ? kind : "EVIDENCE");
        a.setUploadedBy(CurrentUser.require().fullName());
        return attachments.save(a);
    }

    @GetMapping("/collab/attachments/{attachmentId}/content")
    public ResponseEntity<byte[]> download(@PathVariable UUID attachmentId) {
        Attachment a = attachments.findById(attachmentId).orElseThrow(() -> ApiException.notFound("Attachment"));
        perm.require(module(a.getEntityType()), "read");
        byte[] data = storage.read(TenantContext.require(), a.getStorageKey());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(a.getFileName()).build().toString())
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .body(data);
    }

    @DeleteMapping("/collab/attachments/{attachmentId}")
    @Transactional
    public Map<String, Object> deleteAttachment(@PathVariable UUID attachmentId) {
        Attachment a = attachments.findById(attachmentId).orElseThrow(() -> ApiException.notFound("Attachment"));
        perm.require(module(a.getEntityType()), "delete");
        attachments.delete(a);
        storage.delete(a.getStorageKey());
        return Map.of("deleted", attachmentId);
    }

    private static String module(String entityType) {
        String m = MODULES.get(entityType);
        if (m == null) throw ApiException.badRequest("UNSUPPORTED_ENTITY", "Collaboration is not available for " + entityType);
        return m;
    }
}
