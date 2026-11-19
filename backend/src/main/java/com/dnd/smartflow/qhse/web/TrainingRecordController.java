package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.qhse.domain.TrainingRecord;
import com.dnd.smartflow.qhse.domain.TrainingRecordRepository;
import com.dnd.smartflow.qhse.domain.TrainingRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/training-records")
@Tag(name = "Training & certifications")
public class TrainingRecordController extends CrudController<TrainingRecord> {
    private final TrainingRepository trainings;

    public TrainingRecordController(CrudSupport support, TrainingRecordRepository repository, TrainingRepository trainings) {
        super(support, repository, TrainingRecord.class, "training");
        this.trainings = trainings;
    }

    @Override
    protected void beforeSave(TrainingRecord r) {
        if (r.getStatus() == null) r.setStatus("PLANNED");
        if ("COMPLETED".equals(r.getStatus()) && r.getExpiryDate() == null && r.getSessionDate() != null) {
            trainings.findById(r.getTrainingId()).ifPresent(t -> {
                if (t.getValidityMonths() != null && t.getValidityMonths() > 0) r.setExpiryDate(r.getSessionDate().plusMonths(t.getValidityMonths()));
            });
        }
    }
}
