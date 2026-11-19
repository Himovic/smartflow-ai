package com.dnd.smartflow.demo;

import com.dnd.smartflow.common.security.Role;
import com.dnd.smartflow.common.tenant.TenantContext;
import com.dnd.smartflow.iam.User;
import com.dnd.smartflow.iam.UserRepository;
import com.dnd.smartflow.organization.domain.Site;
import com.dnd.smartflow.organization.domain.SiteRepository;
import com.dnd.smartflow.catalog.domain.Product;
import com.dnd.smartflow.catalog.domain.ProductRepository;
import com.dnd.smartflow.tenant.Tenant;
import com.dnd.smartflow.tenant.TenantRepository;
import com.dnd.smartflow.tenant.TenantService;
import com.dnd.smartflow.workflow.WorkflowInstaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Demonstration environment lifecycle: seeds the Atlas Industries Group tenant (and a small second tenant
 * proving tenant isolation) on first start, and can reset it to its initial state on demand or on a
 * schedule. Demo data lives in dedicated tenants and never mixes with customer tenants.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoService {
    static final String SECOND_TENANT = "SAHARAN";

    private final DemoDataSeeder seeder;
    private final TenantRepository tenants;
    private final TenantService tenantService;
    private final WorkflowInstaller workflows;
    private final UserRepository users;
    private final SiteRepository sites;
    private final ProductRepository products;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Instant lastReset;

    @Value("${smartflow.demo.enabled:true}")
    private boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!enabled) return;
        Thread t = new Thread(() -> {
            try {
                if (tenants.findByCode(DemoDataSeeder.TENANT_CODE).isEmpty()) {
                    log.info("Seeding SmartFlow demonstration environment (Atlas Industries Group)…");
                    reseed();
                }
            } catch (Exception e) {
                log.error("Demo seeding failed", e);
            }
        }, "demo-seeder");
        t.setDaemon(true);
        t.start();
    }

    @Scheduled(cron = "${smartflow.demo.reset-cron:-}", zone = "Africa/Algiers")
    public void scheduledReset() {
        if (enabled) reset();
    }

    public boolean isRunning() {
        return running.get();
    }

    public Instant lastReset() {
        return lastReset;
    }

    public Map<String, Object> reset() {
        if (!running.compareAndSet(false, true)) return Map.of("status", "ALREADY_RUNNING");
        try {
            for (String code : List.of(DemoDataSeeder.TENANT_CODE, SECOND_TENANT)) {
                tenants.findByCode(code).filter(Tenant::isDemo).ifPresent(t -> purge(t.getId()));
            }
            running.set(false);
            reseed();
            return Map.of("status", "RESET", "at", lastReset);
        } finally {
            running.set(false);
        }
    }

    private void reseed() {
        running.set(true);
        try {
            seeder.seed();
            seedSecondTenant();
            lastReset = Instant.now();
        } catch (RuntimeException e) {
            // never leave a half-seeded demo tenant behind: it would be mistaken for a complete one
            for (String code : List.of(DemoDataSeeder.TENANT_CODE, SECOND_TENANT)) {
                tenants.findByCode(code).filter(Tenant::isDemo).ifPresent(t -> purge(t.getId()));
            }
            throw e;
        } finally {
            running.set(false);
        }
    }

    /** Deletes every row of a demo tenant, table by table (children first). */
    void purge(UUID tenantId) {
        tx.executeWithoutResult(s -> {
            List<String> tables = jdbc.queryForList("""
                    select c.table_name from information_schema.columns c
                    where c.column_name = 'tenant_id' and c.table_schema = current_schema() and c.table_name not in ('users','tenants')""", String.class);
            jdbc.update("delete from refresh_tokens where tenant_id = ?", tenantId);
            jdbc.update("delete from workflow_events where tenant_id = ?", tenantId);
            for (String t : tables) jdbc.update("delete from " + t + " where tenant_id = ?", tenantId);
            jdbc.update("delete from login_history where tenant_id = ?", tenantId);
            jdbc.update("delete from users where tenant_id = ?", tenantId);
            jdbc.update("delete from reference_sequences where tenant_id = ?", tenantId);
            jdbc.update("delete from tenants where id = ?", tenantId);
        });
        log.info("Demo tenant {} purged", tenantId);
    }

    /** A small pharmaceutical tenant used to demonstrate strict multi-tenant isolation. */
    private void seedSecondTenant() {
        if (tenants.findByCode(SECOND_TENANT).isPresent()) return;
        Tenant t = tx.execute(s -> {
            Tenant x = new Tenant();
            x.setId(UUID.randomUUID());
            x.setCode(SECOND_TENANT);
            x.setName("Saharan Pharma Labs");
            x.setIndustryProfile("PHARMACEUTICAL");
            x.setCountry("Algérie");
            x.setTimezone("Africa/Algiers");
            x.setDefaultLanguage("fr");
            x.setDemo(true);
            return tenants.save(x);
        });
        tenantService.applyIndustryProfile(t.getId(), "PHARMACEUTICAL");
        TenantContext.runAs(t.getId(), () -> tx.executeWithoutResult(s -> {
            workflows.installDefaults();
            Site site = new Site();
            site.setCode("BLD");
            site.setName("Blida Pharmaceutical Plant");
            site.setType("PRODUCTION");
            site.setCity("Blida");
            site.setCountry("Algérie");
            site.setLatitude(36.47);
            site.setLongitude(2.83);
            site.setActive(true);
            sites.save(site);
            String[][] ps = {{"SPL-AMX-500", "Amoxicilline 500 mg gélules x16"}, {"SPL-PCT-1G", "Paracétamol 1 g comprimés x8"}, {"SPL-IBU-400", "Ibuprofène 400 mg x20"}};
            for (String[] p : ps) {
                Product pr = new Product();
                pr.setSku(p[0]);
                pr.setName(p[1]);
                pr.setCategory("Médicaments");
                pr.setUnit("BOITE");
                pr.setUnitPrice(BigDecimal.valueOf(350));
                pr.setActive(true);
                products.save(pr);
            }
            User admin = new User();
            admin.setEmail("admin@saharanpharma.demo");
            admin.setPasswordHash(encoder.encode(DemoDataSeeder.PASSWORD));
            admin.setFirstName("Sara");
            admin.setLastName("Benyahia");
            admin.setRole(Role.TENANT_ADMIN);
            admin.setJobTitle("Responsable assurance qualité");
            admin.setAllSites(true);
            admin.setLanguage("fr");
            admin.setActive(true);
            users.save(admin);
        }));
    }
}
