package com.dnd.smartflow.ai.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Provider abstraction for large language models: OpenAI, any OpenAI-compatible endpoint (Azure, Mistral,
 * vLLM, LM Studio…) and local Ollama. Used only to phrase answers from facts retrieved by the platform.
 */
@Component
@RequiredArgsConstructor
public class LlmClient {
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public record Config(String provider, String baseUrl, String model, String apiKey) {
    }

    public String chat(Config cfg, String system, String user) throws Exception {
        return switch (cfg.provider()) {
            case "OLLAMA" -> ollama(cfg, system, user);
            case "OPENAI", "OPENAI_COMPATIBLE" -> openAi(cfg, system, user);
            default -> throw new IllegalStateException("No LLM provider configured");
        };
    }

    private String openAi(Config cfg, String system, String user) throws Exception {
        String base = cfg.baseUrl() == null || cfg.baseUrl().isBlank() ? "https://api.openai.com/v1" : cfg.baseUrl().replaceAll("/$", "");
        Map<String, Object> body = Map.of("model", cfg.model() == null ? "gpt-4o-mini" : cfg.model(), "temperature", 0.2,
                "messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(base + "/chat/completions")).timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (cfg.apiKey() != null) req.header("Authorization", "Bearer " + cfg.apiKey());
        HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IllegalStateException("LLM provider answered HTTP " + res.statusCode());
        JsonNode n = mapper.readTree(res.body());
        return n.path("choices").path(0).path("message").path("content").asText();
    }

    private String ollama(Config cfg, String system, String user) throws Exception {
        String base = cfg.baseUrl() == null || cfg.baseUrl().isBlank() ? "http://localhost:11434" : cfg.baseUrl().replaceAll("/$", "");
        Map<String, Object> body = Map.of("model", cfg.model() == null ? "llama3.1" : cfg.model(), "stream", false,
                "options", Map.of("temperature", 0.2),
                "messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/api/chat")).timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IllegalStateException("Ollama answered HTTP " + res.statusCode());
        return mapper.readTree(res.body()).path("message").path("content").asText();
    }
}
