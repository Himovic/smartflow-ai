package com.dnd.smartflow.qhse.domain;

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

/** Generated from the SmartFlow domain specification — table waste_records. */
@Entity
@Table(name = "waste_records")
@Getter
@Setter
public class WasteRecord extends TenantEntity {

    @Size(max = 50)
    @Column(name = "reference")
    private String reference;

    @NotBlank
    @Size(max = 255)
    @Column(name = "waste_type", nullable = false)
    private String wasteType;

    @NotBlank
    @Size(max = 30)
    @OneOf({"HAZARDOUS", "NON_HAZARDOUS", "INERT", "RECYCLABLE"})
    @Column(name = "classification", nullable = false)
    private String classification;

    @Size(max = 50)
    @Column(name = "waste_code")
    private String wasteCode;

    @Column(name = "site_id")
    private UUID siteId;

    @NotNull
    @Column(name = "quantity_kg", nullable = false)
    private Double quantityKg;

    @Size(max = 30)
    @OneOf({"RECYCLING", "LANDFILL", "INCINERATION", "COMPOSTING", "RECOVERY", "TREATMENT"})
    @Column(name = "treatment")
    private String treatment;

    @Size(max = 255)
    @Column(name = "carrier")
    private String carrier;

    @Size(max = 255)
    @Column(name = "destination")
    private String destination;

    @Size(max = 100)
    @Column(name = "manifest_number")
    private String manifestNumber;

    @NotNull
    @Column(name = "recorded_date", nullable = false)
    private LocalDate recordedDate;

    @Column(name = "cost")
    private BigDecimal cost;
}
