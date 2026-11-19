package com.dnd.smartflow.ai;

import com.dnd.smartflow.ai.assistant.AssistantService;
import com.dnd.smartflow.ai.forecast.ForecastService;
import com.dnd.smartflow.ai.insights.InsightsService;
import com.dnd.smartflow.common.security.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Center", description = "Forecasting, predictive quality & maintenance, QHSE intelligence and assistant")
public class AiController {
    private final ForecastService forecasts;
    private final InsightsService insights;
    private final AssistantService assistant;
    private final AiServiceClient client;
    private final PermissionService perm;

    @GetMapping("/status")
    public Map<String, Object> status() {
        perm.require("ai", "read");
        boolean up = client.health();
        return Map.of("aiServiceConfigured", client.isEnabled(), "aiServiceReachable", up,
                "mode", up ? "Python AI service (OR-Tools, statsmodels, scikit-learn)" : "Built-in deterministic engines (fallback)");
    }

    @GetMapping("/forecast/overview")
    @Operation(summary = "Stockout risk and replenishment recommendations for all products")
    public List<Map<String, Object>> overview() {
        perm.require("ai", "read");
        perm.require("inventory", "read");
        return forecasts.overview();
    }

    @GetMapping("/forecast/{productId}")
    @Operation(summary = "Weekly demand forecast with prediction interval, hold-out accuracy and replenishment advice")
    public Map<String, Object> forecast(@PathVariable UUID productId, @RequestParam(required = false) UUID warehouseId,
                                        @RequestParam(defaultValue = "12") int horizon) {
        perm.require("ai", "read");
        return forecasts.forecast(productId, warehouseId, Math.max(1, Math.min(26, horizon)), perm.can("ai", "write"));
    }

    @GetMapping("/quality")
    public Map<String, Object> quality() {
        perm.require("ai", "read");
        perm.require("quality", "read");
        return insights.quality();
    }

    @GetMapping("/maintenance")
    public Map<String, Object> maintenance() {
        perm.require("ai", "read");
        perm.require("assets", "read");
        return insights.maintenance();
    }

    @GetMapping("/qhse")
    public Map<String, Object> qhse() {
        perm.require("ai", "read");
        return insights.qhse();
    }

    @PostMapping("/assistant")
    @Operation(summary = "Ask the SmartFlow assistant (FR / EN / AR); answers are grounded on authorized platform data")
    public AssistantService.Answer ask(@RequestBody AssistantService.Question q) {
        perm.require("ai", "read");
        if (q.question() == null || q.question().isBlank()) throw new IllegalArgumentException("Question is empty");
        if (q.question().length() > 1000) throw new IllegalArgumentException("Question is too long");
        return assistant.askWithContext(q);
    }

    @GetMapping("/assistant/suggestions")
    public List<String> suggestions(@RequestParam(defaultValue = "fr") String lang) {
        return assistant.suggestions(lang);
    }
}
