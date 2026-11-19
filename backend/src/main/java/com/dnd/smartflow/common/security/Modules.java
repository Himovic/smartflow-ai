package com.dnd.smartflow.common.security;

import java.util.List;
import java.util.Set;

/** Functional modules a tenant can enable or disable. Core modules cannot be disabled. */
public final class Modules {
    public static final Set<String> CORE = Set.of("dashboard", "organization", "users", "tenant", "notifications", "workflows",
            "reports", "platform", "demo");

    public static final List<String> OPTIONAL = List.of(
            // Pillar 1 — Smart distribution & operations
            "catalog", "inventory", "traceability", "distribution", "fleet", "routes", "assets",
            // Pillar 2 — Integrated QHSE
            "quality", "hygiene", "safety", "environment", "risk", "inspections", "incidents", "nc", "capa", "audits",
            "documents", "training",
            // Platform capabilities
            "integrations", "analytics", "ai");

    private Modules() {
    }
}
