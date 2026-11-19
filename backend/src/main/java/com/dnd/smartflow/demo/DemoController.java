package com.dnd.smartflow.demo;

import com.dnd.smartflow.common.security.PermissionService;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.tenant.TenantRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Demo", description = "Public demonstration landing data and demo administration")
public class DemoController {
    private final DemoService service;
    private final TenantRepository tenants;
    private final PermissionService perm;

    @Value("${smartflow.demo.enabled:true}")
    private boolean enabled;

    @Value("${smartflow.public-url:http://localhost:4200}")
    private String publicUrl;

    @GetMapping("/api/v1/public/demo")
    @Operation(summary = "Public information shown on the demo landing page (no authentication)")
    public Map<String, Object> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("ready", enabled && tenants.findByCode(DemoDataSeeder.TENANT_CODE).isPresent() && !service.isRunning());
        m.put("seeding", service.isRunning());
        m.put("product", "SmartFlow AI + QHSE");
        m.put("tagline", "One intelligent platform connecting industrial operations, distribution and QHSE.");
        m.put("developer", "D&D | Data&Development");
        m.put("organization", "Atlas Industries Group (fictional) — FMCG, Algeria");
        m.put("url", publicUrl);
        m.put("lastReset", service.lastReset());
        if (enabled) {
            m.put("password", DemoDataSeeder.PASSWORD);
            m.put("accounts", List.of(
                    account("SUPER_ADMIN", "demo.admin@smartflowai.demo", "Yacine Bensalem", "Full platform access, administration, integrations, demo reset"),
                    account("QHSE_DIRECTOR", "demo.qhse@smartflowai.demo", "Amina Khelifi", "QHSE control tower, approvals, audits, risk"),
                    account("QUALITY_MANAGER", "demo.quality@smartflowai.demo", "Karim Boumediene", "Non-conformities, CAPA, inspections, traceability (Constantine & Sétif)"),
                    account("SAFETY_MANAGER", "demo.safety@smartflowai.demo", "Nadia Belkacem", "Incidents, risk assessment, permits, training"),
                    account("LOGISTICS_MANAGER", "demo.logistics@smartflowai.demo", "Sofiane Hamidi", "Orders, deliveries, fleet, AI route optimization"),
                    account("EMPLOYEE", "demo.employee@smartflowai.demo", "Rachid Ouali", "Field reporting: non-conformities, incidents, inspections"),
                    account("GENERAL_MANAGER", "demo.gm@smartflowai.demo", "Mourad Benali", "Executive dashboards and reports"),
                    account("HYGIENE_MANAGER", "demo.hygiene@smartflowai.demo", "Lynda Saadi", "Hygiene inspections, cleaning plans, pest control"),
                    account("ENVIRONMENT_MANAGER", "demo.environment@smartflowai.demo", "Farid Zerrouki", "Waste, energy, water, CO2, environmental incidents"),
                    account("AUDITOR", "demo.auditor@smartflowai.demo", "Samia Ait Ahmed", "Audits and read access to QHSE records")));
        }
        m.put("notice", "All data is fictional. ERP, CMMS, GPS and IoT connectors are SIMULATED. These credentials are for demonstration only "
                + "and are never used in production environments.");
        return m;
    }

    private static Map<String, Object> account(String role, String email, String name, String scope) {
        return Map.of("role", role, "email", email, "name", name, "scope", scope);
    }

    @PostMapping("/api/v1/demo/reset")
    @Operation(summary = "Reset the demonstration data to its initial state (platform administrators only)")
    public Map<String, Object> reset() {
        perm.require("platform", "admin");
        if (!enabled) throw ApiException.forbidden("Demo mode is disabled");
        return service.reset();
    }

    @GetMapping("/api/v1/demo/status")
    public Map<String, Object> status() {
        perm.require("demo", "read");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("running", service.isRunning());
        m.put("lastReset", service.lastReset());
        m.put("tenants", tenants.findAll().stream().filter(t -> t.isDemo()).map(t -> Map.of("code", t.getCode(), "name", t.getName())).toList());
        return m;
    }
}
