package com.dnd.smartflow.inventory.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.inventory.domain.Batch;
import com.dnd.smartflow.inventory.domain.BatchRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/batches")
@Tag(name = "Inventory")
public class BatchController extends CrudController<Batch> {
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "QUARANTINE", Set.of("RELEASED", "BLOCKED"),
            "RELEASED", Set.of("QUARANTINE", "BLOCKED", "RECALLED", "EXPIRED", "CONSUMED"),
            "BLOCKED", Set.of("QUARANTINE", "RECALLED"),
            "RECALLED", Set.of(),
            "EXPIRED", Set.of(),
            "CONSUMED", Set.of());

    public BatchController(CrudSupport support, BatchRepository repository) {
        super(support, repository, Batch.class, "inventory");
    }

    @Override
    protected void beforeCreate(Batch b) {
        b.setStatus("QUARANTINE");
        if (b.getQualityStatus() == null) b.setQualityStatus("PENDING");
    }

    @PostMapping("/{id}/status/{status}")
    @Operation(summary = "Change batch status (release requires an approved quality decision)")
    public Batch changeStatus(@PathVariable UUID id, @PathVariable String status, @RequestBody(required = false) Map<String, String> body) {
        support.getPermissions().require("quality".equals(status) ? "quality" : "inventory", "write");
        return support.getTx().execute(s -> {
            Batch b = load(id);
            if (!ALLOWED.getOrDefault(b.getStatus(), Set.of()).contains(status)) {
                throw ApiException.conflict("INVALID_BATCH_STATUS", "Cannot move batch from " + b.getStatus() + " to " + status);
            }
            if ("RELEASED".equals(status)) {
                support.getPermissions().require("quality", "approve");
                b.setQualityStatus("APPROVED");
            }
            if ("BLOCKED".equals(status) || "RECALLED".equals(status)) support.getPermissions().require("traceability", "write");
            String from = b.getStatus();
            b.setStatus(status);
            Batch saved = repository.save(b);
            support.getAudit().log("BATCH_STATUS", "Batch", id, Map.of("from", from, "to", status,
                    "reason", body == null ? "" : body.getOrDefault("reason", "")));
            return saved;
        });
    }
}
