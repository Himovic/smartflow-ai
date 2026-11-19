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

/** Generated from the SmartFlow domain specification — table attachments. */
@Entity
@Table(name = "attachments")
@Getter
@Setter
public class Attachment extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @NotNull
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @NotBlank
    @Size(max = 255)
    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Size(max = 100)
    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @NotBlank
    @Size(max = 500)
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Size(max = 30)
    @Column(name = "kind")
    private String kind;

    @Size(max = 255)
    @Column(name = "uploaded_by")
    private String uploadedBy;
}
