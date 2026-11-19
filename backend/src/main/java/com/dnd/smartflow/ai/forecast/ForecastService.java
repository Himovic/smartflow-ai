package com.dnd.smartflow.ai.forecast;

import com.dnd.smartflow.ai.AiServiceClient;
import com.dnd.smartflow.ai.domain.AiPrediction;
import com.dnd.smartflow.ai.domain.AiPredictionRepository;
import com.dnd.smartflow.catalog.domain.Product;
import com.dnd.smartflow.catalog.domain.ProductRepository;
import com.dnd.smartflow.common.tenant.TenantContext;
import com.dnd.smartflow.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Demand forecasting on weekly order history. The ai-service (statsmodels/scikit-learn) is used when
 * reachable; otherwise the built-in Holt/Holt-Winters model. Every response states the model actually
 * used and its measured hold-out error, and results are stored as predictions.
 */
@Service
@RequiredArgsConstructor
public class ForecastService {
    private final JdbcTemplate jdbc;
    private final ProductRepository products;
    private final AiServiceClient ai;
    private final AiPredictionRepository predictions;

    public record SeriesPoint(String period, double value) {
    }

    public record RemoteRequest(List<SeriesPoint> series, int horizon, int seasonLength) {
    }

    public record RemoteResult(String model, List<Double> forecast, List<Double> lower, List<Double> upper, Double mape, Integer holdout) {
    }

