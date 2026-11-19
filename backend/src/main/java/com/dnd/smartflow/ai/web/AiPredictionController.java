package com.dnd.smartflow.ai.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.ai.domain.AiPrediction;
import com.dnd.smartflow.ai.domain.AiPredictionRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-predictions")
@Tag(name = "AI Center")
public class AiPredictionController extends CrudController<AiPrediction> {

    public AiPredictionController(CrudSupport support, AiPredictionRepository repository) {
        super(support, repository, AiPrediction.class, "ai");
    }
}
