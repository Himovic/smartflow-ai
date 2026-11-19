package com.dnd.smartflow.workflow;

import com.dnd.smartflow.common.persistence.TenantEntity;

import java.util.Map;

/** Side effects and guards attached to workflow transitions (implemented by business modules). */
public interface WorkflowListener {

    boolean supports(String entityType);

    /** Named guard evaluation; throw an ApiException to refuse the transition. */
    default void checkGuard(String guard, TenantEntity entity) {
    }

    default void onTransition(TenantEntity entity, String transition, String from, String to, Map<String, Object> definition) {
    }
}
