package com.dnd.smartflow.common.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.UUID;

@NoRepositoryBean
public interface TenantRepository<E extends TenantEntity> extends JpaRepository<E, UUID>, JpaSpecificationExecutor<E> {
}
