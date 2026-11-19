package com.dnd.smartflow.common.crud;

import com.dnd.smartflow.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.UUID;

/** Gap-tolerant, concurrency-safe business references such as {@code NC-2026-0042}. */
@Component
@RequiredArgsConstructor
public class ReferenceGenerator {
    private final JdbcTemplate jdbc;

    @Transactional
    public String next(String prefix) {
        UUID tenant = TenantContext.require();
        int year = Year.now().getValue();
        Long value = jdbc.queryForObject("""
                insert into reference_sequences(tenant_id, prefix, year, value) values (?, ?, ?, 1)
                on conflict (tenant_id, prefix, year) do update set value = reference_sequences.value + 1
                returning value""", Long.class, tenant, prefix, year);
        return "%s-%d-%04d".formatted(prefix, year, value);
    }

    private static final String[][] REFERENCE_COLUMNS = {{"non_conformities", "reference"}, {"capa_actions", "reference"},
            {"incidents", "reference"}, {"inspections", "reference"}, {"risk_assessments", "reference"}, {"work_permits", "reference"},
            {"audits", "reference"}, {"audit_findings", "reference"}, {"waste_records", "reference"}, {"maintenance_work_orders", "reference"},
            {"sales_orders", "order_number"}, {"deliveries", "delivery_number"}, {"route_plans", "code"}};

    /** Aligns sequences with references already present (imports, seeding) so generated references never collide. */
    @Transactional
    public void synchronize(UUID tenant) {
        for (String[] tc : REFERENCE_COLUMNS) {
            jdbc.update("""
                    insert into reference_sequences(tenant_id, prefix, year, value)
                    select tenant_id, m[1], m[2]::int, max(m[3]::bigint)
                    from (select tenant_id, regexp_match(%s, '^([A-Z]+)-([0-9]{4})-([0-9]+)$') m from %s where tenant_id = ?) x
                    where m is not null group by 1, 2, 3
                    on conflict (tenant_id, prefix, year) do update set value = greatest(reference_sequences.value, excluded.value)"""
                    .formatted(tc[1], tc[0]), tenant);
        }
    }
}
