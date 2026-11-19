package com.dnd.smartflow.qhse.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.qhse.domain.CapaAction;
import com.dnd.smartflow.qhse.domain.CapaActionRepository;
import com.dnd.smartflow.workflow.WorkflowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/capa")
@Tag(name = "CAPA")
public class CapaActionController extends CrudController<CapaAction> {
    private final WorkflowService workflow;
    private final com.dnd.smartflow.notification.NotificationService notifications;

    public CapaActionController(CrudSupport support, CapaActionRepository repository, WorkflowService workflow,
                                com.dnd.smartflow.notification.NotificationService notifications) {
        super(support, repository, CapaAction.class, "capa");
        this.workflow = workflow;
        this.notifications = notifications;
    }

    /** Creates a CAPA from another module (NC, incident, audit…) through the same rules as the API. */
    public CapaAction createLinked(CapaAction body) {
        return create(body);
    }

    @Override
    protected String referencePrefix() {
        return "CAPA";
    }

    @Override
    protected void beforeCreate(CapaAction c) {
        c.setStatus(workflow.initialState("CapaAction", c));
        if (c.getPriority() == null) c.setPriority("MEDIUM");
        if (c.getDueDate() == null) c.setDueDate(LocalDate.now().plusDays(30));
        if (c.getOwnerUserId() == null && c.getOwnerName() == null) {
            c.setOwnerUserId(CurrentUser.require().userId());
            c.setOwnerName(CurrentUser.require().fullName());
        }
    }

    @Override
    protected void afterCreate(CapaAction c) {
        workflow.recordCreation("CapaAction", c.getId(), c.getStatus());
        notifications.notifyUser(c.getOwnerUserId(), new com.dnd.smartflow.notification.NotificationService.Message("CAPA_ASSIGNED",
                "Action assigned: " + c.getReference(), c.getTitle() + " — due " + c.getDueDate(), "INFO", "CAPA", "CapaAction", c.getId()));
    }
}
