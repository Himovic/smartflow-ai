package com.dnd.smartflow.workflow.domain;

import com.dnd.smartflow.common.persistence.TenantEntity;
import com.dnd.smartflow.common.validation.OneOf;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Generated from the SmartFlow domain specification — table workflow_definitions. */
@Entity
@Table(name = "workflow_definitions")
@Getter
@Setter
public class WorkflowDefinition extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "workflow_key", nullable = false)
    private String workflowKey;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Size(max = 50)
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "description")
    private String description;

    @NotBlank
    @Size(max = 50)
    @Column(name = "initial_state", nullable = false)
    private String initialState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "states")
    private List<Map<String, Object>> states;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transitions")
    private List<Map<String, Object>> transitions;

    @Column(name = "active")
    private Boolean active;
}
