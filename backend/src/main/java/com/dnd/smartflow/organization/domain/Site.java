package com.dnd.smartflow.organization.domain;

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

/** Generated from the SmartFlow domain specification — table sites. */
@Entity
@Table(name = "sites")
@Getter
@Setter
public class Site extends TenantEntity {

    @NotBlank
    @Size(max = 50)
    @Column(name = "code", nullable = false)
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Size(max = 50)
    @OneOf({"HEADQUARTERS", "PRODUCTION", "DISTRIBUTION", "WAREHOUSE", "OFFICE"})
    @Column(name = "type", nullable = false)
    private String type;

    @Size(max = 255)
    @Column(name = "address")
    private String address;

    @Size(max = 100)
    @Column(name = "city")
    private String city;

    @Size(max = 100)
    @Column(name = "region")
    private String region;

    @Size(max = 100)
    @Column(name = "country")
    private String country;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Size(max = 255)
    @Column(name = "manager_name")
    private String managerName;

    @Size(max = 50)
    @Column(name = "phone")
    private String phone;

    @Column(name = "active")
    private Boolean active;
}
