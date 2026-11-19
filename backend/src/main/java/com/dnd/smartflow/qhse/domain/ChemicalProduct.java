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

/** Generated from the SmartFlow domain specification — table chemical_products. */
@Entity
@Table(name = "chemical_products")
@Getter
@Setter
public class ChemicalProduct extends TenantEntity {

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @Size(max = 50)
    @Column(name = "cas_number")
    private String casNumber;

    @Size(max = 100)
    @Column(name = "hazard_class")
    private String hazardClass;

    @Column(name = "site_id")
    private UUID siteId;

    @Size(max = 255)
    @Column(name = "storage_location")
    private String storageLocation;

    @Column(name = "quantity")
    private Double quantity;

    @Size(max = 20)
    @Column(name = "unit")
    private String unit;

    @Column(name = "sds_document_id")
    private UUID sdsDocumentId;

    @Column(name = "sds_expiry")
    private LocalDate sdsExpiry;

    @Size(max = 500)
    @Column(name = "ghs_pictograms")
    private String ghsPictograms;

    @Column(name = "supplier_id")
    private UUID supplierId;
}
