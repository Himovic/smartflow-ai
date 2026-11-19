package com.dnd.smartflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * REST client of the Python AI service (FastAPI). Every call is optional: on timeout, error or when the
 * service is disabled, callers fall back to the built-in deterministic algorithms, so the platform keeps
 * working without the AI service.
 */
@Slf4j
@Component
public class AiServiceClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).version(HttpClient.Version.HTTP_1_1).build();
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final boolean enabled;
    private final Duration timeout;

    public AiServiceClient(ObjectMapper mapper, @Value("${smartflow.ai.base-url:}") String baseUrl,
                           @Value("${smartflow.ai.enabled:true}") boolean enabled, @Value("${smartflow.ai.timeout:20s}") Duration timeout) {
        this.mapper = mapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/$", "");
        this.enabled = enabled && !this.baseUrl.isBlank();
        this.timeout = timeout;
    }

    public <T> Optional<T> post(String path, Object body, Class<T> type) {
        if (!enabled) return Optional.empty();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                log.warn("AI service {} answered {}: {}", path, res.statusCode(), res.body().length() > 300 ? res.body().substring(0, 300) : res.body());
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(res.body(), type));
        } catch (Exception e) {
            log.info("AI service unavailable for {} ({}), using built-in fallback", path, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public boolean health() {
        if (!enabled) return false;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/health")).timeout(Duration.ofSeconds(3)).GET().build();
            return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
