package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.qhse.domain.CleaningSchedule;
import com.dnd.smartflow.qhse.domain.CleaningScheduleRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cleaning-schedules")
@Tag(name = "Hygiene")
public class CleaningScheduleController extends CrudController<CleaningSchedule> {

    public CleaningScheduleController(CrudSupport support, CleaningScheduleRepository repository) {
        super(support, repository, CleaningSchedule.class, "hygiene");
    }
}
