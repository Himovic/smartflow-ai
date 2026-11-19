package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.qhse.domain.CleaningRecord;
import com.dnd.smartflow.qhse.domain.CleaningRecordRepository;
import com.dnd.smartflow.qhse.domain.CleaningScheduleRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/cleaning-records")
@Tag(name = "Hygiene")
public class CleaningRecordController extends CrudController<CleaningRecord> {
    private final CleaningScheduleRepository schedules;

    public CleaningRecordController(CrudSupport support, CleaningRecordRepository repository, CleaningScheduleRepository schedules) {
        super(support, repository, CleaningRecord.class, "hygiene");
        this.schedules = schedules;
    }

    public static Duration period(String frequency) {
        return switch (frequency == null ? "WEEKLY" : frequency) {
            case "DAILY" -> Duration.ofDays(1);
            case "BIWEEKLY" -> Duration.ofDays(14);
            case "MONTHLY" -> Duration.ofDays(30);
            case "QUARTERLY" -> Duration.ofDays(91);
            default -> Duration.ofDays(7);
        };
    }

    @Override
    protected void beforeCreate(CleaningRecord r) {
        if (r.getDoneAt() == null) r.setDoneAt(Instant.now());
        if (r.getDoneBy() == null) r.setDoneBy(CurrentUser.require().fullName());
    }

    @Override
    protected void afterCreate(CleaningRecord r) {
        schedules.findById(r.getScheduleId()).ifPresent(s -> {
            if (s.getLastDoneAt() == null || r.getDoneAt().isAfter(s.getLastDoneAt())) {
                s.setLastDoneAt(r.getDoneAt());
                s.setNextDueAt(r.getDoneAt().plus(period(s.getFrequency())));
                schedules.save(s);
            }
        });
    }
}
