package com.dnd.smartflow.analytics;

import com.dnd.smartflow.ai.insights.InsightsService;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.tenant.TenantContext;
import com.dnd.smartflow.config.domain.KpiDefinition;
import com.dnd.smartflow.config.domain.KpiDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Role-based control-tower KPIs. Every figure is computed with SQL over persisted records for the
 * current tenant (and optional site/period filters). Each KPI carries its nature (MEASURED or RISK_INDEX),
 * its configured target and the value of the previous period.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {
    private final JdbcTemplate jdbc;
    private final KpiDefinitionRepository kpis;
    private final AlertService alerts;
    private final InsightsService insights;

    public record Filter(UUID siteId, LocalDate from, LocalDate to) {
        public static Filter of(UUID siteId, LocalDate from, LocalDate to) {
            LocalDate t = to == null ? LocalDate.now() : to;
            LocalDate f = from == null ? t.minusDays(29) : from;
            return new Filter(siteId, f, t);
        }

        Filter previous() {
            long days = ChronoUnit.DAYS.between(from, to) + 1;
            return new Filter(siteId, from.minusDays(days), from.minusDays(1));
        }

        Timestamp start() {
            return Timestamp.from(from.atStartOfDay(ZoneOffset.UTC).toInstant());
        }

        Timestamp end() {
            return Timestamp.from(to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        }
    }

    // --------------------------------------------------------------- helpers
    private UUID tenant() {
        return TenantContext.require();
    }

    private UUID site(Filter f) {
        if (f.siteId() != null) {
            if (!CurrentUser.require().canAccessSite(f.siteId())) throw new IllegalArgumentException("Site not accessible");
            return f.siteId();
        }
        return null;
    }

    private String siteClause(Filter f, String col, List<Object> args) {
        UUID s = site(f);
        if (s != null) {
            args.add(s);
            return " and " + col + " = ?";
        }
        CurrentUser u = CurrentUser.require();
        if (!u.allSites()) {
            if (u.siteIds().isEmpty()) return " and " + col + " is null";
            StringBuilder sb = new StringBuilder(" and (" + col + " is null or " + col + " in (");
            for (int i = 0; i < u.siteIds().size(); i++) {
                sb.append(i == 0 ? "?" : ",?");
                args.add(u.siteIds().get(i));
            }
            return sb.append("))").toString();
        }
        return "";
    }

    private double num(String sql, Filter f, String siteCol, Object... extra) {
        List<Object> args = new ArrayList<>();
        args.add(tenant());
        args.addAll(Arrays.asList(extra));
        String q = sql + (siteCol == null ? "" : siteClause(f, siteCol, args));
        Double v = jdbc.queryForObject(q, Double.class, args.toArray());
        return v == null ? 0 : v;
    }

    private Map<String, Object> kpi(String code, String label, double value, String unit, Double previous, String nature) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("label", label);
        m.put("value", Math.round(value * 10) / 10.0);
        m.put("unit", unit);
        m.put("previous", previous == null ? null : Math.round(previous * 10) / 10.0);
        m.put("nature", nature);
        Optional<KpiDefinition> def = kpis.findOne((r, q, cb) -> cb.equal(r.get("code"), code));
        def.ifPresent(d -> {
            m.put("target", d.getTarget());
            m.put("direction", d.getDirection());
            if (d.getTarget() != null) {
                boolean higher = !"LOWER_IS_BETTER".equals(d.getDirection());
                m.put("onTarget", higher ? value >= d.getTarget() : value <= d.getTarget());
            }
        });
        return m;
    }

    private Map<String, Object> monthlySeries(String sql, Filter f, String siteCol) {
        LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(11);
        List<Object> args = new ArrayList<>(List.of(tenant(), Date.valueOf(start)));
        String q = sql.replace("{site}", siteCol == null ? "" : siteClause(f, siteCol, args)) + " group by 1 order by 1";
        Map<String, Double> byMonth = new HashMap<>();
        jdbc.query(q, rs -> {
            byMonth.put(rs.getString(1), rs.getDouble(2));
        }, args.toArray());
        List<String> months = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String m = start.plusMonths(i).toString().substring(0, 7);
            months.add(m);
            values.add(Math.round(byMonth.getOrDefault(m, 0.0) * 10) / 10.0);
        }
        return Map.of("months", months, "values", values);
    }

    private List<Map<String, Object>> groupCount(String sql, Filter f, String siteCol, Object... extra) {
        List<Object> args = new ArrayList<>();
        args.add(tenant());
        args.addAll(Arrays.asList(extra));
        String q = sql.replace("{site}", siteCol == null ? "" : siteClause(f, siteCol, args)) + " group by 1 order by 2 desc";
        return jdbc.query(q, (rs, i) -> Map.<String, Object>of("name", Objects.toString(rs.getString(1), "—"), "value", rs.getDouble(2)), args.toArray());
    }

    // --------------------------------------------------------------- executive
    @Transactional(readOnly = true)
    public Map<String, Object> executive(Filter f) {
        Filter p = f.previous();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", Map.of("from", f.from(), "to", f.to()));
        List<Map<String, Object>> k = new ArrayList<>();
        k.add(kpi("SITES", "Active sites", num("select count(*) from sites where tenant_id = ? and coalesce(active,true)", f, null), "", null, "MEASURED"));
        double employees = num("select count(*) from employees where tenant_id = ? and status = 'ACTIVE'", f, "site_id");
        k.add(kpi("EMPLOYEES", "Active employees", employees, "", null, "MEASURED"));
        String prodSql = "select coalesce(sum(quantity_produced),0) from batches where tenant_id = ? and production_date between ? and ?";
        k.add(kpi("PRODUCTION_UNITS", "Units produced", num(prodSql, f, "site_id", Date.valueOf(f.from()), Date.valueOf(f.to())), "units",
                num(prodSql, p, "site_id", Date.valueOf(p.from()), Date.valueOf(p.to())), "MEASURED"));
        k.add(kpi("OPEN_ORDERS", "Open orders", num("select count(*) from sales_orders where tenant_id = ? and status in ('DRAFT','CONFIRMED','PREPARING','SHIPPED')", f, null), "", null, "MEASURED"));
        k.add(kpi("STOCK_UNITS", "Units in stock", num("select coalesce(sum(quantity),0) from stock_levels where tenant_id = ?", f, null), "units", null, "MEASURED"));
        k.add(kpi("OTD_RATE", "On-time delivery", otd(f), "%", otd(p), "MEASURED"));
        String fuelSql = "select coalesce(sum(cost),0) from fuel_logs where tenant_id = ? and log_date between ? and ?";
        k.add(kpi("DISTRIBUTION_COST", "Fuel cost", num(fuelSql, f, null, Date.valueOf(f.from()), Date.valueOf(f.to())), "DZD",
                num(fuelSql, p, null, Date.valueOf(p.from()), Date.valueOf(p.to())), "MEASURED"));
        k.add(kpi("NC_OPEN", "Open non-conformities", num("select count(*) from non_conformities where tenant_id = ? and status not in ('CLOSED','CANCELLED')", f, "site_id"), "", null, "MEASURED"));
        k.add(kpi("FIRST_PASS_YIELD", "Quality inspection pass rate", passRate(f, "QUALITY"), "%", passRate(p, "QUALITY"), "MEASURED"));
        k.add(kpi("LTIFR", "Lost-time injury frequency", ltifr(f, employees), "", ltifr(p, employees), "MEASURED"));
        k.add(kpi("SAFETY_INCIDENTS", "Safety incidents", incidents(f, "SAFETY"), "", incidents(p, "SAFETY"), "MEASURED"));
        k.add(kpi("CO2_TOTAL", "CO2 emissions (transport + energy)", co2(f), "kg CO2e", co2(p), "MEASURED"));
        k.add(kpi("RECYCLING_RATE", "Waste recycling rate", recycling(f), "%", recycling(p), "MEASURED"));
        List<AlertService.Alert> all = alerts.alerts();
        k.add(kpi("CRITICAL_ALERTS", "Critical alerts", all.stream().filter(a -> "CRITICAL".equals(a.severity())).count(), "", null, "MEASURED"));
        out.put("kpis", k);
        out.put("operationalRisk", operationalRisk(f));
        out.put("productionTrend", monthlySeries("select to_char(production_date,'YYYY-MM'), sum(quantity_produced) from batches where tenant_id = ? and production_date >= ? {site}", f, "site_id"));
        out.put("revenueTrend", monthlySeries("select to_char(order_date,'YYYY-MM'), sum(total_amount) from sales_orders where tenant_id = ? and order_date >= ? and status <> 'CANCELLED' {site}", f, null));
        out.put("ncTrend", monthlySeries("select to_char(detected_at,'YYYY-MM'), count(*) from non_conformities where tenant_id = ? and detected_at >= ? {site}", f, "site_id"));
        out.put("incidentTrend", monthlySeries("select to_char(occurred_at,'YYYY-MM'), count(*) from incidents where tenant_id = ? and occurred_at >= ? {site}", f, "site_id"));
        out.put("alerts", all.stream().limit(8).toList());
        out.put("sites", jdbc.query("""
                select s.id, s.name, s.type, s.city, s.latitude, s.longitude,
                  (select count(*) from non_conformities n where n.site_id = s.id and n.status not in ('CLOSED','CANCELLED')) open_nc,
                  (select count(*) from incidents i where i.site_id = s.id and i.status <> 'CLOSED') open_incidents,
                  (select count(*) from employees e where e.site_id = s.id and e.status = 'ACTIVE') employees
                from sites s where s.tenant_id = ? order by s.name""", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getObject("id"));
            m.put("name", rs.getString("name"));
            m.put("type", rs.getString("type"));
            m.put("city", rs.getString("city"));
            m.put("latitude", rs.getObject("latitude"));
            m.put("longitude", rs.getObject("longitude"));
            m.put("openNc", rs.getInt("open_nc"));
            m.put("openIncidents", rs.getInt("open_incidents"));
            m.put("employees", rs.getInt("employees"));
            return m;
        }, tenant()));
        return out;
    }

    private double otd(Filter f) {
        List<Object> args = new ArrayList<>(List.of(tenant(), f.start(), f.end()));
        Map<String, Object> r = jdbc.queryForMap("select count(*) total, count(*) filter (where coalesce(delay_minutes,0) = 0) ontime from deliveries "
                + "where tenant_id = ? and status = 'DELIVERED' and delivered_at >= ? and delivered_at < ?", args.toArray());
        long total = ((Number) r.get("total")).longValue();
        return total == 0 ? 0 : ((Number) r.get("ontime")).doubleValue() * 100 / total;
    }

    private double passRate(Filter f, String domain) {
        List<Object> args = new ArrayList<>(List.of(tenant(), domain, f.start(), f.end()));
        String q = "select count(*) total, count(*) filter (where result = 'PASS') pass from inspections where tenant_id = ? and domain = ? "
                + "and status = 'COMPLETED' and completed_at >= ? and completed_at < ?" + siteClause(f, "site_id", args);
        Map<String, Object> r = jdbc.queryForMap(q, args.toArray());
        long total = ((Number) r.get("total")).longValue();
        return total == 0 ? 0 : ((Number) r.get("pass")).doubleValue() * 100 / total;
    }

    private double incidents(Filter f, String domain) {
        return num("select count(*) from incidents where tenant_id = ? and domain = ? and occurred_at >= ? and occurred_at < ?", f, "site_id",
                domain, f.start(), f.end());
    }

    /** LTIFR = lost-time injuries × 1,000,000 / hours worked (173 h per employee-month). */
    private double ltifr(Filter f, double employees) {
        double lti = num("select count(*) from incidents where tenant_id = ? and domain = 'SAFETY' and coalesce(lost_time_days,0) > 0 "
                + "and occurred_at >= ? and occurred_at < ?", f, "site_id", f.start(), f.end());
        double months = (ChronoUnit.DAYS.between(f.from(), f.to()) + 1) / 30.4;
        double hours = employees * 173 * months;
        return hours == 0 ? 0 : lti * 1_000_000 / hours;
    }

    /** Transport CO2 (deliveries) + electricity (0.55 kg/kWh) + natural gas (1.9 kg/m3), overridable per tenant. */
    private double co2(Filter f) {
        double transport = num("select coalesce(sum(co2_kg),0) from deliveries where tenant_id = ? and delivered_at >= ? and delivered_at < ?", f, null, f.start(), f.end());
        double kwh = num("select coalesce(sum(reading_value),0) from environmental_readings where tenant_id = ? and reading_type = 'ENERGY_ELECTRICITY' "
                + "and recorded_at >= ? and recorded_at < ?", f, "site_id", f.start(), f.end());
        double gas = num("select coalesce(sum(reading_value),0) from environmental_readings where tenant_id = ? and reading_type = 'ENERGY_GAS' "
                + "and recorded_at >= ? and recorded_at < ?", f, "site_id", f.start(), f.end());
        Map<String, Double> factors = emissionFactors();
        return transport + kwh * factors.getOrDefault("electricity_kwh", 0.55) + gas * factors.getOrDefault("natural_gas_m3", 1.9);
    }

    private Map<String, Double> emissionFactors() {
        try {
            String json = jdbc.queryForObject("select settings->>'emissionFactors' from tenants where id = ?", String.class, tenant());
            if (json == null) return Map.of();
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** Transport, electricity and gas emissions (kg CO2e) for a period. */
    @Transactional(readOnly = true)
    public Map<String, Double> co2Breakdown(Filter f) {
        double transport = num("select coalesce(sum(co2_kg),0) from deliveries where tenant_id = ? and delivered_at >= ? and delivered_at < ?", f, null, f.start(), f.end());
        double kwh = num("select coalesce(sum(reading_value),0) from environmental_readings where tenant_id = ? and reading_type = 'ENERGY_ELECTRICITY' "
                + "and recorded_at >= ? and recorded_at < ?", f, "site_id", f.start(), f.end());
        double gas = num("select coalesce(sum(reading_value),0) from environmental_readings where tenant_id = ? and reading_type = 'ENERGY_GAS' "
                + "and recorded_at >= ? and recorded_at < ?", f, "site_id", f.start(), f.end());
        Map<String, Double> factors = emissionFactors();
        double elec = kwh * factors.getOrDefault("electricity_kwh", 0.55);
        double g = gas * factors.getOrDefault("natural_gas_m3", 1.9);
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("transport", Math.round(transport * 10) / 10.0);
        m.put("electricity", Math.round(elec * 10) / 10.0);
        m.put("gas", Math.round(g * 10) / 10.0);
        m.put("total", Math.round((transport + elec + g) * 10) / 10.0);
        return m;
    }

    private double recycling(Filter f) {
        List<Object> args = new ArrayList<>(List.of(tenant(), Date.valueOf(f.from()), Date.valueOf(f.to())));
        String q = "select coalesce(sum(quantity_kg),0) total, coalesce(sum(quantity_kg) filter (where treatment in ('RECYCLING','RECOVERY','COMPOSTING')),0) rec "
                + "from waste_records where tenant_id = ? and recorded_date between ? and ?" + siteClause(f, "site_id", args);
        Map<String, Object> r = jdbc.queryForMap(q, args.toArray());
        double total = ((Number) r.get("total")).doubleValue();
        return total == 0 ? 0 : ((Number) r.get("rec")).doubleValue() * 100 / total;
    }

    private Map<String, Object> operationalRisk(Filter f) {
        double critNc = num("select count(*) from non_conformities where tenant_id = ? and severity = 'CRITICAL' and status not in ('CLOSED','CANCELLED')", f, "site_id");
        double seriousInc = num("select count(*) from incidents where tenant_id = ? and severity >= 4 and status <> 'CLOSED'", f, "site_id");
        double overdueCapa = num("select count(*) from capa_actions where tenant_id = ? and due_date < current_date and status not in ('CLOSED','CANCELLED','PENDING_VERIFICATION')", f, "site_id");
        double highRisks = num("select count(*) from risk_assessments where tenant_id = ? and coalesce(residual_level, risk_level) in ('HIGH','CRITICAL') and status <> 'ARCHIVED'", f, "site_id");
        double down = num("select count(*) from equipment where tenant_id = ? and status = 'DOWN'", f, "site_id");
        double score = Math.min(100, critNc * 8 + seriousInc * 10 + overdueCapa * 2 + highRisks * 3 + down * 5);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score", Math.round(score));
        m.put("level", score >= 60 ? "HIGH" : score >= 30 ? "MEDIUM" : "LOW");
        m.put("nature", "RISK_INDEX");
        m.put("formula", "8×critical NC + 10×serious open incidents + 2×overdue CAPA + 3×high residual risks + 5×equipment down (max 100)");
        m.put("components", Map.of("criticalNc", critNc, "seriousIncidents", seriousInc, "overdueCapa", overdueCapa, "highRisks", highRisks, "equipmentDown", down));
        return m;
    }

    // --------------------------------------------------------------- QHSE
    @Transactional(readOnly = true)
    public Map<String, Object> qhse(Filter f) {
        Filter p = f.previous();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", Map.of("from", f.from(), "to", f.to()));
        List<Map<String, Object>> k = new ArrayList<>();
        k.add(kpi("NC_OPEN", "Open non-conformities", num("select count(*) from non_conformities where tenant_id = ? and status not in ('CLOSED','CANCELLED')", f, "site_id"), "", null, "MEASURED"));
        k.add(kpi("CRITICAL_INCIDENTS", "Critical incidents", num("select count(*) from incidents where tenant_id = ? and severity >= 4 and occurred_at >= ? and occurred_at < ?", f, "site_id", f.start(), f.end()), "",
                num("select count(*) from incidents where tenant_id = ? and severity >= 4 and occurred_at >= ? and occurred_at < ?", p, "site_id", p.start(), p.end()), "MEASURED"));
        String nm = "select count(*) from incidents where tenant_id = ? and type = 'NEAR_MISS' and occurred_at >= ? and occurred_at < ?";
        k.add(kpi("NEAR_MISSES", "Near misses reported", num(nm, f, "site_id", f.start(), f.end()), "", num(nm, p, "site_id", p.start(), p.end()), "MEASURED"));
        Map<String, Object> capa = jdbc.queryForMap("select count(*) filter (where status <> 'CANCELLED') total, count(*) filter (where status in ('CLOSED','PENDING_VERIFICATION')) done "
                + "from capa_actions where tenant_id = ?", tenant());
        double capaTotal = ((Number) capa.get("total")).doubleValue();
        k.add(kpi("CAPA_COMPLETION", "CAPA completion rate", capaTotal == 0 ? 0 : ((Number) capa.get("done")).doubleValue() * 100 / capaTotal, "%", null, "MEASURED"));
        k.add(kpi("CAPA_OVERDUE", "Overdue actions", num("select count(*) from capa_actions where tenant_id = ? and due_date < current_date and status not in ('CLOSED','CANCELLED','PENDING_VERIFICATION')", f, "site_id"), "", null, "MEASURED"));
        k.add(kpi("AUDIT_SCORE", "Average audit score (12 months)", num("select coalesce(avg(score),0) from audits where tenant_id = ? and status = 'COMPLETED' and end_date >= current_date - 365", f, "site_id"), "%", null, "MEASURED"));
        double allPass = 0;
        {
            List<Object> args = new ArrayList<>(List.of(tenant(), f.start(), f.end()));
            Map<String, Object> r = jdbc.queryForMap("select count(*) total, count(*) filter (where result = 'PASS') pass from inspections where tenant_id = ? and status = 'COMPLETED' "
                    + "and completed_at >= ? and completed_at < ?" + siteClause(f, "site_id", args), args.toArray());
            long t = ((Number) r.get("total")).longValue();
            allPass = t == 0 ? 0 : ((Number) r.get("pass")).doubleValue() * 100 / t;
        }
        k.add(kpi("INSPECTION_PASS_RATE", "Inspection compliance", allPass, "%", null, "MEASURED"));
        k.add(kpi("HYGIENE_COMPLIANCE", "Hygiene inspection compliance", passRate(f, "HYGIENE"), "%", passRate(p, "HYGIENE"), "MEASURED"));
        out.put("kpis", k);
        out.put("riskDistribution", groupCount("select coalesce(residual_level, risk_level), count(*) from risk_assessments where tenant_id = ? and status <> 'ARCHIVED' {site}", f, "site_id"));
        out.put("ncBySeverityTrend", Map.of(
                "CRITICAL", monthlySeries("select to_char(detected_at,'YYYY-MM'), count(*) from non_conformities where tenant_id = ? and detected_at >= ? and severity = 'CRITICAL' {site}", f, "site_id"),
                "MAJOR", monthlySeries("select to_char(detected_at,'YYYY-MM'), count(*) from non_conformities where tenant_id = ? and detected_at >= ? and severity = 'MAJOR' {site}", f, "site_id"),
                "MINOR", monthlySeries("select to_char(detected_at,'YYYY-MM'), count(*) from non_conformities where tenant_id = ? and detected_at >= ? and severity = 'MINOR' {site}", f, "site_id")));
        out.put("ncByType", groupCount("select type, count(*) from non_conformities where tenant_id = ? and detected_at >= ? and detected_at < ? {site}", f, "site_id", f.start(), f.end()));
        out.put("ncByStatus", groupCount("select status, count(*) from non_conformities where tenant_id = ? and status not in ('CLOSED','CANCELLED') {site}", f, "site_id"));
        out.put("incidentsByType", groupCount("select type, count(*) from incidents where tenant_id = ? and occurred_at >= current_date - 365 {site}", f, "site_id"));
        out.put("safetyTrend", Map.of(
                "accidents", monthlySeries("select to_char(occurred_at,'YYYY-MM'), count(*) from incidents where tenant_id = ? and occurred_at >= ? and domain='SAFETY' and type <> 'NEAR_MISS' {site}", f, "site_id"),
                "nearMisses", monthlySeries("select to_char(occurred_at,'YYYY-MM'), count(*) from incidents where tenant_id = ? and occurred_at >= ? and type = 'NEAR_MISS' {site}", f, "site_id"),
                "environmental", monthlySeries("select to_char(occurred_at,'YYYY-MM'), count(*) from incidents where tenant_id = ? and occurred_at >= ? and domain='ENVIRONMENT' {site}", f, "site_id")));
        out.put("inspectionsByDomain", jdbc.query("select domain, count(*) total, count(*) filter (where result='PASS') pass from inspections where tenant_id = ? and status='COMPLETED' "
                + "and completed_at >= current_date - 90 group by 1 order by 1", (rs, i) -> Map.<String, Object>of("domain", rs.getString(1), "total", rs.getInt(2),
                "passRate", rs.getInt(2) == 0 ? 0.0 : Math.round(rs.getInt(3) * 1000.0 / rs.getInt(2)) / 10.0), tenant()));
        out.put("environment", insights.environment());
        out.put("upcomingInspections", jdbc.queryForList("select id, reference, title, domain, scheduled_date, inspector_name from inspections where tenant_id = ? "
                + "and status in ('SCHEDULED','IN_PROGRESS') order by scheduled_date limit 8", tenant()));
        return out;
    }

    // --------------------------------------------------------------- distribution
    @Transactional(readOnly = true)
    public Map<String, Object> distribution(Filter f, List<Map<String, Object>> stockoutRisks) {
        Filter p = f.previous();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", Map.of("from", f.from(), "to", f.to()));
        List<Map<String, Object>> k = new ArrayList<>();
        String ordersSql = "select count(*) from sales_orders where tenant_id = ? and order_date between ? and ?";
        k.add(kpi("ORDERS", "Orders received", num(ordersSql, f, null, Date.valueOf(f.from()), Date.valueOf(f.to())), "",
                num(ordersSql, p, null, Date.valueOf(p.from()), Date.valueOf(p.to())), "MEASURED"));
        String delSql = "select count(*) from deliveries where tenant_id = ? and status = 'DELIVERED' and delivered_at >= ? and delivered_at < ?";
        k.add(kpi("DELIVERIES", "Deliveries completed", num(delSql, f, null, f.start(), f.end()), "", num(delSql, p, null, p.start(), p.end()), "MEASURED"));
        k.add(kpi("OTD_RATE", "On-time delivery", otd(f), "%", otd(p), "MEASURED"));
        k.add(kpi("AVG_DELAY", "Average delay of late deliveries", num("select coalesce(avg(delay_minutes),0) from deliveries where tenant_id = ? and delay_minutes > 0 "
                + "and delivered_at >= ? and delivered_at < ?", f, null, f.start(), f.end()), "min", null, "MEASURED"));
        double active = num("select count(*) from vehicles where tenant_id = ? and status <> 'OUT_OF_SERVICE'", f, null);
        double used = num("select count(distinct vehicle_id) from deliveries where tenant_id = ? and departed_at >= ? and departed_at < ?", f, null,
                Timestamp.from(Instant.now().minus(Duration.ofDays(7))), Timestamp.from(Instant.now().plus(Duration.ofDays(1))));
        k.add(kpi("FLEET_UTILIZATION", "Fleet utilization (7 days)", active == 0 ? 0 : used * 100 / active, "%", null, "MEASURED"));
        String fuel = "select coalesce(sum(liters),0) from fuel_logs where tenant_id = ? and log_date between ? and ?";
        k.add(kpi("FUEL_LITERS", "Fuel consumed", num(fuel, f, null, Date.valueOf(f.from()), Date.valueOf(f.to())), "L",
                num(fuel, p, null, Date.valueOf(p.from()), Date.valueOf(p.to())), "MEASURED"));
        String co2 = "select coalesce(sum(co2_kg),0) from deliveries where tenant_id = ? and delivered_at >= ? and delivered_at < ?";
        k.add(kpi("CO2_TRANSPORT", "Transport CO2 (estimated)", num(co2, f, null, f.start(), f.end()), "kg", num(co2, p, null, p.start(), p.end()), "MEASURED"));
        double cnt = num("select count(*) from deliveries where tenant_id = ? and status='DELIVERED' and co2_kg is not null and delivered_at >= ? and delivered_at < ?", f, null, f.start(), f.end());
        k.add(kpi("CO2_PER_DELIVERY", "CO2 per delivery", cnt == 0 ? 0 : num(co2, f, null, f.start(), f.end()) / cnt, "kg", null, "MEASURED"));
        out.put("kpis", k);
        out.put("ordersByStatus", groupCount("select status, count(*) from sales_orders where tenant_id = ? and order_date >= current_date - 90 {site}", f, null));
        out.put("deliveriesDaily", jdbc.query("""
                select d::date as dday,
                  (select count(*) from deliveries x where x.tenant_id = ? and x.planned_date = d::date) planned,
                  (select count(*) from deliveries x where x.tenant_id = ? and x.status = 'DELIVERED' and x.delivered_at::date = d::date) delivered,
                  (select count(*) from deliveries x where x.tenant_id = ? and x.status = 'DELIVERED' and x.delay_minutes > 0 and x.delivered_at::date = d::date) late
                from generate_series(current_date - 29, current_date, interval '1 day') d order by 1""",
                (rs, i) -> Map.<String, Object>of("day", rs.getString(1), "planned", rs.getInt(2), "delivered", rs.getInt(3), "late", rs.getInt(4)),
                tenant(), tenant(), tenant()));
        out.put("fleetStatus", groupCount("select status, count(*) from vehicles where tenant_id = ? {site}", f, null));
        out.put("stockByWarehouse", groupCount("select w.name, sum(s.quantity) from stock_levels s join warehouses w on w.id = s.warehouse_id where s.tenant_id = ? {site}", f, "w.site_id"));
        out.put("stockoutRisks", stockoutRisks.stream().filter(m -> !"LOW".equals(m.get("stockoutRisk"))).limit(10).toList());
        out.put("routeOptimization", jdbc.queryForMap("select count(*) plans, coalesce(sum(original_distance_km - optimized_distance_km),0) km_saved, "
                + "coalesce(sum(original_distance_km),0) original_km, coalesce(sum(optimized_distance_km),0) optimized_km, coalesce(sum(estimated_co2_kg),0) co2 "
                + "from route_plans where tenant_id = ? and plan_date >= current_date - 90", tenant()));
        out.put("fuelTrend", monthlySeries("select to_char(log_date,'YYYY-MM'), sum(liters) from fuel_logs where tenant_id = ? and log_date >= ? {site}", f, null));
        out.put("co2Trend", monthlySeries("select to_char(delivered_at,'YYYY-MM'), sum(co2_kg) from deliveries where tenant_id = ? and delivered_at >= ? {site}", f, null));
        out.put("vehicles", jdbc.queryForList("select id, plate_number, type, status, current_latitude, current_longitude, last_gps_at from vehicles "
                + "where tenant_id = ? and current_latitude is not null", tenant()));
        return out;
    }
}
