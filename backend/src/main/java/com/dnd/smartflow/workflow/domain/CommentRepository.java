package com.dnd.smartflow.workflow.domain;

import com.dnd.smartflow.common.persistence.TenantRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends TenantRepository<Comment> {
}
