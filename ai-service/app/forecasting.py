"""Weekly demand forecasting with exponential smoothing (statsmodels), selected on a back-test hold-out."""
import warnings
from typing import List, Tuple

import numpy as np
from statsmodels.tsa.holtwinters import ExponentialSmoothing

from .models import ForecastRequest, ForecastResult


def _mape(actual: np.ndarray, predicted: np.ndarray) -> float:
    mask = actual != 0
    if not mask.any():
        return 0.0
    return float(np.mean(np.abs((actual[mask] - predicted[mask]) / actual[mask])) * 100)


def _candidates(y: np.ndarray, season: int) -> List[Tuple[str, dict]]:
    c = [("Holt linear trend (damped)", dict(trend="add", damped_trend=True, seasonal=None))]
    if season > 1 and len(y) >= 2 * season + 8:
        c.append(("Holt-Winters additive (statsmodels)", dict(trend="add", damped_trend=True, seasonal="add", seasonal_periods=season)))
    c.append(("Simple exponential smoothing", dict(trend=None, seasonal=None)))
    return c


def _fit(y: np.ndarray, cfg: dict):
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        return ExponentialSmoothing(y, initialization_method="estimated", **cfg).fit(optimized=True)


def forecast(req: ForecastRequest) -> ForecastResult:
    y = np.array([max(0.0, p.value) for p in req.series], dtype=float)
    horizon = max(1, min(52, req.horizon))
    holdout = min(8, max(0, len(y) // 5))
    best_name, best_cfg, best_err = None, None, float("inf")
    for name, cfg in _candidates(y, req.seasonLength):
        try:
            if holdout and len(y) - holdout >= 8:
                train = y[:-holdout]
                if cfg.get("seasonal") and len(train) < 2 * cfg["seasonal_periods"]:
                    continue
                err = _mape(y[-holdout:], _fit(train, cfg).forecast(holdout))
            else:
                err = float("nan")
            if best_name is None or (not np.isnan(err) and err < best_err):
                best_name, best_cfg, best_err = name, cfg, err
        except Exception:  # noqa: BLE001 - try next candidate
            continue
    if best_name is None:
        mean = float(np.mean(y[-8:])) if len(y) else 0.0
        return ForecastResult(model="Moving average (fallback)", forecast=[round(mean, 1)] * horizon, lower=[0.0] * horizon,
                              upper=[round(mean * 1.5, 1)] * horizon, mape=None, holdout=0)
    fit = _fit(y, best_cfg)
    f = np.clip(fit.forecast(horizon), 0, None)
    resid = y - fit.fittedvalues
    sd = float(np.std(resid, ddof=1)) if len(resid) > 1 else 0.0
    widths = np.array([1.96 * sd * np.sqrt(1 + h * 0.15) for h in range(horizon)])
    return ForecastResult(model=best_name, forecast=[round(float(v), 1) for v in f],
                          lower=[round(float(v), 1) for v in np.clip(f - widths, 0, None)],
                          upper=[round(float(v), 1) for v in f + widths],
                          mape=None if np.isinf(best_err) or np.isnan(best_err) else round(best_err, 1), holdout=holdout)
