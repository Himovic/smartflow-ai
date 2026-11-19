package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.qhse.domain.PpeItem;
import com.dnd.smartflow.qhse.domain.PpeItemRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ppe-items")
@Tag(name = "Safety")
public class PpeItemController extends CrudController<PpeItem> {

    public PpeItemController(CrudSupport support, PpeItemRepository repository) {
        super(support, repository, PpeItem.class, "safety");
    }

    @Override
    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.ASC, "code");
    }
}
