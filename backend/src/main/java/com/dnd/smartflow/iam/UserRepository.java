package com.dnd.smartflow.iam;

import com.dnd.smartflow.common.persistence.TenantRepository;
import com.dnd.smartflow.common.security.Role;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends TenantRepository<User> {
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPasswordResetHash(String hash);

    List<User> findByRoleInAndActiveTrue(List<Role> roles);

    long countByActiveTrue();
}
