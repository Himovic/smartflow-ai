package com.dnd.smartflow.auth;

import com.dnd.smartflow.common.security.*;
import com.dnd.smartflow.common.tenant.TenantContext;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.iam.User;
import com.dnd.smartflow.iam.UserRepository;
import com.dnd.smartflow.notification.MailGateway;
import com.dnd.smartflow.tenant.Tenant;
import com.dnd.smartflow.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final int MAX_FAILED = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final Pattern STRONG = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,}$");

    private final UserRepository users;
    private final TenantRepository tenants;
    private final RefreshTokenRepository refreshTokens;
    private final LoginHistoryRepository history;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final JwtProperties jwtProps;
    private final CredentialCipher cipher;
    private final MailGateway mail;

    public record TokenPair(String accessToken, String refreshToken, long expiresIn, UUID userId) {
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair login(String email, String password, String ip, String userAgent) {
        return TenantContext.callAs(TenantContext.ROOT, () -> {
            Optional<User> found = users.findByEmailIgnoreCase(email.trim());
            if (found.isEmpty()) {
                encoder.matches(password, "$2a$11$invalidinvalidinvalidinvalidinvalidinvalidinvalidinval"); // timing equalisation
                recordLogin(null, null, email, false, "UNKNOWN_USER", ip, userAgent);
                throw new BadCredentialsException("Invalid email or password");
            }
            User user = found.get();
            Instant now = Instant.now();
            if (!user.isActive()) {
                recordLogin(user, email, false, "INACTIVE", ip, userAgent);
                throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "This account is deactivated");
            }
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
                recordLogin(user, email, false, "LOCKED", ip, userAgent);
                throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_LOCKED", "Too many failed attempts. Try again later.");
            }
            if (!encoder.matches(password, user.getPasswordHash())) {
                user.setFailedAttempts(user.getFailedAttempts() + 1);
                if (user.getFailedAttempts() >= MAX_FAILED) {
                    user.setLockedUntil(now.plus(LOCK));
                    user.setFailedAttempts(0);
                }
                users.save(user);
                recordLogin(user, email, false, "BAD_PASSWORD", ip, userAgent);
                throw new BadCredentialsException("Invalid email or password");
            }
            Tenant tenant = tenants.findById(user.getTenantId()).orElseThrow();
            if (!tenant.isActive()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "TENANT_DISABLED", "Organization is deactivated");
            }
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(now);
            users.save(user);
            recordLogin(user, email, true, "OK", ip, userAgent);
            return issue(user, ip, userAgent);
        });
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair refresh(String rawRefreshToken, String ip, String userAgent) {
        return TenantContext.callAs(TenantContext.ROOT, () -> {
            RefreshToken token = refreshTokens.findByTokenHash(CredentialCipher.sha256(rawRefreshToken))
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "Invalid session"));
            if (token.getRevokedAt() != null) {
                // Reuse of a rotated token: assume theft and terminate every session of the user.
                refreshTokens.revokeAllForUser(token.getUserId(), Instant.now());
                throw new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_REUSED", "Session expired, please sign in again");
            }
            if (!token.isActive()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "Session expired, please sign in again");
            }
            User user = users.findById(token.getUserId()).filter(User::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "Session expired"));
            token.setRevokedAt(Instant.now());
            refreshTokens.save(token);
            return issue(user, ip, userAgent);
        });
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(CredentialCipher.sha256(rawRefreshToken)).ifPresent(t -> {
            t.setRevokedAt(Instant.now());
            refreshTokens.save(t);
        });
    }

    @Transactional(readOnly = true)
    public List<RefreshToken> sessions(UUID userId) {
        return refreshTokens.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, Instant.now());
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        RefreshToken t = refreshTokens.findById(sessionId).filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Session"));
        t.setRevokedAt(Instant.now());
        refreshTokens.save(t);
    }

    /** Always answers the same way to avoid account enumeration. */
    @Transactional
    public void forgotPassword(String email) {
        TenantContext.runAs(TenantContext.ROOT, () -> users.findByEmailIgnoreCase(email.trim()).filter(User::isActive).ifPresent(user -> {
            Tenant tenant = tenants.findById(user.getTenantId()).orElseThrow();
            if (tenant.isDemo()) {
                log.info("Password reset requested for demo account {} — ignored (demo accounts are shared)", email);
                return;
            }
            String raw = cipher.randomToken(32);
            user.setPasswordResetHash(CredentialCipher.sha256(raw));
            user.setPasswordResetExpires(Instant.now().plus(Duration.ofMinutes(30)));
            users.save(user);
            mail.send(user.getEmail(), "SmartFlow AI — password reset",
                    "Use this code to reset your password within 30 minutes:\n\n" + raw + "\n\nIf you did not request it, ignore this message.");
        }));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        TenantContext.runAs(TenantContext.ROOT, () -> {
            User user = users.findByPasswordResetHash(CredentialCipher.sha256(rawToken))
                    .filter(u -> u.getPasswordResetExpires() != null && u.getPasswordResetExpires().isAfter(Instant.now()))
                    .orElseThrow(() -> ApiException.badRequest("INVALID_RESET_TOKEN", "The reset code is invalid or expired"));
            applyNewPassword(user, newPassword);
            user.setPasswordResetHash(null);
            user.setPasswordResetExpires(null);
            users.save(user);
            refreshTokens.revokeAllForUser(user.getId(), Instant.now());
        });
    }

    @Transactional
    public void changePassword(UUID userId, String current, String newPassword) {
        CurrentUser cu = CurrentUser.require();
        if (cu.demo()) {
            throw ApiException.forbidden("Passwords of shared demonstration accounts cannot be changed");
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!encoder.matches(current, user.getPasswordHash())) {
            throw ApiException.badRequest("BAD_PASSWORD", "Current password is incorrect");
        }
        applyNewPassword(user, newPassword);
        users.save(user);
    }

    public void applyNewPassword(User user, String newPassword) {
        if (newPassword == null || !STRONG.matcher(newPassword).matches()) {
            throw ApiException.badRequest("WEAK_PASSWORD",
                    "Password must have at least 10 characters with upper and lower case letters, a digit and a symbol");
        }
        user.setPasswordHash(encoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
    }

    private TokenPair issue(User user, String ip, String userAgent) {
        String raw = cipher.randomToken(48);
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTenantId(user.getTenantId());
        rt.setTokenHash(CredentialCipher.sha256(raw));
        rt.setExpiresAt(Instant.now().plus(jwtProps.refreshTokenTtl()));
        rt.setIpAddress(ip);
        rt.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(500, userAgent.length())));
        refreshTokens.save(rt);
        String access = jwt.createAccessToken(user.getId(), user.getTenantId(), user.getEmail(), user.getRole());
        return new TokenPair(access, raw, jwt.accessTokenTtlSeconds(), user.getId());
    }

    private void recordLogin(User user, String email, boolean ok, String reason, String ip, String ua) {
        recordLogin(user.getTenantId(), user.getId(), email, ok, reason, ip, ua);
    }

    private void recordLogin(UUID tenantId, UUID userId, String email, boolean ok, String reason, String ip, String ua) {
        LoginHistory h = new LoginHistory();
        h.setTenantId(tenantId);
        h.setUserId(userId);
        h.setEmail(email);
        h.setSuccess(ok);
        h.setReason(reason);
        h.setIpAddress(ip);
        h.setUserAgent(ua == null ? null : ua.substring(0, Math.min(500, ua.length())));
        history.save(h);
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        refreshTokens.deleteExpired(Instant.now().minus(Duration.ofDays(30)));
    }
}
