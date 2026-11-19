package com.dnd.smartflow.ai.forecast;

import java.util.ArrayList;
import java.util.List;

/**
 * Built-in forecasting fallback: Holt's linear exponential smoothing with optional additive seasonality
 * (Holt-Winters) when at least two full seasons are available. Smoothing parameters are selected by grid
 * search on a hold-out window and the hold-out MAPE is reported, so accuracy figures are measured, not assumed.
 */
public final class TimeSeriesForecaster {
    private TimeSeriesForecaster() {
    }

    public record Result(String model, double[] forecast, double[] lower, double[] upper, double mape, int holdout, double alpha, double beta, double gamma) {
    }

    public static Result forecast(double[] y, int horizon, int season) {
        int n = y.length;
        boolean seasonal = season > 1 && n >= 2 * season + 8;
        int holdout = Math.min(8, Math.max(0, n / 5));
        double bestErr = Double.MAX_VALUE;
        double[] best = {0.3, 0.1, 0.2};
        double[] alphas = {0.1, 0.2, 0.3, 0.5, 0.7};
        double[] betas = {0.01, 0.05, 0.1, 0.2};
        double[] gammas = seasonal ? new double[]{0.05, 0.1, 0.3} : new double[]{0};
        if (holdout > 0 && n - holdout >= 4) {
            double[] train = java.util.Arrays.copyOf(y, n - holdout);
            for (double a : alphas)
                for (double b : betas)
                    for (double g : gammas) {
                        double[] f = run(train, holdout, season, seasonal && train.length >= 2 * season, a, b, g).forecast;
                        double err = mape(java.util.Arrays.copyOfRange(y, n - holdout, n), f);
                        if (err < bestErr) {
                            bestErr = err;
                            best = new double[]{a, b, g};
                        }
                    }
        }
        Fit fit = run(y, horizon, season, seasonal, best[0], best[1], best[2]);
        double sd = Math.sqrt(fit.sse / Math.max(1, n - 1));
        double[] lo = new double[horizon], hi = new double[horizon];
        for (int h = 0; h < horizon; h++) {
            double w = 1.96 * sd * Math.sqrt(1 + h * 0.15);
            fit.forecast[h] = Math.max(0, fit.forecast[h]);
            lo[h] = Math.max(0, fit.forecast[h] - w);
            hi[h] = fit.forecast[h] + w;
        }
        return new Result(seasonal ? "Holt-Winters additive (built-in)" : "Holt linear trend (built-in)", fit.forecast, lo, hi,
                bestErr == Double.MAX_VALUE ? Double.NaN : Math.round(bestErr * 10) / 10.0, holdout, best[0], best[1], best[2]);
    }

    private record Fit(double[] forecast, double sse) {
    }

    private static Fit run(double[] y, int horizon, int m, boolean seasonal, double a, double b, double g) {
        int n = y.length;
        double level = y[0], trend = n > 1 ? (y[Math.min(n - 1, 4)] - y[0]) / Math.min(n - 1, 4) : 0;
        double[] s = new double[Math.max(1, m)];
        if (seasonal) {
            double mean0 = 0;
            for (int i = 0; i < m; i++) mean0 += y[i];
            mean0 /= m;
            double mean1 = 0;
            for (int i = m; i < 2 * m; i++) mean1 += y[i];
            mean1 /= m;
            level = mean0;
            trend = (mean1 - mean0) / m;
            for (int i = 0; i < m; i++) s[i] = ((y[i] - mean0) + (y[i + m] - mean1)) / 2;
        }
        double sse = 0;
        for (int t = 1; t < n; t++) {
            double season = seasonal ? s[t % m] : 0;
            double pred = level + trend + season;
            sse += (y[t] - pred) * (y[t] - pred);
            double prevLevel = level;
            level = a * (y[t] - season) + (1 - a) * (level + trend);
            trend = b * (level - prevLevel) + (1 - b) * trend;
            if (seasonal) s[t % m] = g * (y[t] - level) + (1 - g) * season;
        }
        double[] f = new double[horizon];
        for (int h = 1; h <= horizon; h++) f[h - 1] = level + h * trend + (seasonal ? s[(n - 1 + h) % m] : 0);
        return new Fit(f, sse);
    }

    static double mape(double[] actual, double[] forecast) {
        double sum = 0;
        int k = 0;
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] == 0) continue;
            sum += Math.abs(actual[i] - forecast[i]) / Math.abs(actual[i]);
            k++;
        }
        return k == 0 ? 0 : sum / k * 100;
    }

    public static List<Double> toList(double[] a) {
        List<Double> l = new ArrayList<>();
        for (double v : a) l.add(Math.round(v * 10) / 10.0);
        return l;
    }
}
