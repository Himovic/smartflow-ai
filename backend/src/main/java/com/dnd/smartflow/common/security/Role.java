package com.dnd.smartflow.common.security;

import java.util.Set;

/**
 * Platform roles and their default permission grants. Permissions follow the {@code module:action} pattern
 * ({@code *} wildcards allowed). Actions: read, write, delete, approve, admin.
 */
public enum Role {
    SUPER_ADMIN(Set.of("*:*")),
    TENANT_ADMIN(Set.of("*:*")),
    GENERAL_MANAGER(Set.of("*:read", "*:approve", "reports:*", "ai:*", "analytics:*")),
    SITE_MANAGER(Set.of("*:read", "organization:write", "assets:write", "inventory:write", "distribution:write", "nc:write",
            "incidents:write", "inspections:write", "capa:write", "capa:approve", "hygiene:write", "reports:*", "ai:read")),
    QHSE_DIRECTOR(Set.of("*:read", "quality:*", "hygiene:*", "safety:*", "environment:*", "risk:*", "inspections:*", "incidents:*",
            "nc:*", "capa:*", "audits:*", "documents:*", "training:*", "traceability:*", "workflows:*", "reports:*", "ai:*", "analytics:*")),
    QUALITY_MANAGER(Set.of("*:read", "quality:*", "inspections:*", "nc:*", "capa:*", "audits:*", "documents:*", "traceability:*",
            "catalog:write", "reports:*", "ai:read", "analytics:read")),
    HYGIENE_MANAGER(Set.of("*:read", "hygiene:*", "inspections:*", "nc:write", "nc:approve", "capa:write", "capa:approve", "documents:write",
            "environment:write", "reports:*", "ai:read")),
    SAFETY_MANAGER(Set.of("*:read", "safety:*", "incidents:*", "risk:*", "training:*", "inspections:*", "capa:*", "nc:write",
            "documents:write", "reports:*", "ai:read")),
    ENVIRONMENT_MANAGER(Set.of("*:read", "environment:*", "incidents:*", "risk:write", "inspections:*", "capa:write", "capa:approve",
            "nc:write", "documents:write", "reports:*", "ai:read")),
    PRODUCTION_MANAGER(Set.of("*:read", "assets:write", "inventory:write", "nc:write", "capa:write", "inspections:write", "incidents:write", "ai:read")),
    LOGISTICS_MANAGER(Set.of("*:read", "distribution:*", "fleet:*", "routes:*", "inventory:*", "catalog:write", "traceability:read",
            "incidents:write", "nc:write", "reports:*", "ai:*")),
    WAREHOUSE_MANAGER(Set.of("*:read", "inventory:*", "hygiene:write", "inspections:write", "nc:write", "incidents:write", "traceability:read")),
    MAINTENANCE_MANAGER(Set.of("*:read", "assets:*", "incidents:write", "nc:write", "inspections:write", "safety:write", "ai:read")),
    SUPERVISOR(Set.of("*:read", "nc:write", "incidents:write", "inspections:write", "capa:write", "hygiene:write")),
    EMPLOYEE(Set.of("dashboard:read", "nc:read", "nc:write", "incidents:read", "incidents:write", "inspections:read", "inspections:write",
            "documents:read", "training:read", "notifications:*", "capa:read", "ai:read", "catalog:read", "organization:read", "inventory:read",
            "traceability:read", "assets:read")),
    AUDITOR(Set.of("*:read", "audits:*", "nc:write", "inspections:write")),
    VIEWER(Set.of("*:read"));

    private final Set<String> grants;

    Role(Set<String> grants) {
        this.grants = grants;
    }

    public Set<String> grants() {
        return grants;
    }

    /** True when the role grants {@code module:action}. Platform administration ({@code platform:*}) is reserved to SUPER_ADMIN. */
    public boolean allows(String module, String action) {
        if ("platform".equals(module)) {
            return this == SUPER_ADMIN;
        }
        if (("users".equals(module) || "tenant".equals(module) || "integrations".equals(module) || "demo".equals(module))
                && !"read".equals(action)) {
            return this == SUPER_ADMIN || this == TENANT_ADMIN;
        }
        for (String grant : grants) {
            int idx = grant.indexOf(':');
            String m = grant.substring(0, idx);
            String a = grant.substring(idx + 1);
            if ((m.equals("*") || m.equals(module)) && (a.equals("*") || a.equals(action))) {
                return true;
            }
        }
        return false;
    }

    /** Roles that see every site of their tenant regardless of site assignment. */
    public boolean seesAllSitesByDefault() {
        return switch (this) {
            case SUPER_ADMIN, TENANT_ADMIN, GENERAL_MANAGER, QHSE_DIRECTOR, AUDITOR, VIEWER -> true;
            default -> false;
        };
    }
}
