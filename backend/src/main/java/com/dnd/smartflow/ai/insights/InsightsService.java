package com.dnd.smartflow.ai.insights;

import com.dnd.smartflow.ai.AiServiceClient;
import com.dnd.smartflow.assets.domain.*;
import com.dnd.smartflow.catalog.domain.*;
import com.dnd.smartflow.fleet.domain.*;
import com.dnd.smartflow.inventory.domain.*;
import com.dnd.smartflow.organization.domain.*;
import com.dnd.smartflow.qhse.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Predictive quality, predictive maintenance and QHSE intelligence. Each indicator states its nature:
 * MEASURED (computed from records), STATISTICAL (anomaly statistics) or RISK_INDEX (transparent weighted
 * score — not a trained model). Anomaly detection uses the ai-service Isolation Forest when available.
 */
@Service
@RequiredArgsConstructor
public class InsightsService {
    private final SupplierRepository suppliers;
    private final BatchRepository batches;
    private final NonConformityRepository ncs;
    private final InspectionRepository inspections;
    private final ProductionLineRepository lines;
    private final ProductRepository products;
    private final StockLevelRepository levels;
    private final EquipmentRepository equipment;
    private final MaintenanceWorkOrderRepository workOrders;
    private final VehicleRepository vehicles;
    private final IncidentRepository incidents;
    private final CapaActionRepository capas;
    private final RiskAssessmentRepository risks;
    private final EnvironmentalReadingRepository readings;
    private final WasteRecordRepository waste;
    private final SiteRepository sites;
    private final AiServiceClient ai;

    // ------------------------------------------------------------------ quality
    @Transactional(readOnly = true)
    public Map<String, Object> quality() {
        List<NonConformity> allNc = ncs.findAll();
        List<Batch> allBatches = batches.findAll();
        List<Inspection> allInspections = inspections.findAll();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("supplierPerformance", supplierPerformance(allNc, allBatches, allInspections));
        out.put("lineDefectRates", lineDefectRates(allNc, allBatches));
        out.put("highRiskBatches", highRiskBatches(allNc, allBatches, allInspections));
        out.put("anomalies", anomalies(allNc));
        out.put("defectTrend", monthly(allNc.stream().map(n -> n.getDetectedAt()).filter(Objects::nonNull).toList(), 12));
        return out;
    }

