package com.dnd.smartflow.integration.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/** Small HTTP helper shared by live connectors (timeouts, auth headers, simple client-side throttling). */
public final class HttpSupport {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final long MIN_INTERVAL_MS = 150;
    private static long lastCall;

    private HttpSupport() {
    }

    public static synchronized void throttle() {
        long wait = lastCall + MIN_INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCall = System.currentTimeMillis();
    }

    public static JsonNode get(String url, Map<String, String> headers) throws Exception {
        throttle();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).header("Accept", "application/json").GET();
        headers.forEach(b::header);
        return send(b.build());
    }

    public static JsonNode postJson(String url, Object body, Map<String, String> headers) throws Exception {
        throttle();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
        headers.forEach(b::header);
        return send(b.build());
    }

    public static JsonNode postForm(String url, String form, Map<String, String> headers) throws Exception {
        throttle();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form));
        headers.forEach(b::header);
        return send(b.build());
    }

    private static JsonNode send(HttpRequest req) throws Exception {
        HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 429) throw new IllegalStateException("Remote API rate limit reached (HTTP 429)");
        if (res.statusCode() / 100 != 2) {
            String body = res.body() == null ? "" : res.body();
            throw new IllegalStateException("HTTP " + res.statusCode() + ": " + (body.length() > 300 ? body.substring(0, 300) : body));
        }
        return MAPPER.readTree(res.body());
    }

    public static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static JsonNode path(JsonNode node, String dotted) {
        if (dotted == null || dotted.isBlank()) return node;
        JsonNode cur = node;
        for (String p : dotted.split("\\.")) cur = cur.path(p);
        return cur;
    }
}
