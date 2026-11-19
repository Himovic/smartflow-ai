package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.common.security.Role;
import com.dnd.smartflow.notification.NotificationService;
import com.dnd.smartflow.qhse.domain.EnvironmentalReading;
import com.dnd.smartflow.qhse.domain.EnvironmentalReadingRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/environmental-readings")
@Tag(name = "Environmental & hygiene monitoring", description = "Temperature, humidity, water, air, energy readings (manual or IoT)")
public class EnvironmentalReadingController extends CrudController<EnvironmentalReading> {
    private static final Set<String> HYGIENE_TYPES = Set.of("TEMPERATURE", "HUMIDITY", "WATER_PH", "WATER_CHLORINE");
    private final NotificationService notifications;

    public EnvironmentalReadingController(CrudSupport support, EnvironmentalReadingRepository repository, NotificationService notifications) {
        super(support, repository, EnvironmentalReading.class, "environment");
        this.notifications = notifications;
    }

    public static void evaluate(EnvironmentalReading r) {
        boolean ok = true;
        if (r.getMinThreshold() != null && r.getReadingValue() < r.getMinThreshold()) ok = false;
        if (r.getMaxThreshold() != null && r.getReadingValue() > r.getMaxThreshold()) ok = false;
        r.setWithinLimits(ok);
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.DESC, "recordedAt");
    }

    @Override
    protected void beforeSave(EnvironmentalReading r) {
        if (r.getRecordedAt() == null) r.setRecordedAt(Instant.now());
        if (r.getSource() == null) r.setSource("MANUAL");
        evaluate(r);
    }

    @Override
    protected void afterCreate(EnvironmentalReading r) {
        if (Boolean.FALSE.equals(r.getWithinLimits())) {
            boolean hygiene = HYGIENE_TYPES.contains(r.getReadingType());
            notifications.notifyRoles(new NotificationService.Message("READING_OUT_OF_LIMITS",
                    r.getReadingType().replace('_', ' ') + " out of limits: " + r.getReadingValue() + " " + r.getUnit(),
                    (r.getLocation() == null ? "" : r.getLocation() + " — ") + "limits " + r.getMinThreshold() + " / " + r.getMaxThreshold(),
                    "WARNING", hygiene ? "HYGIENE" : "ENVIRONMENT", "EnvironmentalReading", r.getId()),
                    hygiene ? Role.HYGIENE_MANAGER : Role.ENVIRONMENT_MANAGER, Role.QHSE_DIRECTOR);
        }
    }
}