    public List<Map<String, Object>> supplierPerformance(List<NonConformity> allNc, List<Batch> allBatches, List<Inspection> allInspections) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Instant since = Instant.now().minus(Duration.ofDays(365));
        for (Supplier s : suppliers.findAll()) {
            long nc = allNc.stream().filter(n -> s.getId().equals(n.getSupplierId()) && n.getDetectedAt() != null && n.getDetectedAt().isAfter(since)).count();
            long lots = allBatches.stream().filter(b -> s.getId().equals(b.getSupplierId())).count();
            List<Inspection> incoming = allInspections.stream().filter(i -> s.getId().equals(i.getSupplierId()) && "COMPLETED".equals(i.getStatus())).toList();
            long failed = incoming.stream().filter(i -> "FAIL".equals(i.getResult())).count();
            double failRate = incoming.isEmpty() ? 0 : failed * 100.0 / incoming.size();
            double ncPer100 = lots == 0 ? nc : nc * 100.0 / lots;
            double ncPenalty = Math.min(35, lots > 0 ? ncPer100 * 1.5 : nc * 1.2);
            double index = Math.max(0, 100 - Math.min(40, failRate * 0.8) - ncPenalty
                    - Math.min(15, (100 - Optional.ofNullable(s.getOnTimeRate()).orElse(95.0)) * 0.5));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("supplierId", s.getId());
            m.put("code", s.getCode());
            m.put("name", s.getName());
            m.put("category", s.getCategory());
            m.put("status", s.getStatus());
            m.put("lotsSupplied", lots);
            m.put("nonConformities12m", nc);
            m.put("incomingInspections", incoming.size());
            m.put("inspectionFailRate", round(failRate));
            m.put("onTimeRate", s.getOnTimeRate());
            m.put("performanceIndex", round(index));
            m.put("nature", "RISK_INDEX");
            rows.add(m);
        }
        rows.sort(Comparator.comparingDouble(m -> (Double) m.get("performanceIndex")));
        return rows;
    }

    private List<Map<String, Object>> lineDefectRates(List<NonConformity> allNc, List<Batch> allBatches) {
        Instant since = Instant.now().minus(Duration.ofDays(90));
        LocalDate sinceDate = LocalDate.now().minusDays(90);
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<UUID, String> siteNames = sites.findAll().stream().collect(Collectors.toMap(Site::getId, Site::getName));
        for (ProductionLine l : lines.findAll()) {
            long nc = allNc.stream().filter(n -> l.getId().equals(n.getProductionLineId()) && n.getDetectedAt() != null && n.getDetectedAt().isAfter(since)).count();
            List<Batch> produced = allBatches.stream().filter(b -> l.getId().equals(b.getProductionLineId()) && b.getProductionDate() != null
                    && !b.getProductionDate().isBefore(sinceDate)).toList();
            long units = produced.stream().mapToLong(b -> Optional.ofNullable(b.getQuantityProduced()).orElse(0)).sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineId", l.getId());
            m.put("code", l.getCode());
            m.put("name", l.getName());
            m.put("site", siteNames.get(l.getSiteId()));
            m.put("batches90d", produced.size());
            m.put("nonConformities90d", nc);
            m.put("ncPer100Batches", produced.isEmpty() ? 0.0 : round(nc * 100.0 / produced.size()));
            m.put("ncPerMillionUnits", units == 0 ? 0.0 : round(nc * 1_000_000.0 / units));
            m.put("oee", l.getOee());
            m.put("nature", "MEASURED");
            rows.add(m);
        }
        rows.sort((a, b) -> Double.compare((Double) b.get("ncPer100Batches"), (Double) a.get("ncPer100Batches")));
        return rows;
    }

    private List<Map<String, Object>> highRiskBatches(List<NonConformity> allNc, List<Batch> allBatches, List<Inspection> allInspections) {
        Map<UUID, Supplier> sup = suppliers.findAll().stream().collect(Collectors.toMap(Supplier::getId, s -> s));
        Map<UUID, Product> prod = products.findAll().stream().collect(Collectors.toMap(Product::getId, p -> p));
        Map<UUID, Integer> stockByBatch = new HashMap<>();
        levels.findAll().forEach(l -> {
            if (l.getBatchId() != null) stockByBatch.merge(l.getBatchId(), l.getQuantity(), Integer::sum);
        });
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Batch b : allBatches) {
            if (Set.of("RECALLED", "CONSUMED", "EXPIRED").contains(b.getStatus())) continue;
            long openNc = allNc.stream().filter(n -> b.getId().equals(n.getBatchId()) && !Set.of("CLOSED", "CANCELLED").contains(n.getStatus())).count();
            long failedInsp = allInspections.stream().filter(i -> b.getId().equals(i.getBatchId()) && "FAIL".equals(i.getResult())).count();
            Supplier s = b.getSupplierId() == null ? null : sup.get(b.getSupplierId());
            int stock = stockByBatch.getOrDefault(b.getId(), 0);
            List<String> reasons = new ArrayList<>();
            double score = 0;
            if (openNc > 0) { score += Math.min(45, 30 + 15 * (openNc - 1)); reasons.add(openNc + " open NC"); }
            if (failedInsp > 0) { score += 25; reasons.add("failed inspection"); }
            if (s != null && s.getQualityScore() != null && s.getQualityScore() < 80) { score += 15; reasons.add("supplier score " + s.getQualityScore()); }
            if (b.getExpiryDate() != null && stock > 0 && ChronoUnit.DAYS.between(today, b.getExpiryDate()) <= 21) { score += 15; reasons.add("expires " + b.getExpiryDate()); }
            if ("QUARANTINE".equals(b.getStatus()) || "BLOCKED".equals(b.getStatus())) { score += 10; reasons.add(b.getStatus().toLowerCase()); }
            if (score < 25) continue;
            Product p = prod.get(b.getProductId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("batchId", b.getId());
            m.put("batchNumber", b.getBatchNumber());
            m.put("product", p == null ? "" : p.getName());
            m.put("status", b.getStatus());
            m.put("stock", stock);
            m.put("riskScore", Math.min(100, score));
            m.put("reasons", reasons);
            m.put("nature", "RISK_INDEX");
            rows.add(m);
        }
        rows.sort((a, b) -> Double.compare((Double) b.get("riskScore"), (Double) a.get("riskScore")));
        return rows.stream().limit(15).toList();
    }

    /** Weekly NC counts per production line: z-score of the last week vs the 12 previous weeks (or Isolation Forest). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> anomalies(List<NonConformity> allNc) {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        List<Map<String, Object>> series = new ArrayList<>();
        for (ProductionLine l : lines.findAll()) {
            double[] counts = new double[13];
            for (NonConformity n : allNc) {
                if (!l.getId().equals(n.getProductionLineId()) || n.getDetectedAt() == null) continue;
                LocalDate d = n.getDetectedAt().atZone(ZoneOffset.UTC).toLocalDate();
                long w = ChronoUnit.WEEKS.between(d.with(DayOfWeek.MONDAY), monday);
                if (w >= 0 && w < 13) counts[12 - (int) w]++;
            }
            series.add(Map.of("id", l.getId().toString(), "label", l.getCode() + " — " + l.getName(), "values", counts));
        }
        Optional<Map> remote = ai.post("/quality/anomalies", Map.of("series", series), Map.class);
        if (remote.isPresent()) return (Map<String, Object>) remote.get();
        List<Map<String, Object>> flagged = new ArrayList<>();
        for (Map<String, Object> s : series) {
            double[] v = (double[]) s.get("values");
            double[] base = Arrays.copyOf(v, 12);
            double mean = Arrays.stream(base).average().orElse(0);
            double sd = Math.sqrt(Arrays.stream(base).map(x -> (x - mean) * (x - mean)).sum() / 11.0);
            double z = sd == 0 ? (v[12] > mean ? 3.0 : 0) : (v[12] - mean) / sd;
            if (z >= 2 && v[12] >= 2) {
                flagged.add(Map.of("id", s.get("id"), "label", s.get("label"), "lastWeek", v[12], "baselineMean", round(mean), "zScore", round(z)));
            }
        }
        return Map.of("method", "z-score of last week vs 12-week baseline (built-in)", "nature", "STATISTICAL", "flagged", flagged);
    }

    // ------------------------------------------------------------------ maintenance
    @Transactional(readOnly = true)
    public Map<String, Object> maintenance() {
        List<MaintenanceWorkOrder> wos = workOrders.findAll();
        LocalDate today = LocalDate.now();
        Instant since = Instant.now().minus(Duration.ofDays(180));
        List<Map<String, Object>> rows = new ArrayList<>();
        int operational = 0, total = 0;
        for (Equipment e : equipment.findAll()) {
            if ("RETIRED".equals(e.getStatus())) continue;
            total++;
            if ("OPERATIONAL".equals(e.getStatus())) operational++;
            List<MaintenanceWorkOrder> mine = wos.stream().filter(w -> e.getId().equals(w.getEquipmentId())).toList();
            long correctives = mine.stream().filter(w -> "CORRECTIVE".equals(w.getType()) && w.getCompletedDate() != null
                    && w.getCompletedDate().isAfter(today.minusDays(180))).count();
            double downtime = mine.stream().filter(w -> w.getCompletedDate() != null && w.getCompletedDate().isAfter(today.minusDays(180)))
                    .mapToDouble(w -> Optional.ofNullable(w.getDowntimeHours()).orElse(0.0)).sum();
            long overdueDays = e.getNextMaintenanceDate() == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(e.getNextMaintenanceDate(), today));
            double crit = switch (Objects.toString(e.getCriticality(), "MEDIUM")) {
                case "CRITICAL" -> 1.0;
                case "HIGH" -> 0.75;
                case "LOW" -> 0.25;
                default -> 0.5;
            };
            double mtbfFactor = 0;
            if (e.getMtbfHours() != null && e.getMtbfHours() > 0 && e.getLastMaintenanceDate() != null) {
                double hoursSince = ChronoUnit.DAYS.between(e.getLastMaintenanceDate(), today) * 16.0;
                mtbfFactor = Math.min(1.0, hoursSince / e.getMtbfHours());
            }
            double score = Math.min(100, correctives * 12 + Math.min(25, overdueDays * 1.0) + mtbfFactor * 30 + crit * 15
                    + ("DOWN".equals(e.getStatus()) ? 20 : 0));
            List<String> reasons = new ArrayList<>();
            if (correctives > 0) reasons.add(correctives + " breakdown(s) in 180 days");
            if (overdueDays > 0) reasons.add("preventive maintenance overdue by " + overdueDays + " days");
            if (mtbfFactor > 0.8) reasons.add("running time close to MTBF");
            if ("DOWN".equals(e.getStatus())) reasons.add("currently down");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("equipmentId", e.getId());
            m.put("code", e.getCode());
            m.put("name", e.getName());
            m.put("category", e.getCategory());
            m.put("status", e.getStatus());
            m.put("criticality", e.getCriticality());
            m.put("downtimeHours180d", round(downtime));
            m.put("nextMaintenanceDate", e.getNextMaintenanceDate());
            m.put("riskScore", round(score));
            m.put("riskLevel", score >= 60 ? "HIGH" : score >= 35 ? "MEDIUM" : "LOW");
            m.put("reasons", reasons);
            m.put("recommendation", score >= 60 ? "Schedule preventive intervention within 7 days" : score >= 35
                    ? "Plan inspection at next window" : "Keep current maintenance plan");
            rows.add(m);
        }
        rows.sort((a, b) -> Double.compare((Double) b.get("riskScore"), (Double) a.get("riskScore")));
        List<Map<String, Object>> fleet = new ArrayList<>();
        for (Vehicle v : vehicles.findAll()) {
            int odo = Optional.ofNullable(v.getOdometerKm()).orElse(0);
            int next = Optional.ofNullable(v.getNextServiceKm()).orElse(odo + 15000);
            int remaining = next - odo;
            double score = Math.min(100, Math.max(0, (5000 - remaining) / 50.0) + ("MAINTENANCE".equals(v.getStatus()) ? 20 : 0)
                    + Math.max(0, (odo - 300000) / 10000.0));
            fleet.add(Map.of("vehicleId", v.getId(), "plateNumber", v.getPlateNumber(), "type", Objects.toString(v.getType(), ""),
                    "odometerKm", odo, "kmToService", remaining, "riskScore", round(score),
                    "riskLevel", score >= 60 ? "HIGH" : score >= 35 ? "MEDIUM" : "LOW"));
        }
        fleet.sort((a, b) -> Double.compare((Double) b.get("riskScore"), (Double) a.get("riskScore")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("method", "Transparent risk index: breakdown frequency, overdue preventive maintenance, MTBF consumption, criticality. "
                + "Not a trained model — it will be replaced by a supervised model once enough failure history is collected.");
        out.put("nature", "RISK_INDEX");
        out.put("availability", total == 0 ? 100.0 : round(operational * 100.0 / total));
        out.put("equipment", rows);
        out.put("vehicles", fleet);
        return out;
    }

    // ------------------------------------------------------------------ QHSE intelligence
    @Transactional(readOnly = true)
    public Map<String, Object> qhse() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Incident> inc = incidents.findAll();
        out.put("incidentTrend", monthly(inc.stream().map(Incident::getOccurredAt).filter(Objects::nonNull).toList(), 12));
        out.put("nearMissTrend", monthly(inc.stream().filter(i -> "NEAR_MISS".equals(i.getType())).map(Incident::getOccurredAt)
                .filter(Objects::nonNull).toList(), 12));
        out.put("topRisks", risks.findAll().stream().filter(r -> !"ARCHIVED".equals(r.getStatus()))
                .sorted(Comparator.comparing((RiskAssessment r) -> Optional.ofNullable(r.getResidualScore()).orElse(r.getRiskScore())).reversed())
                .limit(10).map(r -> Map.<String, Object>of("id", r.getId(), "reference", r.getReference(), "title", r.getTitle(),
                        "score", r.getRiskScore(), "residualScore", Optional.ofNullable(r.getResidualScore()).orElse(r.getRiskScore()),
                        "level", Optional.ofNullable(r.getResidualLevel()).orElse(r.getRiskLevel()))).toList());
        Instant since = Instant.now().minus(Duration.ofDays(90));
        Map<String, List<NonConformity>> groups = ncs.findAll().stream().filter(n -> n.getDetectedAt() != null && n.getDetectedAt().isAfter(since))
                .collect(Collectors.groupingBy(n -> Objects.toString(n.getCategory(), n.getType()) + "|" + Objects.toString(n.getProductionLineId(), "-")));
        Map<UUID, String> lineNames = lines.findAll().stream().collect(Collectors.toMap(ProductionLine::getId, ProductionLine::getName));
        out.put("recurrentNonConformities", groups.entrySet().stream().filter(e -> e.getValue().size() >= 3)
                .sorted((a, b) -> b.getValue().size() - a.getValue().size()).map(e -> {
                    String[] k = e.getKey().split("\\|");
                    return Map.<String, Object>of("category", k[0], "line", k[1].equals("-") ? "—" : lineNames.getOrDefault(UUID.fromString(k[1]), "?"),
                            "occurrences90d", e.getValue().size(), "references", e.getValue().stream().map(NonConformity::getReference).limit(6).toList());
                }).toList());
        LocalDate today = LocalDate.now();
        out.put("overdueCapa", capas.findAll().stream().filter(c -> c.getDueDate() != null && c.getDueDate().isBefore(today)
                        && !Set.of("CLOSED", "CANCELLED", "PENDING_VERIFICATION").contains(c.getStatus()))
                .sorted(Comparator.comparing(CapaAction::getDueDate))
                .map(c -> Map.<String, Object>of("id", c.getId(), "reference", c.getReference(), "title", c.getTitle(),
                        "owner", Objects.toString(c.getOwnerName(), ""), "dueDate", c.getDueDate(), "daysOverdue",
                        ChronoUnit.DAYS.between(c.getDueDate(), today), "priority", Objects.toString(c.getPriority(), ""))).toList());
        out.put("environment", environment());
        return out;
    }

    public Map<String, Object> environment() {
        LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(11);
        Map<String, double[]> byType = new LinkedHashMap<>();
        for (String t : List.of("ENERGY_ELECTRICITY", "ENERGY_GAS", "WATER_CONSUMPTION")) byType.put(t, new double[12]);
        for (EnvironmentalReading r : readings.findAll()) {
            double[] arr = byType.get(r.getReadingType());
            if (arr == null || r.getRecordedAt() == null) continue;
            LocalDate d = r.getRecordedAt().atZone(ZoneOffset.UTC).toLocalDate();
            int idx = (int) ChronoUnit.MONTHS.between(start, d.withDayOfMonth(1));
            if (idx >= 0 && idx < 12) arr[idx] += r.getReadingValue();
        }
        double[] wasteKg = new double[12], recycled = new double[12];
        for (WasteRecord w : waste.findAll()) {
            int idx = (int) ChronoUnit.MONTHS.between(start, w.getRecordedDate().withDayOfMonth(1));
            if (idx < 0 || idx >= 12) continue;
            wasteKg[idx] += w.getQuantityKg();
            if (Set.of("RECYCLING", "RECOVERY", "COMPOSTING").contains(w.getTreatment())) recycled[idx] += w.getQuantityKg();
        }
        List<String> months = new ArrayList<>();
        for (int i = 0; i < 12; i++) months.add(start.plusMonths(i).toString().substring(0, 7));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("months", months);
        m.put("electricityKwh", round(byType.get("ENERGY_ELECTRICITY")));
        m.put("gasM3", round(byType.get("ENERGY_GAS")));
        m.put("waterM3", round(byType.get("WATER_CONSUMPTION")));
        m.put("wasteKg", round(wasteKg));
        m.put("recycledKg", round(recycled));
        m.put("nature", "MEASURED");
        return m;
    }

    public static Map<String, Object> monthly(List<Instant> instants, int months) {
        LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(months - 1);
        int[] counts = new int[months];
        for (Instant i : instants) {
            LocalDate d = i.atZone(ZoneOffset.UTC).toLocalDate();
            int idx = (int) ChronoUnit.MONTHS.between(start, d.withDayOfMonth(1));
            if (idx >= 0 && idx < months) counts[idx]++;
        }
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            labels.add(start.plusMonths(i).toString().substring(0, 7));
            values.add(counts[i]);
        }
        return Map.of("months", labels, "values", values);
    }

    static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    static List<Double> round(double[] v) {
        List<Double> l = new ArrayList<>();
        for (double x : v) l.add(round(x));
        return l;
    }
}
