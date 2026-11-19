package com.dnd.smartflow.inventory.domain;

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

/** Generated from the SmartFlow domain specification — table warehouses. */
@Entity
@Table(name = "warehouses")
@Getter
@Setter
public class Warehouse extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "code", nullable = false)
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "site_id")
    private UUID siteId;

    @Size(max = 50)
    @OneOf({"CENTRAL", "REGIONAL", "PRODUCTION", "COLD_STORAGE", "TRANSIT"})
    @Column(name = "type")
    private String type;

    @Size(max = 100)
    @Column(name = "city")
    private String city;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "capacity_pallets")
    private Integer capacityPallets;

    @Column(name = "temperature_controlled")
    private Boolean temperatureControlled;

    @Column(name = "min_temp")
    private Double minTemp;

    @Column(name = "max_temp")
    private Double maxTemp;

    @Size(max = 255)
    @Column(name = "manager_name")
    private String managerName;

    @Size(max = 100)
    @Column(name = "external_ref")
    private String externalRef;
}
