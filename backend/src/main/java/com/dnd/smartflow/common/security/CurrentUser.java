package com.dnd.smartflow.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Authenticated principal resolved from the access token. */
public record CurrentUser(UUID userId, UUID tenantId, UUID homeTenantId, String email, String fullName, Role role,
                          boolean allSites, List<UUID> siteIds, Set<String> enabledModules, boolean demo) {

    public static Optional<CurrentUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentUser cu) {
            return Optional.of(cu);
        }
        return Optional.empty();
    }

    public static CurrentUser require() {
        return find().orElseThrow(() -> new IllegalStateException("No authenticated user"));
    }

    public boolean can(String module, String action) {
        boolean moduleOn = Modules.CORE.contains(module) || enabledModules == null || enabledModules.contains(module);
        return moduleOn && role.allows(module, action);
    }

    public boolean canAccessSite(UUID siteId) {
        return allSites || siteId == null || (siteIds != null && siteIds.contains(siteId));
    }
}
