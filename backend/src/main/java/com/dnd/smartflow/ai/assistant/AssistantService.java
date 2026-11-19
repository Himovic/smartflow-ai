package com.dnd.smartflow.ai.assistant;

import com.dnd.smartflow.ai.forecast.ForecastService;
import com.dnd.smartflow.ai.insights.InsightsService;
import com.dnd.smartflow.analytics.AlertService;
import com.dnd.smartflow.analytics.DashboardService;
import com.dnd.smartflow.audit.AuditService;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.organization.domain.Site;
import com.dnd.smartflow.organization.domain.SiteRepository;
import com.dnd.smartflow.qhse.domain.*;
import com.dnd.smartflow.tenant.Tenant;
import com.dnd.smartflow.tenant.TenantService;
import com.dnd.smartflow.tenant.TenantSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * SmartFlow AI Assistant. Questions are routed to analytical intents that query the tenant's data with
 * the user's permissions; answers cite the records they come from and say whether figures are measured
 * or predicted. When an LLM provider is configured it only rephrases the retrieved facts; without one the
 * deterministic answers are returned, so the assistant always works.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {
    private final NonConformityRepository ncs;
    private final RiskAssessmentRepository risks;
    private final SiteRepository sites;
    private final InsightsService insights;
    private final ForecastService forecasts;
    private final DashboardService dashboards;
    private final AlertService alerts;
    private final TenantService tenants;
    private final LlmClient llm;
    private final ObjectMapper mapper;
    private final AuditService audit;

    public record Question(String question, String language) {
    }

    public record Reference(String entityType, String id, String label, String route) {
    }

    public record Answer(String intent, String language, String answer, String dataNature, List<Map<String, Object>> table,
                         List<Reference> references, String engine, List<String> suggestions) {
    }

    private record Intent(String key, String module, Pattern pattern, Function<String, Answer> handler) {
    }

    private List<Intent> intents() {
        return List.of(
                new Intent("critical_nc", "nc", p("(critical|critique|critiques|حرج|خطير).*(non.?conform|nc\\b|عدم المطابقة)|(non.?conform|nc\\b|عدم المطابقة).*(critical|critique|حرج|خطير)"), this::criticalNc),
                new Intent("line_defects", "quality", p("(line|ligne|خط).*(defect|defaut|défaut|عيوب|عيب)|(defect|defaut|défaut|عيوب).*(line|ligne|خط)"), this::lineDefects),
                new Intent("overdue_capa", "capa", p("(capa|action|إجراء).*(overdue|late|retard|متأخر)|(overdue|retard|متأخر).*(capa|action|إجراء)"), this::overdueCapa),
                new Intent("batch_quality", "quality", p("(batch|lot|lots|دفعة|دفعات).*(quality|qualite|qualité|جودة|issue|probleme|problème)"), this::batchQuality),
                new Intent("site_risks", "risk", p("(risk|risque|risques|مخاطر|خطر)"), this::siteRisks),
                new Intent("vehicle_maintenance", "fleet", p("(vehicle|vehicule|véhicule|camion|truck|مركبة|شاحنة).*(maintenance|صيانة)|(maintenance|صيانة).*(vehicle|vehicule|véhicule|camion|مركبة)"), this::vehicleMaintenance),
                new Intent("equipment_maintenance", "assets", p("(equipment|equipement|équipement|machine|معدات|آلة).*(maintenance|failure|panne|صيانة|عطل)"), this::equipmentMaintenance),
                new Intent("co2", "environment", p("(co2|carbon|carbone|emission|émission|كربون|انبعاث)"), this::co2),
                new Intent("supplier_quality", "catalog", p("(supplier|fournisseur|fournisseurs|مورد|موردين)"), this::suppliers),
                new Intent("stockout", "inventory", p("(stock|rupture|stockout|replenish|réappro|مخزون|نفاد)"), this::stockout),
                new Intent("priorities", "dashboard", p("(priorit|أولوي|focus|should .* do|que dois|quoi faire)"), this::priorities),
                new Intent("incidents", "incidents", p("(incident|accident|near.?miss|presque|حادث|حوادث)"), this::incidents));
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    @Transactional(readOnly = true)
    public Answer ask(Question q) {
        String text = q.question() == null ? "" : q.question().trim();
        String lang = q.language() != null ? q.language() : detect(text);
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        CurrentUser u = CurrentUser.require();
        Answer base = null;
        for (Intent i : intents()) {
            if (i.pattern().matcher(text).find() || i.pattern().matcher(normalized).find()) {
                if (!u.can(i.module(), "read")) {
                    base = new Answer(i.key(), lang, t(lang, "You are not authorized to access this information.",
                            "Vous n'êtes pas autorisé à consulter ces informations.", "ليس لديك صلاحية الاطلاع على هذه المعلومات."),
                            "NONE", List.of(), List.of(), "rules", suggestions(lang));
                } else {
                    base = i.handler().apply(lang);
                }
                break;
            }
        }
        if (base == null) base = help(lang);
        audit.log("ASSISTANT_QUESTION", "Assistant", null, Map.of("intent", base.intent(), "question", text.length() > 300 ? text.substring(0, 300) : text));
        return phrase(text, base);
    }

    /** Lets a configured LLM rephrase the grounded answer; never adds facts. Falls back silently. */
    private Answer phrase(String question, Answer base) {
        Tenant t = tenants.current();
        TenantSettings.LlmSettings cfg = t.getSettings().getLlm();
        if (cfg == null || cfg.getProvider() == null || "NONE".equals(cfg.getProvider()) || "NONE".equals(base.dataNature())) return base;
        try {
            String facts = mapper.writeValueAsString(Map.of("draftAnswer", base.answer(), "table", base.table(), "dataNature", base.dataNature(),
                    "references", base.references().stream().map(Reference::label).toList()));
            String system = "You are SmartFlow AI, an assistant for an industrial QHSE and distribution platform. Answer ONLY with the facts provided "
                    + "in JSON. Never invent figures, records or names. Keep record references exactly as given. State clearly when figures are "
                    + "predictions. Be concise and actionable. Answer in " + switch (base.language()) {
                case "ar" -> "Arabic";
                case "en" -> "English";
                default -> "French";
            } + ".";
            String content = llm.chat(new LlmClient.Config(cfg.getProvider(), cfg.getBaseUrl(), cfg.getModel(), tenants.decryptLlmKey(t)), system,
                    "Question: " + question + "\nFacts: " + facts);
            if (content == null || content.isBlank()) return base;
            return new Answer(base.intent(), base.language(), content, base.dataNature(), base.table(), base.references(),
                    "llm:" + cfg.getProvider().toLowerCase() + " (grounded on platform data)", base.suggestions());
        } catch (Exception e) {
            log.info("LLM provider unavailable ({}), returning deterministic answer", e.getMessage());
            return base;
        }
    }

    // ---------------------------------------------------------------- intents
    private Answer criticalNc(String lang) {
        Instant monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<NonConformity> list = ncs.findAll((r, q, cb) -> cb.and(cb.equal(r.get("severity"), "CRITICAL"),
                cb.or(cb.greaterThanOrEqualTo(r.get("detectedAt"), monthStart), cb.not(r.get("status").in("CLOSED", "CANCELLED")))));
        list = list.stream().filter(n -> CurrentUser.require().canAccessSite(n.getSiteId()))
                .sorted(Comparator.comparing(NonConformity::getDetectedAt, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        List<Map<String, Object>> rows = list.stream().limit(10).map(n -> row("reference", n.getReference(), "title", n.getTitle(),
                "status", n.getStatus(), "detectedAt", n.getDetectedAt(), "dueDate", n.getDueDate())).toList();
        String a = list.isEmpty()
                ? t(lang, "No critical non-conformity this month and none open.", "Aucune non-conformité critique ce mois-ci ni en cours.", "لا توجد حالات عدم مطابقة حرجة هذا الشهر.")
                : t(lang, list.size() + " critical non-conformities are open or were detected this month. Most recent: " + list.get(0).getReference() + " — " + list.get(0).getTitle() + ".",
                list.size() + " non-conformités critiques sont ouvertes ou détectées ce mois-ci. La plus récente : " + list.get(0).getReference() + " — " + list.get(0).getTitle() + ".",
                "يوجد " + list.size() + " حالات عدم مطابقة حرجة مفتوحة أو مكتشفة هذا الشهر. الأحدث: " + list.get(0).getReference() + ".");
        return new Answer("critical_nc", lang, a, "MEASURED", rows,
                list.stream().limit(10).map(n -> new Reference("NonConformity", n.getId().toString(), n.getReference(), "/non-conformities/" + n.getId())).toList(),
                "rules", suggestions(lang));
    }

    @SuppressWarnings("unchecked")
    private Answer lineDefects(String lang) {
        List<Map<String, Object>> rows = ((List<Map<String, Object>>) insights.quality().get("lineDefectRates")).stream().limit(8).toList();
        if (rows.isEmpty()) return empty("line_defects", lang);
        Map<String, Object> top = rows.get(0);
        String a = t(lang, "Highest defect rate (last 90 days): " + top.get("name") + " with " + top.get("ncPer100Batches") + " NC per 100 batches ("
                        + top.get("nonConformities90d") + " NC for " + top.get("batches90d") + " batches).",
                "Taux de défauts le plus élevé (90 derniers jours) : " + top.get("name") + " avec " + top.get("ncPer100Batches") + " NC pour 100 lots ("
                        + top.get("nonConformities90d") + " NC pour " + top.get("batches90d") + " lots).",
                "أعلى معدل عيوب (آخر 90 يومًا): " + top.get("name") + " بمعدل " + top.get("ncPer100Batches") + " حالة لكل 100 دفعة.");
        return new Answer("line_defects", lang, a, "MEASURED", rows, List.of(), "rules", suggestions(lang));
    }

    @SuppressWarnings("unchecked")
    private Answer overdueCapa(String lang) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) insights.qhse().get("overdueCapa");
        String a = rows.isEmpty() ? t(lang, "No CAPA action is overdue.", "Aucune action CAPA n'est en retard.", "لا توجد إجراءات تصحيحية متأخرة.")
                : t(lang, rows.size() + " CAPA actions are overdue. The oldest is " + rows.get(0).get("reference") + " (" + rows.get(0).get("daysOverdue") + " days late, owner " + rows.get(0).get("owner") + ").",
                rows.size() + " actions CAPA sont en retard. La plus ancienne : " + rows.get(0).get("reference") + " (" + rows.get(0).get("daysOverdue") + " jours de retard, responsable " + rows.get(0).get("owner") + ").",
                "يوجد " + rows.size() + " إجراءات متأخرة. أقدمها " + rows.get(0).get("reference") + " (" + rows.get(0).get("daysOverdue") + " يومًا).");
        return new Answer("overdue_capa", lang, a, "MEASURED", rows.stream().limit(12).toList(),
                rows.stream().limit(12).map(r -> new Reference("CapaAction", r.get("id").toString(), (String) r.get("reference"), "/capa/" + r.get("id"))).toList(),
                "rules", suggestions(lang));
    }

    @SuppressWarnings("unchecked")
    private Answer batchQuality(String lang) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) insights.quality().get("highRiskBatches");
        String a = rows.isEmpty() ? t(lang, "No batch currently shows quality risk signals.", "Aucun lot ne présente actuellement de signal de risque qualité.", "لا توجد دفعات بمؤشرات خطر جودة حاليًا.")
                : t(lang, rows.size() + " batches show quality risk signals. Highest: " + rows.get(0).get("batchNumber") + " (" + rows.get(0).get("product") + ", score " + rows.get(0).get("riskScore") + ": " + rows.get(0).get("reasons") + ").",
                rows.size() + " lots présentent des signaux de risque qualité. Le plus exposé : " + rows.get(0).get("batchNumber") + " (" + rows.get(0).get("product") + ", score " + rows.get(0).get("riskScore") + " : " + rows.get(0).get("reasons") + ").",
                "توجد " + rows.size() + " دفعات بمؤشرات خطر جودة. الأعلى: " + rows.get(0).get("batchNumber") + ".");
        return new Answer("batch_quality", lang, a + " " + t(lang, "(Risk index computed from open NCs, inspections, supplier score and expiry.)",
                "(Indice de risque calculé à partir des NC ouvertes, inspections, score fournisseur et péremption.)", "(مؤشر خطر محسوب من البيانات.)"),
                "MIXED", rows, rows.stream().map(r -> new Reference("Batch", r.get("batchId").toString(), (String) r.get("batchNumber"), "/traceability/" + r.get("batchId"))).toList(),
                "rules", suggestions(lang));
    }

    private Answer siteRisks(String lang) {
        List<Site> all = sites.findAll();
        String q = lastQuestion.get() == null ? "" : lastQuestion.get().toLowerCase(Locale.ROOT);
        Site site = all.stream().filter(s -> q.contains(s.getName().toLowerCase(Locale.ROOT)) || (s.getCity() != null && q.contains(s.getCity().toLowerCase(Locale.ROOT)))
                || q.contains(s.getCode().toLowerCase(Locale.ROOT))).findFirst().orElse(null);
        List<RiskAssessment> list = risks.findAll((r, cq, cb) -> cb.notEqual(r.get("status"), "ARCHIVED")).stream()
                .filter(r -> site == null || site.getId().equals(r.getSiteId()))
                .filter(r -> CurrentUser.require().canAccessSite(r.getSiteId()))
                .sorted(Comparator.comparing((RiskAssessment r) -> Optional.ofNullable(r.getResidualScore()).orElse(r.getRiskScore())).reversed()).limit(8).toList();
        String where = site == null ? t(lang, "across all sites", "sur l'ensemble des sites", "في جميع المواقع") : t(lang, "at " + site.getName(), "sur le site " + site.getName(), "في موقع " + site.getName());
        List<Map<String, Object>> rows = list.stream().map(r -> row("reference", r.getReference(), "title", r.getTitle(), "hazard", r.getHazardCategory(),
                "initial", r.getRiskScore() + " " + r.getRiskLevel(), "residual", r.getResidualScore() == null ? "—" : r.getResidualScore() + " " + r.getResidualLevel())).toList();
        String a = list.isEmpty() ? t(lang, "No active risk assessment " + where + ".", "Aucune évaluation de risque active " + where + ".", "لا توجد تقييمات مخاطر نشطة " + where + ".")
                : t(lang, "Main risks " + where + ": " + list.get(0).getTitle() + " (score " + list.get(0).getRiskScore() + ")" + (list.size() > 1 ? ", " + list.get(1).getTitle() : "") + ".",
                "Principaux risques " + where + " : " + list.get(0).getTitle() + " (score " + list.get(0).getRiskScore() + ")" + (list.size() > 1 ? ", " + list.get(1).getTitle() : "") + ".",
                "أهم المخاطر " + where + ": " + list.get(0).getTitle() + ".");
        return new Answer("site_risks", lang, a, "MEASURED", rows,
                list.stream().map(r -> new Reference("RiskAssessment", r.getId().toString(), r.getReference(), "/risk-assessments")).toList(), "rules", suggestions(lang));
    }

    @SuppressWarnings("unchecked")
    private Answer vehicleMaintenance(String lang) {
        List<Map<String, Object>> rows = ((List<Map<String, Object>>) insights.maintenance().get("vehicles")).stream().limit(8).toList();
        if (rows.isEmpty()) return empty("vehicle_maintenance", lang);
        String a = t(lang, "Vehicles with the highest maintenance risk: " + rows.get(0).get("plateNumber") + " (" + rows.get(0).get("kmToService") + " km to service)"
                        + (rows.size() > 1 ? ", " + rows.get(1).get("plateNumber") : "") + ". Risk index based on mileage to service and vehicle age.",
                "Véhicules au risque de maintenance le plus élevé : " + rows.get(0).get("plateNumber") + " (" + rows.get(0).get("kmToService") + " km avant révision)"
                        + (rows.size() > 1 ? ", " + rows.get(1).get("plateNumber") : "") + ". Indice basé sur le kilométrage avant révision et l'usure.",
                "المركبات الأعلى خطرًا من حيث الصيانة: " + rows.get(0).get("plateNumber") + ".");
        return new Answer("vehicle_maintenance", lang, a, "RISK_INDEX", rows, List.of(), "rules", suggestions(lang));
    }

    @SuppressWarnings("unchecked")
    private Answer equipmentMaintenance(String lang) {
        Map<String, Object> m = insights.maintenance();
        List<Map<String, Object>> rows = ((List<Map<String, Object>>) m.get("equipment")).stream().limit(8).toList();
        if (rows.isEmpty()) return empty("equipment_maintenance", lang);
        String a = t(lang, "Equipment availability is " + m.get("availability") + "%. Highest failure risk: " + rows.get(0).get("name") + " (" + rows.get(0).get("reasons") + "). " + rows.get(0).get("recommendation") + ".",
                "Disponibilité des équipements : " + m.get("availability") + " %. Risque de panne le plus élevé : " + rows.get(0).get("name") + " (" + rows.get(0).get("reasons") + ").",
                "توافر المعدات " + m.get("availability") + "%. أعلى خطر عطل: " + rows.get(0).get("name") + ".");
        return new Answer("equipment_maintenance", lang, a, "RISK_INDEX", rows, List.of(), "rules", suggestions(lang));
    }

    private Answer co2(String lang) {
        LocalDate first = LocalDate.now().withDayOfMonth(1).minusMonths(1);
        LocalDate last = first.plusMonths(1).minusDays(1);
        Map<String, Double> m = dashboards.co2Breakdown(DashboardService.Filter.of(null, first, last));
        Map<String, Double> prev = dashboards.co2Breakdown(DashboardService.Filter.of(null, first.minusMonths(1), first.minusDays(1)));
        String month = first.toString().substring(0, 7);
        double tonnes = Math.round(m.get("total") / 100.0) / 10.0;
        String a = t(lang, "CO2 footprint for " + month + ": " + tonnes + " t CO2e — electricity " + Math.round(m.get("electricity")) + " kg, natural gas "
                        + Math.round(m.get("gas")) + " kg, transport " + Math.round(m.get("transport")) + " kg. Previous month: " + Math.round(prev.get("total") / 100.0) / 10.0 + " t.",
                "Empreinte CO2 de " + month + " : " + tonnes + " t CO2e — électricité " + Math.round(m.get("electricity")) + " kg, gaz naturel "
                        + Math.round(m.get("gas")) + " kg, transport " + Math.round(m.get("transport")) + " kg. Mois précédent : " + Math.round(prev.get("total") / 100.0) / 10.0 + " t.",
                "البصمة الكربونية لشهر " + month + ": " + tonnes + " طن مكافئ CO2 (كهرباء " + Math.round(m.get("electricity")) + " كغ، غاز " + Math.round(m.get("gas"))
                        + " كغ، نقل " + Math.round(m.get("transport")) + " كغ).");
        return new Answer("co2", lang, a + " " + t(lang, "Energy is measured (meter readings × emission factors); transport is estimated from distance × consumption.",
                "L'énergie est mesurée (relevés × facteurs d'émission) ; le transport est estimé (distance × consommation).", "الطاقة مقاسة، والنقل تقديري."),
                "MEASURED", List.of(row("month", month, "electricityKg", m.get("electricity"), "gasKg", m.get("gas"), "transportKg", m.get("transport"), "totalKg", m.get("total")),
                row("month", first.minusMonths(1).toString().substring(0, 7), "electricityKg", prev.get("electricity"), "gasKg", prev.get("gas"),
                        "transportKg", prev.get("transport"), "totalKg", prev.get("total"))), List.of(), "rules", suggestions(lang));
    }

    @SuppressWarnings("unchecked")
    private Answer suppliers(String lang) {
        List<Map<String, Object>> rows = ((List<Map<String, Object>>) insights.quality().get("supplierPerformance")).stream().limit(8).toList();
        if (rows.isEmpty()) return empty("supplier_quality", lang);
        Map<String, Object> w = rows.get(0);
        String a = t(lang, "Weakest supplier quality performance: " + w.get("name") + " (index " + w.get("performanceIndex") + "/100, " + w.get("nonConformities12m") + " NC in 12 months, "
                        + w.get("inspectionFailRate") + "% incoming inspection failures).",
                "Fournisseur le moins performant en qualité : " + w.get("name") + " (indice " + w.get("performanceIndex") + "/100, " + w.get("nonConformities12m") + " NC sur 12 mois, "
                        + w.get("inspectionFailRate") + " % d'échecs en contrôle réception).",
                "أضعف مورد من حيث الجودة: " + w.get("name") + " (المؤشر " + w.get("performanceIndex") + "/100).");
        return new Answer("supplier_quality", lang, a, "MIXED", rows,
                rows.stream().map(r -> new Reference("Supplier", r.get("supplierId").toString(), (String) r.get("name"), "/suppliers")).toList(), "rules", suggestions(lang));
    }

    private Answer stockout(String lang) {
        List<Map<String, Object>> rows = forecasts.overview().stream().filter(m -> !"LOW".equals(m.get("stockoutRisk"))).limit(10).toList();
        String a = rows.isEmpty() ? t(lang, "No product shows a stockout risk over the coming weeks.", "Aucun produit ne présente de risque de rupture dans les prochaines semaines.", "لا يوجد خطر نفاد مخزون متوقع.")
                : t(lang, rows.size() + " products are at stockout risk. Most exposed: " + rows.get(0).get("name") + " with " + rows.get(0).get("daysOfCover") + " days of cover; recommended replenishment "
                        + rows.get(0).get("recommendedReplenishment") + " units.",
                rows.size() + " produits sont en risque de rupture. Le plus exposé : " + rows.get(0).get("name") + " avec " + rows.get(0).get("daysOfCover") + " jours de couverture ; réapprovisionnement recommandé "
                        + rows.get(0).get("recommendedReplenishment") + " unités.",
                "يوجد " + rows.size() + " منتجات معرضة لنفاد المخزون. الأكثر عرضة: " + rows.get(0).get("name") + ".");
        return new Answer("stockout", lang, a + " " + t(lang, "(Forecast-based prediction.)", "(Prédiction issue des prévisions.)", "(تنبؤ.)"), "PREDICTION", rows, List.of(), "rules", suggestions(lang));
    }

    private Answer priorities(String lang) {
        List<AlertService.Alert> list = alerts.alerts();
        List<AlertService.Alert> crit = list.stream().filter(a -> "CRITICAL".equals(a.severity())).limit(6).toList();
        List<AlertService.Alert> top = crit.isEmpty() ? list.stream().limit(6).toList() : crit;
        StringBuilder sb = new StringBuilder(t(lang, "Recommended priorities, by severity:", "Priorités recommandées, par gravité :", "الأولويات الموصى بها حسب الخطورة:"));
        int i = 1;
        for (AlertService.Alert a : top) sb.append("\n").append(i++).append(". ").append(a.title()).append(" — ").append(a.detail());
        if (top.isEmpty()) sb.append(" ").append(t(lang, "nothing urgent at the moment.", "rien d'urgent pour le moment.", "لا شيء عاجل حاليًا."));
        return new Answer("priorities", lang, sb.toString(), "MEASURED",
                top.stream().map(a -> row("severity", a.severity(), "category", a.category(), "title", a.title(), "detail", a.detail())).toList(),
                top.stream().filter(a -> a.entityId() != null).map(a -> new Reference(a.entityType(), a.entityId().toString(), a.title(), a.route())).toList(),
                "rules", suggestions(lang));
    }

    @SuppressWarnings("unchecked")
    private Answer incidents(String lang) {
        Map<String, Object> q = insights.qhse();
        Map<String, Object> trend = (Map<String, Object>) q.get("incidentTrend");
        Map<String, Object> nm = (Map<String, Object>) q.get("nearMissTrend");
        List<Integer> v = (List<Integer>) trend.get("values");
        List<Integer> n = (List<Integer>) nm.get("values");
        int cur = v.get(v.size() - 1), prev = v.get(v.size() - 2);
        String a = t(lang, "Incidents this month: " + cur + " (previous month " + prev + "), of which near misses: " + n.get(n.size() - 1) + ". Reporting near misses is a positive safety-culture signal.",
                "Incidents ce mois-ci : " + cur + " (mois précédent " + prev + "), dont presque-accidents : " + n.get(n.size() - 1) + ". La remontée des presque-accidents est un signal positif de culture sécurité.",
                "الحوادث هذا الشهر: " + cur + " (الشهر السابق " + prev + ")، منها حوادث وشيكة: " + n.get(n.size() - 1) + ".");
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> months = (List<String>) trend.get("months");
        for (int i = 0; i < months.size(); i++) rows.add(row("month", months.get(i), "incidents", v.get(i), "nearMisses", n.get(i)));
        return new Answer("incidents", lang, a, "MEASURED", rows, List.of(), "rules", suggestions(lang));
    }

    private Answer help(String lang) {
        return new Answer("help", lang, t(lang, "I answer questions about your operational and QHSE data. Try one of the suggestions below.",
                "Je réponds aux questions sur vos données opérationnelles et QHSE. Essayez l'une des suggestions ci-dessous.",
                "أجيب عن الأسئلة المتعلقة ببياناتك التشغيلية وبيانات الجودة والسلامة والبيئة. جرّب أحد الاقتراحات أدناه."),
                "NONE", List.of(), List.of(), "rules", suggestions(lang));
    }

    private Answer empty(String intent, String lang) {
        return new Answer(intent, lang, t(lang, "No data available yet for this question.", "Pas encore de données pour cette question.", "لا توجد بيانات بعد."),
                "MEASURED", List.of(), List.of(), "rules", suggestions(lang));
    }

    public List<String> suggestions(String lang) {
        return switch (lang) {
            case "en" -> List.of("What are the most critical non-conformities this month?", "Which production lines have the highest defect rates?",
                    "Which CAPA actions are overdue?", "Which batches have quality issues?", "What are the main safety risks at Constantine?",
                    "Which vehicles have the highest maintenance risk?", "What is the monthly CO2 footprint?", "Which suppliers have the worst quality performance?",
                    "What actions should the QHSE manager prioritize?", "Which products are at risk of stockout?");
            case "ar" -> List.of("ما هي أخطر حالات عدم المطابقة هذا الشهر؟", "ما هي خطوط الإنتاج ذات أعلى معدل عيوب؟", "ما هي الإجراءات التصحيحية المتأخرة؟",
                    "ما هي الدفعات التي بها مشاكل جودة؟", "ما هي أهم المخاطر في قسنطينة؟", "ما هي المركبات الأعلى خطرًا من حيث الصيانة؟",
                    "ما هي البصمة الكربونية CO2 لهذا الشهر؟", "ما هم الموردون الأضعف من حيث الجودة؟", "ما هي الأولويات لمدير الجودة والسلامة؟");
            default -> List.of("Quelles sont les non-conformités critiques ce mois-ci ?", "Quelles lignes de production ont le plus de défauts ?",
                    "Quelles actions CAPA sont en retard ?", "Quels lots présentent des problèmes qualité ?", "Quels sont les principaux risques sur le site de Constantine ?",
                    "Quels véhicules ont le risque de maintenance le plus élevé ?", "Quelle est l'empreinte CO2 du mois ?", "Quels fournisseurs ont la pire performance qualité ?",
                    "Quelles actions le responsable QHSE doit-il prioriser ?", "Quels produits risquent une rupture de stock ?");
        };
    }

    // ---------------------------------------------------------------- utils
    private final ThreadLocal<String> lastQuestion = new ThreadLocal<>();

    public Answer askWithContext(Question q) {
        lastQuestion.set(q.question());
        try {
            return ask(q);
        } finally {
            lastQuestion.remove();
        }
    }

    static String detect(String text) {
        if (text.codePoints().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ARABIC)) return "ar";
        String l = text.toLowerCase(Locale.ROOT);
        if (l.matches(".*\\b(quel|quelle|quels|quelles|les|des|du|ce|est|sont|mois|lots|retard|fournisseur|véhicule|risque)\\b.*")) return "fr";
        return "en";
    }

    private static String t(String lang, String en, String fr, String ar) {
        return switch (lang) {
            case "en" -> en;
            case "ar" -> ar;
            default -> fr;
        };
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
