package com.dnd.smartflow.ai.domain;

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

/** Generated from the SmartFlow domain specification — table ai_predictions. */
@Entity
@Table(name = "ai_predictions")
@Getter
@Setter
public class AiPrediction extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "prediction_type", nullable = false)
    private String predictionType;

    @Size(max = 50)
    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Size(max = 100)
    @Column(name = "model")
    private String model;

    @Column(name = "horizon")
    private Integer horizon;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result")
    private Map<String, Object> result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics")
    private Map<String, Object> metrics;
}
