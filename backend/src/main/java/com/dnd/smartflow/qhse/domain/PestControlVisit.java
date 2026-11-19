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

/** Generated from the SmartFlow domain specification — table pest_control_visits. */
@Entity
@Table(name = "pest_control_visits")
@Getter
@Setter
public class PestControlVisit extends TenantEntity {

    @NotNull
    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Size(max = 255)
    @Column(name = "provider")
    private String provider;

    @NotNull
    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "bait_stations_checked")
    private Integer baitStationsChecked;

    @Column(name = "activity_found")
    private Boolean activityFound;

    @Column(name = "findings")
    private String findings;

    @Column(name = "next_visit")
    private LocalDate nextVisit;

    @Size(max = 30)
    @OneOf({"SCHEDULED", "DONE", "ACTION_REQUIRED"})
    @Column(name = "status")
    private String status;
}
