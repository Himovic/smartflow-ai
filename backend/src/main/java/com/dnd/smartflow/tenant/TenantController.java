package com.dnd.smartflow.tenant;

import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.security.Modules;
import com.dnd.smartflow.common.security.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Tenant configuration", description = "Industry profiles, enabled modules, terminology and platform tenants")
public class TenantController {
    private final TenantService service;
    private final TenantRepository tenants;
    private final IndustryProfileCatalog catalog;
    private final PermissionService perm;

    @GetMapping("/tenant/config")
    @Operation(summary = "Configuration of the current tenant")
    public Map<String, Object> config() {
        return view(service.current());
    }

    @PutMapping("/tenant/config")
    public Map<String, Object> update(@RequestBody TenantService.TenantConfigRequest req) {
        perm.require("tenant", "admin");
        return view(service.updateCurrent(req));
    }

    @PostMapping("/tenant/config/apply-profile/{profile}")
    @Operation(summary = "Apply an industry profile template to the current tenant")
    public Map<String, Object> applyProfile(@PathVariable String profile) {
        perm.require("tenant", "admin");
        return view(service.applyIndustryProfile(CurrentUser.require().tenantId(), profile));
    }

    @GetMapping("/tenant/industry-profiles")
    public Collection<Map<String, Object>> profiles() {
        return catalog.profiles().stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>(p);
            return m;
        }).toList();
    }

    @GetMapping("/tenant/modules")
    public Map<String, Object> modules() {
        return Map.of("core", Modules.CORE, "optional", Modules.OPTIONAL);
    }

    /** Organization selector for platform administrators. */
    @GetMapping("/platform/tenants")
    public List<Map<String, Object>> list() {
        CurrentUser u = CurrentUser.require();
        if (!u.can("platform", "read")) {
            Tenant t = service.get(u.homeTenantId());
            return List.of(summary(t));
        }
        return tenants.findAll().stream().map(this::summary).toList();
    }

    private Map<String, Object> summary(Tenant t) {
        return Map.of("id", t.getId(), "code", t.getCode(), "name", t.getName(), "industryProfile", t.getIndustryProfile(), "demo", t.isDemo());
    }

    private static Map<String, Object> view(Tenant t) {
        return TenantControllerViews.view(t);
    }
}
