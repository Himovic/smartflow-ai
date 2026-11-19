package com.dnd.smartflow.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID> {
    Page<LoginHistory> findByUserIdOrderByOccurredAtDesc(UUID userId, Pageable pageable);

    Page<LoginHistory> findByTenantIdOrderByOccurredAtDesc(UUID tenantId, Pageable pageable);
}
