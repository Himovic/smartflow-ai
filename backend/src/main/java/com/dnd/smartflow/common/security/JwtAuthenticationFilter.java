package com.dnd.smartflow.common.security;

import com.dnd.smartflow.common.tenant.TenantContext;
import com.dnd.smartflow.iam.User;
import com.dnd.smartflow.iam.UserRepository;
import com.dnd.smartflow.tenant.Tenant;
import com.dnd.smartflow.tenant.TenantRepository;
import com.dnd.smartflow.tenant.TenantService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authenticates bearer tokens, binds the tenant context and builds the {@link CurrentUser}. A SUPER_ADMIN may
 * act on another tenant through the {@code X-Tenant-Id} header (organization selector); every other role is
 * pinned to its own tenant.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserRepository users;
    private final TenantRepository tenants;
    private final TenantService tenantService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            resolveToken(request).ifPresent(token -> authenticate(token, request));
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7));
        }
        // Browsers cannot set headers on WebSocket handshakes.
        if (request.getRequestURI().startsWith("/ws/")) {
            return Optional.ofNullable(request.getParameter("access_token"));
        }
        return Optional.empty();
    }

    private void authenticate(String token, HttpServletRequest request) {
        Claims claims;
        try {
            claims = jwt.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            return; // anonymous; protected endpoints will answer 401
        }
        UUID userId = UUID.fromString(claims.getSubject());
        UUID homeTenant = UUID.fromString(claims.get("tid", String.class));
        TenantContext.set(TenantContext.ROOT);
        Optional<User> found = users.findById(userId);
        if (found.isEmpty() || !found.get().isActive() || !found.get().getTenantId().equals(homeTenant)) {
            TenantContext.clear();
            return;
        }
        User user = found.get();
        Tenant home = tenants.findById(homeTenant).orElse(null);
        if (home == null || !home.isActive()) {
            TenantContext.clear();
            return;
        }
        UUID effectiveTenant = homeTenant;
        String override = request.getHeader("X-Tenant-Id");
        if (override != null && !override.isBlank() && user.getRole() == Role.SUPER_ADMIN) {
            try {
                UUID candidate = UUID.fromString(override);
                if (tenants.existsById(candidate)) effectiveTenant = candidate;
            } catch (IllegalArgumentException ignored) {
                // keep home tenant
            }
        }
        Tenant effective = effectiveTenant.equals(homeTenant) ? home : tenants.findById(effectiveTenant).orElse(home);
        boolean allSites = user.isAllSites() || user.getRole().seesAllSitesByDefault() || !effectiveTenant.equals(homeTenant);
        CurrentUser principal = new CurrentUser(user.getId(), effectiveTenant, homeTenant, user.getEmail(), user.fullName(),
                user.getRole(), allSites, user.getSiteIds() == null ? List.of() : List.copyOf(user.getSiteIds()),
                tenantService.enabledModules(effectiveTenant), effective.isDemo());
        TenantContext.set(effectiveTenant);
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
