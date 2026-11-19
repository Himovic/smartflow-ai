package com.dnd.smartflow.notification.web;

import com.dnd.smartflow.common.crud.CrudController;
import com.dnd.smartflow.common.crud.CrudSupport;
import com.dnd.smartflow.notification.domain.NotificationRule;
import com.dnd.smartflow.notification.domain.NotificationRuleRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notification-rules")
@Tag(name = "Notifications")
public class NotificationRuleController extends CrudController<NotificationRule> {

    public NotificationRuleController(CrudSupport support, NotificationRuleRepository repository) {
        super(support, repository, NotificationRule.class, "notifications");
    }
}
