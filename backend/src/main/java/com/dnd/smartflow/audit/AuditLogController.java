package com.dnd.smartflow.audit;

import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.security.PermissionService;
import com.dnd.smartflow.common.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit trail")
public class AuditLogController {
    private final AuditLogRepository repository;
    private final PermissionService perm;

    @GetMapping
    public PageResponse<AuditLog> list(@RequestParam(required = false) String entityType, @RequestParam(required = false) UUID entityId,
                                       @RequestParam(required = false) String action, @RequestParam(defaultValue = "0") int page) {
        UUID tenant = CurrentUser.require().tenantId();
        boolean ownRecord = entityType != null && entityId != null;
        if (!ownRecord) perm.require("users", "admin");
        return PageResponse.of(repository.findAll((r, q, cb) -> cb.and(cb.equal(r.get("tenantId"), tenant),
                entityType == null ? cb.conjunction() : cb.equal(r.get("entityType"), entityType),
                entityId == null ? cb.conjunction() : cb.equal(r.get("entityId"), entityId),
                action == null ? cb.conjunction() : cb.equal(r.get("action"), action)), PageRequest.of(page, 50, Sort.by(Sort.Direction.DESC, "occurredAt"))));
    }
}
