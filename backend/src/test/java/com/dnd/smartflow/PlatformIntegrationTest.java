package com.dnd.smartflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test against a real PostgreSQL (CI service container): boots the platform, seeds the demo
 * tenant and exercises authentication, tenant isolation, dashboards, workflow and AI fallback paths.
 * Enabled with SMARTFLOW_IT=true and IT_DB_URL.
 */
@EnabledIfEnvironmentVariable(named = "SMARTFLOW_IT", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformIntegrationTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv().getOrDefault("IT_DB_URL", "jdbc:postgresql://localhost:5432/smartflow_it"));
        r.add("spring.datasource.username", () -> "smartflow");
        r.add("spring.datasource.password", () -> "smartflow");
        r.add("smartflow.ai.enabled", () -> "false");
        r.add("smartflow.integrations.scheduler-enabled", () -> "false");
        r.add("smartflow.storage.path", () -> System.getProperty("java.io.tmpdir") + "/smartflow-it");
    }

    @LocalServerPort
    int port;
    @Autowired
    ObjectMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();
    private String admin;
    private String employee;

    @BeforeAll
    void waitForSeed() throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(3));
        while (Instant.now().isBefore(deadline)) {
            JsonNode info = get("/api/v1/public/demo", null);
            if (info.path("ready").asBoolean()) break;
            Thread.sleep(1000);
        }
        admin = login("demo.admin@smartflowai.demo");
        employee = login("demo.employee@smartflowai.demo");
    }

    @Test
    void dashboardsAreComputedFromSeededData() throws Exception {
        JsonNode exec = get("/api/v1/analytics/dashboard/executive", admin);
        assertThat(exec.path("kpis").size()).isGreaterThan(10);
        assertThat(get("/api/v1/analytics/dashboard/qhse", admin).path("kpis").size()).isGreaterThan(5);
        assertThat(get("/api/v1/analytics/dashboard/distribution", admin).path("kpis").size()).isGreaterThan(5);
    }

    @Test
    void employeeReportsNonConformityAndCannotApprove() throws Exception {
        JsonNode nc = post("/api/v1/non-conformities", employee, "{\"title\":\"IT test\",\"type\":\"PRODUCT\",\"category\":\"ALLERGEN\"}");
        assertThat(nc.path("severity").asText()).isEqualTo("CRITICAL");
        assertThat(nc.path("status").asText()).isEqualTo("REPORTED");
        HttpResponse<String> denied = send("POST", "/api/v1/workflow/NonConformity/" + nc.path("id").asText() + "/transitions/qualify", employee,
                "{\"fields\":{\"assigneeName\":\"x\",\"dueDate\":\"2030-01-01\"}}");
        assertThat(denied.statusCode()).isEqualTo(403);
    }

    @Test
    void tenantsAreIsolated() throws Exception {
        String other = login("admin@saharanpharma.demo");
        assertThat(get("/api/v1/non-conformities", other).path("totalElements").asInt()).isZero();
        assertThat(get("/api/v1/non-conformities", admin).path("totalElements").asInt()).isPositive();
    }

    @Test
    void routingAndForecastWorkWithoutTheAiService() throws Exception {
        JsonNode wh = get("/api/v1/warehouses?code=WH-ALG-DC", admin).path("content").get(0);
        JsonNode r = post("/api/v1/routing/optimize", admin, "{\"depotWarehouseId\":\"" + wh.path("id").asText() + "\"}");
        assertThat(r.path("solution").path("engine").asText()).contains("built-in");
        assertThat(r.path("solution").path("optimizedDistanceKm").asDouble()).isLessThanOrEqualTo(r.path("solution").path("originalDistanceKm").asDouble());
        JsonNode p = get("/api/v1/products?sku=ATL-PA-101", admin).path("content").get(0);
        JsonNode f = get("/api/v1/ai/forecast/" + p.path("id").asText(), admin);
        assertThat(f.path("forecast").size()).isEqualTo(12);
    }

    @Test
    void assistantAnswersFromData() throws Exception {
        JsonNode a = post("/api/v1/ai/assistant", admin, "{\"question\":\"Which CAPA actions are overdue?\"}");
        assertThat(a.path("intent").asText()).isEqualTo("overdue_capa");
        assertThat(a.path("dataNature").asText()).isEqualTo("MEASURED");
    }

    private String login(String email) throws Exception {
        return post("/api/v1/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"Demo@2026!\"}").path("accessToken").asText();
    }

    private JsonNode get(String path, String token) throws Exception {
        return mapper.readTree(send("GET", path, token, null).body());
    }

    private JsonNode post(String path, String token, String body) throws Exception {
        HttpResponse<String> r = send("POST", path, token, body);
        assertThat(r.statusCode()).as(r.body()).isBetween(200, 299);
        return mapper.readTree(r.body());
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
