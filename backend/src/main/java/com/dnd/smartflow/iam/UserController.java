package com.dnd.smartflow.iam;

import com.dnd.smartflow.audit.AuditService;
import com.dnd.smartflow.auth.AuthService;
import com.dnd.smartflow.auth.LoginHistory;
import com.dnd.smartflow.auth.LoginHistoryRepository;
import com.dnd.smartflow.auth.RefreshTokenRepository;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.security.PermissionService;
import com.dnd.smartflow.common.security.Role;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.common.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User administration")
public class UserController {
    private final UserRepository users;
    private final AuthService auth;
    private final RefreshTokenRepository tokens;
    private final LoginHistoryRepository history;
    private final PermissionService perm;
    private final AuditService audit;

    public record UserDto(UUID id, String email, String firstName, String lastName, Role role, String jobTitle, String phone, UUID departmentId,
                          UUID employeeId, boolean allSites, List<UUID> siteIds, String language, boolean active, Instant lastLoginAt, Instant createdAt) {
        static UserDto of(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getRole(), u.getJobTitle(), u.getPhone(), u.getDepartmentId(),
                    u.getEmployeeId(), u.isAllSites(), u.getSiteIds(), u.getLanguage(), u.isActive(), u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record UserRequest(@NotBlank @Email String email, @NotBlank String firstName, @NotBlank String lastName, @NotNull Role role,
                              String jobTitle, String phone, UUID departmentId, UUID employeeId, Boolean allSites, List<UUID> siteIds,
                              String language, String password) {
    }

    @GetMapping
    public PageResponse<UserDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
                                      @RequestParam(required = false) String q, @RequestParam(required = false) Role role) {
        perm.require("users", "read");
        return PageResponse.of(users.findAll((r, cq, cb) -> cb.and(
                q == null || q.isBlank() ? cb.conjunction() : cb.or(cb.like(cb.lower(r.get("email")), "%" + q.toLowerCase() + "%"),
                        cb.like(cb.lower(r.get("lastName")), "%" + q.toLowerCase() + "%"), cb.like(cb.lower(r.get("firstName")), "%" + q.toLowerCase() + "%")),
                role == null ? cb.conjunction() : cb.equal(r.get("role"), role)), PageRequest.of(page, Math.min(size, 200), Sort.by("lastName"))), UserDto::of);
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> roles() {
        return Arrays.stream(Role.values()).map(r -> Map.<String, Object>of("role", r, "grants", r.grants(), "allSites", r.seesAllSitesByDefault())).toList();
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable UUID id) {
        perm.require("users", "read");
        return UserDto.of(load(id));
    }

    @PostMapping
    @Transactional
    public UserDto create(@Valid @RequestBody UserRequest req) {
        perm.require("users", "admin");
        guardRole(req.role());
        if (users.findByEmailIgnoreCase(req.email()).isPresent()) throw ApiException.conflict("EMAIL_TAKEN", "This e-mail is already used");
        User u = new User();
        apply(u, req);
        auth.applyNewPassword(u, req.password());
        u = users.save(u);
        audit.log("USER_CREATED", "User", u.getId(), Map.of("email", u.getEmail(), "role", u.getRole().name()));
        return UserDto.of(u);
    }

    @PutMapping("/{id}")
    @Transactional
    public UserDto update(@PathVariable UUID id, @Valid @RequestBody UserRequest req) {
        perm.require("users", "admin");
        guardRole(req.role());
        User u = load(id);
        Role before = u.getRole();
        apply(u, req);
        if (req.password() != null && !req.password().isBlank()) {
            if (CurrentUser.require().demo()) throw ApiException.forbidden("Passwords of demonstration accounts cannot be changed");
            auth.applyNewPassword(u, req.password());
            tokens.revokeAllForUser(u.getId(), Instant.now());
        }
        audit.log("USER_UPDATED", "User", id, Map.of("roleFrom", before.name(), "roleTo", u.getRole().name()));
        return UserDto.of(users.save(u));
    }

    @PostMapping("/{id}/activation")
    @Transactional
    public UserDto activation(@PathVariable UUID id, @RequestParam boolean active) {
        perm.require("users", "admin");
        User u = load(id);
        if (u.getId().equals(CurrentUser.require().userId())) throw ApiException.badRequest("SELF_DEACTIVATION", "You cannot deactivate your own account");
        if (CurrentUser.require().demo() && u.getEmail().endsWith("@smartflowai.demo")) {
            throw ApiException.forbidden("Shared demonstration accounts cannot be deactivated");
        }
        u.setActive(active);
        if (!active) tokens.revokeAllForUser(u.getId(), Instant.now());
        audit.log(active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "User", id, Map.of());
        return UserDto.of(users.save(u));
    }

    @GetMapping("/{id}/login-history")
    public List<LoginHistory> loginHistory(@PathVariable UUID id) {
        perm.require("users", "read");
        load(id);
        return history.findByUserIdOrderByOccurredAtDesc(id, PageRequest.of(0, 100)).getContent();
    }

    @GetMapping("/login-history")
    public PageResponse<LoginHistory> tenantHistory(@RequestParam(defaultValue = "0") int page) {
        perm.require("users", "admin");
        return PageResponse.of(history.findByTenantIdOrderByOccurredAtDesc(CurrentUser.require().tenantId(), PageRequest.of(page, 50)));
    }

    private void guardRole(Role role) {
        if (role == Role.SUPER_ADMIN && CurrentUser.require().role() != Role.SUPER_ADMIN) {
            throw ApiException.forbidden("Only a platform administrator can grant SUPER_ADMIN");
        }
    }

    private void apply(User u, UserRequest req) {
        u.setEmail(req.email().trim().toLowerCase(Locale.ROOT));
        u.setFirstName(req.firstName());
        u.setLastName(req.lastName());
        u.setRole(req.role());
        u.setJobTitle(req.jobTitle());
        u.setPhone(req.phone());
        u.setDepartmentId(req.departmentId());
        u.setEmployeeId(req.employeeId());
        u.setAllSites(Boolean.TRUE.equals(req.allSites()));
        u.setSiteIds(req.siteIds() == null ? new ArrayList<>() : new ArrayList<>(req.siteIds()));
        u.setLanguage(req.language() == null ? "fr" : req.language());
    }

    private User load(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }
}
