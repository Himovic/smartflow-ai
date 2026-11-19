package com.dnd.smartflow.ai.routing;

import com.dnd.smartflow.audit.AuditService;
import com.dnd.smartflow.common.security.PermissionService;
import com.dnd.smartflow.fleet.domain.RoutePlan;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/routing")
@RequiredArgsConstructor
@Tag(name = "Routes", description = "AI route optimization (OR-Tools service with deterministic built-in fallback)")
public class RoutingController {
    private final RouteOptimizationService service;
    private final PermissionService perm;
    private final AuditService audit;

    @PostMapping("/optimize")
    @Operation(summary = "Optimize the planned deliveries of a depot for a date under capacity, priority and time-window constraints")
    public Map<String, Object> optimize(@RequestBody RouteOptimizationService.OptimizeRequest req) {
        perm.require("routes", req.save() ? "write" : "read");
        return service.optimize(req);
    }

    @PostMapping("/plans/{id}/dispatch")
    public RoutePlan dispatch(@PathVariable UUID id) {
        perm.require("routes", "write");
        RoutePlan p = service.dispatch(id);
        audit.log("ROUTE_DISPATCHED", "RoutePlan", id, Map.of("vehicles", p.getVehiclesUsed()));
        return p;
    }
}
