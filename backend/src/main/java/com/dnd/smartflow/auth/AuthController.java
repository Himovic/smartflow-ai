package com.dnd.smartflow.auth;

import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.security.LoginRateLimiter;
import com.dnd.smartflow.common.security.Modules;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.iam.User;
import com.dnd.smartflow.iam.UserRepository;
import com.dnd.smartflow.tenant.Tenant;
import com.dnd.smartflow.tenant.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {
    private final AuthService auth;
    private final UserRepository users;
    private final TenantService tenantService;
    private final LoginHistoryRepository history;
    private final LoginRateLimiter limiter;

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ForgotRequest(@NotBlank @Email String email) {
    }

    public record ResetRequest(@NotBlank String token, @NotBlank String newPassword) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password; returns an access token and a rotating refresh token")
    public AuthService.TokenPair login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        throttle(http);
        return auth.login(req.email(), req.password(), ip(http), http.getHeader("User-Agent"));
    }

    @PostMapping("/refresh")
    public AuthService.TokenPair refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest http) {
        throttle(http);
        return auth.refresh(req.refreshToken(), ip(http), http.getHeader("User-Agent"));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req.refreshToken());
        return Map.of("status", "LOGGED_OUT");
    }

    @PostMapping("/forgot-password")
    public Map<String, Object> forgot(@Valid @RequestBody ForgotRequest req, HttpServletRequest http) {
        throttle(http);
        auth.forgotPassword(req.email());
        return Map.of("status", "If the account exists, a reset code has been sent by email.");
    }

    @PostMapping("/reset-password")
    public Map<String, Object> reset(@Valid @RequestBody ResetRequest req, HttpServletRequest http) {
        throttle(http);
        auth.resetPassword(req.token(), req.newPassword());
        return Map.of("status", "PASSWORD_RESET");
    }

    @GetMapping("/me")
    @Operation(summary = "Profile, effective permissions and tenant configuration of the signed-in user")
    public Map<String, Object> me() {
        CurrentUser cu = CurrentUser.require();
        User u = users.findById(cu.userId()).orElseThrow(() -> ApiException.notFound("User"));
        Tenant tenant = tenantService.get(cu.tenantId());
        Map<String, List<String>> permissions = new LinkedHashMap<>();
        List<String> all = new ArrayList<>(Modules.CORE);
        all.addAll(Modules.OPTIONAL);
        for (String module : all) {
            List<String> actions = new ArrayList<>();
            for (String a : List.of("read", "write", "delete", "approve", "admin")) {
                if (cu.can(module, a)) actions.add(a);
            }
            if (!actions.isEmpty()) permissions.put(module, actions);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("firstName", u.getFirstName());
        m.put("lastName", u.getLastName());
        m.put("jobTitle", u.getJobTitle());
        m.put("role", u.getRole());
        m.put("language", u.getLanguage());
        m.put("allSites", cu.allSites());
        m.put("siteIds", cu.siteIds());
        m.put("permissions", permissions);
        m.put("homeTenantId", cu.homeTenantId());
        m.put("tenant", com.dnd.smartflow.tenant.TenantControllerViews.view(tenant));
        m.put("demo", cu.demo());
        return m;
    }

    @PutMapping("/me/password")
    public Map<String, Object> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        auth.changePassword(CurrentUser.require().userId(), req.currentPassword(), req.newPassword());
        return Map.of("status", "PASSWORD_CHANGED");
    }

    @PutMapping("/me/language/{lang}")
    public Map<String, Object> language(@PathVariable String lang) {
        if (!List.of("fr", "en", "ar").contains(lang)) throw ApiException.badRequest("UNSUPPORTED_LANGUAGE", "Use fr, en or ar");
        User u = users.findById(CurrentUser.require().userId()).orElseThrow();
        u.setLanguage(lang);
        users.save(u);
        return Map.of("language", lang);
    }

    @GetMapping("/me/sessions")
    public List<Map<String, Object>> sessions() {
        return auth.sessions(CurrentUser.require().userId()).stream().map(s -> Map.<String, Object>of(
                "id", s.getId(), "createdAt", s.getCreatedAt(), "expiresAt", s.getExpiresAt(),
                "ipAddress", Objects.toString(s.getIpAddress(), ""), "userAgent", Objects.toString(s.getUserAgent(), ""))).toList();
    }

    @DeleteMapping("/me/sessions/{id}")
    public Map<String, Object> revoke(@PathVariable UUID id) {
        auth.revokeSession(CurrentUser.require().userId(), id);
        return Map.of("status", "REVOKED");
    }

    @GetMapping("/me/login-history")
    public List<LoginHistory> myHistory() {
        return history.findByUserIdOrderByOccurredAtDesc(CurrentUser.require().userId(), PageRequest.of(0, 50)).getContent();
    }

    private void throttle(HttpServletRequest http) {
        if (!limiter.tryAcquire(ip(http))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many attempts, please wait a few minutes");
        }
    }

    private static String ip(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        return fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : http.getRemoteAddr();
    }
}
