package com.dnd.smartflow.common.tenant;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Holds the tenant of the current unit of work. {@link #ROOT} is a reserved identifier used only by
 * platform-level code paths (authentication, scheduled jobs that iterate over tenants, demo seeding)
 * where Hibernate's tenant filter must be bypassed. It is never assigned from user input.
 */
public final class TenantContext {

    public static final UUID ROOT = new UUID(0L, 0L);
    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static UUID require() {
        UUID id = CURRENT.get();
        if (id == null || ROOT.equals(id)) {
            throw new IllegalStateException("No tenant bound to the current context");
        }
        return id;
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Runs the supplier bound to the given tenant and restores the previous binding afterwards. */
    public static <T> T callAs(UUID tenantId, Supplier<T> work) {
        UUID previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return work.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static void runAs(UUID tenantId, Runnable work) {
        callAs(tenantId, () -> {
            work.run();
            return null;
        });
    }
}
