package com.dnd.smartflow.traceability;

import com.dnd.smartflow.audit.AuditService;
import com.dnd.smartflow.catalog.domain.Product;
import com.dnd.smartflow.common.security.PermissionService;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.inventory.domain.Batch;
import com.dnd.smartflow.qhse.service.QhseReports;
import com.dnd.smartflow.reporting.ReportExporter.Column;
import com.dnd.smartflow.reporting.ReportExporter.Section;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/traceability")
@RequiredArgsConstructor
@Tag(name = "Traceability & recall")
public class TraceabilityController {
    private final TraceabilityService service;
    private final PermissionService perm;
    private final AuditService audit;
    private final QhseReports reports;

    public record RecallRequest(String reason, String recallClass) {
    }

    @GetMapping("/batches/{id}")
    @Operation(summary = "Full backward and forward traceability of a batch")
    public Map<String, Object> trace(@PathVariable UUID id) {
        perm.require("traceability", "read");
        return service.trace(id);
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String batchNumber) {
        perm.require("traceability", "read");
        Batch b = service.findByNumber(batchNumber).orElseThrow(() -> ApiException.notFound("Batch " + batchNumber));
        return service.trace(b.getId());
    }

    @PostMapping("/batches/{id}/recall")
    @Operation(summary = "Initiate a product recall: blocks the batch, frees allocations, opens a critical NC and alerts stakeholders")
    public Map<String, Object> recall(@PathVariable UUID id, @RequestBody RecallRequest req) {
        perm.require("traceability", "approve");
        Map<String, Object> result = service.recall(id, req.reason(), req.recallClass());
        audit.log("BATCH_RECALL", "Batch", id, Map.of("reason", req.reason()));
        return result;
    }

    @GetMapping("/batches/{id}/recall-report")
    @SuppressWarnings("unchecked")
    public ResponseEntity<byte[]> report(@PathVariable UUID id) {
        perm.require("traceability", "read");
        Map<String, Object> t = service.trace(id);
        Batch b = (Batch) t.get("batch");
        Product p = (Product) t.get("product");
        Map<String, Object> forward = (Map<String, Object>) t.get("forward");
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("Batch", b.getBatchNumber());
        f.put("Product", p == null ? "—" : p.getSku() + " — " + p.getName());
        f.put("Status", b.getStatus());
        f.put("Production / expiry", b.getProductionDate() + " / " + b.getExpiryDate());
        f.put("Raw material lots", b.getRawMaterialLots());
        Map<String, Object> s = new LinkedHashMap<>();
        ((Map<String, Object>) t.get("summary")).forEach((k, v) -> s.put(k.replaceAll("([A-Z])", " $1").toLowerCase(), v));
        return reports.pdf("recall_" + b.getBatchNumber(), "Recall impact report — batch " + b.getBatchNumber(), List.of(
                Section.fields("Batch", f), Section.fields("Impact summary", s),
                Section.table("Remaining inventory", List.of(new Column("warehouse", "Warehouse"), new Column("city", "City"),
                        new Column("quantity", "Quantity"), new Column("reserved", "Reserved")), (List<Map<String, Object>>) forward.get("stock")),
                Section.table("Customers & points of sale to contact", List.of(new Column("code", "Code"), new Column("name", "Customer"),
                        new Column("type", "Type"), new Column("city", "City"), new Column("phone", "Phone"), new Column("quantity", "Qty received")),
                        (List<Map<String, Object>>) forward.get("customers"))));
    }
}
