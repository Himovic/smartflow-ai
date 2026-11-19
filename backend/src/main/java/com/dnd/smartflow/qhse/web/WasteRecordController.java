package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.qhse.domain.WasteRecord;
import com.dnd.smartflow.qhse.domain.WasteRecordRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/waste-records")
@Tag(name = "Environment")
public class WasteRecordController extends CrudController<WasteRecord> {

    public WasteRecordController(CrudSupport support, WasteRecordRepository repository) {
        super(support, repository, WasteRecord.class, "environment");
    }

    @Override
    protected String referencePrefix() {
        return "WST";
    }
}
