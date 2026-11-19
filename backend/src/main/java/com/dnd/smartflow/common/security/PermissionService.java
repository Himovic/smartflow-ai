package com.dnd.smartflow.common.security;

import com.dnd.smartflow.common.web.ApiException;
import org.springframework.stereotype.Component;

/** Imperative and SpEL ({@code @perm.can('nc','write')}) access checks. */
@Component("perm")
public class PermissionService {

    public boolean can(String module, String action) {
        return CurrentUser.find().map(u -> u.can(module, action)).orElse(false);
    }

    public void require(String module, String action) {
        if (!can(module, action)) {
            throw ApiException.forbidden("Missing permission " + module + ":" + action + " or module disabled");
        }
    }
}