    public List<SeriesPoint> weeklyHistory(UUID productId, UUID warehouseId, int weeks) {
        UUID tenant = TenantContext.require();
        LocalDate end = LocalDate.now().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate start = end.minusWeeks(weeks);
        StringBuilder sql = new StringBuilder("""
                select date_trunc('week', o.order_date)::date as wk, sum(l.quantity) as qty
                from sales_order_lines l join sales_orders o on o.id = l.order_id
                where l.tenant_id = ? and o.tenant_id = ? and l.product_id = ? and o.status <> 'CANCELLED'
                  and o.order_date >= ? and o.order_date < ?""");
        List<Object> args = new ArrayList<>(List.of(tenant, tenant, productId, Date.valueOf(start), Date.valueOf(end)));
        if (warehouseId != null) {
            sql.append(" and o.warehouse_id = ?");
            args.add(warehouseId);
        }
        sql.append(" group by 1 order by 1");
        Map<LocalDate, Double> byWeek = new HashMap<>();
        jdbc.query(sql.toString(), rs -> {
            byWeek.put(rs.getDate("wk").toLocalDate(), rs.getDouble("qty"));
        }, args.toArray());
        List<SeriesPoint> out = new ArrayList<>();
        for (LocalDate w = start.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)); w.isBefore(end); w = w.plusWeeks(1)) {
            out.add(new SeriesPoint(w.toString(), byWeek.getOrDefault(w, 0.0)));
        }
        return out;
    }

    public int freeStock(UUID productId, UUID warehouseId) {
        UUID tenant = TenantContext.require();
        String sql = """
                select coalesce(sum(s.quantity - coalesce(s.reserved_quantity,0)),0) from stock_levels s
                left join batches b on b.id = s.batch_id
                where s.tenant_id = ? and s.product_id = ? and (b.id is null or b.status = 'RELEASED')""" + (warehouseId != null ? " and s.warehouse_id = ?" : "");
        Object[] args = warehouseId != null ? new Object[]{tenant, productId, warehouseId} : new Object[]{tenant, productId};
        Integer v = jdbc.queryForObject(sql, Integer.class, args);
        return v == null ? 0 : v;
    }

    @Transactional
    public Map<String, Object> forecast(UUID productId, UUID warehouseId, int horizon, boolean persist) {
        Product p = products.findById(productId).orElseThrow(() -> ApiException.notFound("Product"));
        List<SeriesPoint> history = weeklyHistory(productId, warehouseId, 104);
        while (!history.isEmpty() && history.get(0).value() == 0) history.remove(0);
        if (history.size() < 8) throw ApiException.badRequest("INSUFFICIENT_HISTORY", "At least 8 weeks of demand history are required");
        double[] y = history.stream().mapToDouble(SeriesPoint::value).toArray();
        RemoteResult r = ai.post("/forecast", new RemoteRequest(history, horizon, 52), RemoteResult.class).orElseGet(() -> {
            TimeSeriesForecaster.Result f = TimeSeriesForecaster.forecast(y, horizon, 52);
            return new RemoteResult(f.model(), TimeSeriesForecaster.toList(f.forecast()), TimeSeriesForecaster.toList(f.lower()),
                    TimeSeriesForecaster.toList(f.upper()), Double.isNaN(f.mape()) ? null : f.mape(), f.holdout());
        });
        LocalDate last = LocalDate.parse(history.get(history.size() - 1).period());
        List<Map<String, Object>> points = new ArrayList<>();
        for (int h = 0; h < r.forecast().size(); h++) {
            points.add(Map.of("period", last.plusWeeks(h + 1).toString(), "value", r.forecast().get(h),
                    "lower", r.lower().get(h), "upper", r.upper().get(h)));
        }
        int free = freeStock(productId, warehouseId);
        double next4 = r.forecast().stream().limit(4).mapToDouble(Double::doubleValue).sum();
        double weekly = r.forecast().stream().limit(4).mapToDouble(Double::doubleValue).average().orElse(0);
        double sd = std(Arrays.copyOfRange(y, Math.max(0, y.length - 12), y.length));
        double safety = 1.65 * sd * Math.sqrt(2); // 95% service level, 2-week lead time
        double daysOfCover = weekly <= 0 ? 999 : free / (weekly / 7.0);
        String risk = daysOfCover < 7 ? "HIGH" : daysOfCover < 14 ? "MEDIUM" : "LOW";
        long recommended = Math.max(0, Math.round(next4 + safety - free));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("product", Map.of("id", p.getId(), "sku", p.getSku(), "name", p.getName()));
        out.put("model", r.model());
        out.put("dataNature", "History = measured order quantities; forecast, interval and stockout risk = predictions.");
        out.put("history", history);
        out.put("forecast", points);
        out.put("metrics", metrics(r));
        Map<String, Object> stock = new LinkedHashMap<>();
        stock.put("freeStock", free);
        stock.put("forecastNext4Weeks", Math.round(next4));
        stock.put("safetyStock", Math.round(safety));
        stock.put("daysOfCover", Math.round(daysOfCover * 10) / 10.0);
        stock.put("stockoutRisk", risk);
        stock.put("recommendedReplenishment", recommended);
        out.put("stock", stock);
        if (persist) {
            AiPrediction pred = new AiPrediction();
            pred.setPredictionType("DEMAND_FORECAST");
            pred.setTargetType("Product");
            pred.setTargetId(productId);
            pred.setModel(r.model());
            pred.setHorizon(horizon);
            pred.setResult(Map.of("forecast", points, "stock", stock));
            pred.setMetrics(metrics(r));
            predictions.save(pred);
        }
        return out;
    }

    /** Stockout-risk overview of all active products (built-in model for speed). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> overview() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Product p : products.findAll()) {
            if (Boolean.FALSE.equals(p.getActive())) continue;
            List<SeriesPoint> h = weeklyHistory(p.getId(), null, 26);
            double[] y = h.stream().mapToDouble(SeriesPoint::value).toArray();
            if (Arrays.stream(y).sum() == 0) continue;
            TimeSeriesForecaster.Result f = TimeSeriesForecaster.forecast(y, 4, 0);
            double weekly = Arrays.stream(f.forecast()).average().orElse(0);
            int free = freeStock(p.getId(), null);
            double cover = weekly <= 0 ? 999 : free / (weekly / 7.0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("productId", p.getId());
            m.put("sku", p.getSku());
            m.put("name", p.getName());
            m.put("category", p.getCategory());
            m.put("freeStock", free);
            m.put("forecastWeekly", Math.round(weekly));
            m.put("lastWeekActual", Math.round(y[y.length - 1]));
            m.put("daysOfCover", Math.round(Math.min(cover, 999) * 10) / 10.0);
            m.put("stockoutRisk", cover < 7 ? "HIGH" : cover < 14 ? "MEDIUM" : "LOW");
            m.put("recommendedReplenishment", Math.max(0, Math.round(weekly * 4 - free)));
            out.add(m);
        }
        out.sort(Comparator.comparingDouble(m -> (Double) m.get("daysOfCover")));
        return out;
    }

    private static Map<String, Object> metrics(RemoteResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("holdoutWeeks", r.holdout());
        m.put("mapePercent", r.mape());
        m.put("note", "MAPE measured by back-testing the model on the most recent hold-out weeks");
        return m;
    }

    private static double std(double[] v) {
        if (v.length < 2) return 0;
        double mean = Arrays.stream(v).average().orElse(0);
        return Math.sqrt(Arrays.stream(v).map(x -> (x - mean) * (x - mean)).sum() / (v.length - 1));
    }

    static long weeksBetween(LocalDate a, LocalDate b) {
        return ChronoUnit.WEEKS.between(a, b);
    }
}
