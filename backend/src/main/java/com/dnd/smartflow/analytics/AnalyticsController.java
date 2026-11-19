package com.dnd.smartflow.analytics;

import com.dnd.smartflow.ai.forecast.ForecastService;
import com.dnd.smartflow.common.security.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics & control tower")
public class AnalyticsController {
    private final DashboardService dashboards;
    private final AlertService alerts;
    private final ForecastService forecasts;
    private final PermissionService perm;

    @GetMapping("/dashboard/executive")
    @Operation(summary = "Executive control tower (KPIs, operational risk, trends, sites)")
    public Map<String, Object> executive(@RequestParam(required = false) UUID siteId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        perm.require("dashboard", "read");
        return dashboards.executive(DashboardService.Filter.of(siteId, from, to));
    }

    @GetMapping("/dashboard/qhse")
    public Map<String, Object> qhse(@RequestParam(required = false) UUID siteId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        perm.require("dashboard", "read");
        perm.require("nc", "read");
        return dashboards.qhse(DashboardService.Filter.of(siteId, from, to));
    }

    @GetMapping("/dashboard/distribution")
    public Map<String, Object> distribution(@RequestParam(required = false) UUID siteId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        perm.require("dashboard", "read");
        perm.require("distribution", "read");
        return dashboards.distribution(DashboardService.Filter.of(siteId, from, to), forecasts.overview());
    }

    @GetMapping("/alerts")
    public List<AlertService.Alert> alerts() {
        perm.require("dashboard", "read");
        return alerts.alerts();
    }
}
