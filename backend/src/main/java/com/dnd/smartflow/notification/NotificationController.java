package com.dnd.smartflow.notification;

import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.common.web.PageResponse;
import com.dnd.smartflow.notification.domain.Notification;
import com.dnd.smartflow.notification.domain.NotificationRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {
    private final NotificationRepository repository;

    private Specification<Notification> mine(boolean unreadOnly) {
        CurrentUser u = CurrentUser.require();
        return (root, q, cb) -> {
            Predicate target = cb.or(cb.equal(root.get("userId"), u.userId()),
                    cb.and(cb.isNull(root.get("userId")), cb.equal(root.get("targetRole"), u.role().name())));
            return unreadOnly ? cb.and(target, cb.isNull(root.get("readAt"))) : target;
        };
    }

    @GetMapping
    public PageResponse<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(defaultValue = "false") boolean unread) {
        return PageResponse.of(repository.findAll(mine(unread), PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"))),
                NotificationService::view);
    }

    @GetMapping("/unread-count")
    public Map<String, Object> unread() {
        return Map.of("count", repository.count(mine(true)));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public Map<String, Object> markRead(@PathVariable UUID id) {
        Notification n = repository.findOne(mine(false).and((r, q, cb) -> cb.equal(r.get("id"), id)))
                .orElseThrow(() -> ApiException.notFound("Notification"));
        n.setReadAt(Instant.now());
        repository.save(n);
        return Map.of("id", id, "readAt", n.getReadAt());
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Object> readAll() {
        var list = repository.findAll(mine(true));
        Instant now = Instant.now();
        list.forEach(n -> n.setReadAt(now));
        repository.saveAll(list);
        return Map.of("updated", list.size());
    }
}
