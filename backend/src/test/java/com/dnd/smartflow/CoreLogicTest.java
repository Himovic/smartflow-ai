package com.dnd.smartflow;

import com.dnd.smartflow.ai.forecast.TimeSeriesForecaster;
import com.dnd.smartflow.ai.routing.HeuristicRouteSolver;
import com.dnd.smartflow.ai.routing.RoutingModels.*;
import com.dnd.smartflow.common.security.CredentialCipher;
import com.dnd.smartflow.common.security.Role;
import com.dnd.smartflow.fleet.service.EmissionCalculator;
import com.dnd.smartflow.qhse.domain.NonConformity;
import com.dnd.smartflow.qhse.service.ChecklistScorer;
import com.dnd.smartflow.qhse.service.NcRules;
import com.dnd.smartflow.qhse.service.RiskMatrix;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class CoreLogicTest {

    @Nested
    class Risk {
        @Test
        void scoresAndLevelsFollowTheFiveByFiveMatrix() {
            assertThat(RiskMatrix.score(5, 5)).isEqualTo(25);
            assertThat(RiskMatrix.level(4)).isEqualTo("LOW");
            assertThat(RiskMatrix.level(9)).isEqualTo("MEDIUM");
            assertThat(RiskMatrix.level(12)).isEqualTo("HIGH");
            assertThat(RiskMatrix.level(15)).isEqualTo("CRITICAL");
            assertThatThrownBy(() -> RiskMatrix.score(6, 1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class NonConformitySeverity {
        @Test
        void foodSafetyHazardsAreAlwaysCritical() {
            NonConformity nc = new NonConformity();
            nc.setCategory("FOREIGN_BODY");
            assertThat(NcRules.suggestSeverity(nc)).isEqualTo("CRITICAL");
            assertThat(NcRules.escalate("MINOR", "CRITICAL")).isEqualTo("CRITICAL");
            assertThat(NcRules.escalate("MAJOR", "MINOR")).isEqualTo("MAJOR");
        }

        @Test
        void customerComplaintOnBatchIsAtLeastMajor() {
            NonConformity nc = new NonConformity();
            nc.setType("CUSTOMER_COMPLAINT");
            nc.setBatchId(UUID.randomUUID());
            assertThat(NcRules.suggestSeverity(nc)).isEqualTo("MAJOR");
        }
    }

    @Nested
    class Checklist {
        List<Map<String, Object>> questions = List.of(
                q("q1", "YES_NO", false, null), q("q2", "PASS_FAIL", true, null),
                new HashMap<>(Map.of("id", "q3", "type", "NUMERIC", "min", 0, "max", 25, "weight", 2, "createNc", true)),
                new HashMap<>(Map.of("id", "q4", "type", "TEXT", "required", true, "condition", Map.of("questionId", "q1", "equals", "NO"))));

        static Map<String, Object> q(String id, String type, boolean critical, Object expected) {
            Map<String, Object> m = new HashMap<>(Map.of("id", id, "type", type, "text", id, "critical", critical, "required", true));
            if (expected != null) m.put("expected", expected);
            return m;
        }

        @Test
        void allPassingAnswersGiveFullScore() {
            var ev = ChecklistScorer.evaluate(questions, List.of(a("q1", "YES"), a("q2", "PASS"), a("q3", 18)), 85);
            assertThat(ev.score()).isEqualTo(100.0);
            assertThat(ev.result()).isEqualTo("PASS");
            assertThat(ev.missing()).isEmpty();
        }

        @Test
        void criticalFailureFailsTheInspectionAndConditionalQuestionBecomesRequired() {
            var ev = ChecklistScorer.evaluate(questions, List.of(a("q1", "NO"), a("q2", "FAIL"), a("q3", 30)), 85);
            assertThat(ev.result()).isEqualTo("FAIL");
            assertThat(ev.failures()).extracting(ChecklistScorer.Failure::questionId).containsExactlyInAnyOrder("q1", "q2", "q3");
            assertThat(ev.failures()).filteredOn(ChecklistScorer.Failure::createNc).hasSize(2); // critical q2 + createNc q3
            assertThat(ev.missing()).containsExactly("null"); // q4 has no text, conditional and unanswered
        }

        @Test
        void notApplicableAnswersAreExcludedFromScore() {
            var ev = ChecklistScorer.evaluate(questions, List.of(a("q1", "N/A"), a("q2", "PASS"), a("q3", 10)), 85);
            assertThat(ev.score()).isEqualTo(100.0);
        }

        static Map<String, Object> a(String id, Object v) {
            return Map.of("questionId", id, "value", v);
        }
    }

    @Nested
    class Routing {
        @Test
        void optimizedPlanRespectsCapacityAndIsNotLongerThanManualPlan() {
            Random r = new Random(7);
            List<Stop> stops = new ArrayList<>();
            String[] towns = {"Blida", "Alger", "Boumerdès", "Tipaza", "Médéa"};
            for (int i = 0; i < 25; i++) {
                stops.add(new Stop("s" + i, "C" + i + " (" + towns[r.nextInt(towns.length)] + ")", 36.4 + r.nextDouble() * 0.5,
                        2.7 + r.nextDouble() * 1.0, 300 + r.nextInt(900), 1 + r.nextInt(3), null, null, 15));
            }
            List<VehicleSpec> fleet = List.of(new VehicleSpec("v1", "T1", 7500, 24, 2.68), new VehicleSpec("v2", "T2", 7500, 24, 2.68),
                    new VehicleSpec("v3", "V3", 1400, 11.5, 2.68));
            Problem p = new Problem(new Location(36.71, 3.17), stops, fleet, 55, 1.3, 11 * 60);
            Solution s = new HeuristicRouteSolver().solve(p);
            assertThat(s.routes()).allSatisfy(route -> assertThat(route.loadKg()).isLessThanOrEqualTo(route.capacityKg()));
            int served = s.routes().stream().mapToInt(route -> route.stops().size()).sum();
            assertThat(served + s.unassignedStopIds().size()).isEqualTo(25);
            assertThat(s.optimizedDistanceKm()).isLessThanOrEqualTo(s.originalDistanceKm());
            assertThat(s.estimatedCo2Kg()).isPositive();
        }

        @Test
        void haversineDistanceAlgiersToOranIsAbout350Km() {
            assertThat(HeuristicRouteSolver.haversineKm(36.7538, 3.0588, 35.6971, -0.6308)).isBetween(340.0, 360.0);
        }

        @Test
        void emissionsUseVehicleFactors() {
            double liters = EmissionCalculator.fuelLiters(100, 25.0);
            assertThat(liters).isEqualTo(25.0);
            assertThat(EmissionCalculator.co2Kg(liters, null, "DIESEL")).isEqualTo(67.0);
        }
    }

    @Nested
    class Forecasting {
        @Test
        void seasonalSeriesIsForecastWithMeasuredHoldoutError() {
            double[] y = new double[120];
            for (int t = 0; t < y.length; t++) y[t] = 200 + t * 0.8 + 40 * Math.sin(2 * Math.PI * t / 52.0);
            var f = TimeSeriesForecaster.forecast(y, 8, 52);
            assertThat(f.model()).contains("Holt-Winters");
            assertThat(f.forecast()).hasSize(8);
            assertThat(f.mape()).isLessThan(10.0);
            for (int h = 0; h < 8; h++) assertThat(f.lower()[h]).isLessThanOrEqualTo(f.forecast()[h]);
        }

        @Test
        void shortSeriesFallsBackToTrendModel() {
            double[] y = {10, 12, 13, 15, 16, 18, 19, 21, 22, 24};
            var f = TimeSeriesForecaster.forecast(y, 4, 52);
            assertThat(f.model()).contains("Holt linear");
            assertThat(f.forecast()[0]).isGreaterThan(22);
        }
    }

    @Nested
    class Security {
        @Test
        void rolesGrantExpectedPermissions() {
            assertThat(Role.EMPLOYEE.allows("nc", "write")).isTrue();
            assertThat(Role.EMPLOYEE.allows("nc", "approve")).isFalse();
            assertThat(Role.EMPLOYEE.allows("users", "read")).isFalse();
            assertThat(Role.VIEWER.allows("quality", "write")).isFalse();
            assertThat(Role.QHSE_DIRECTOR.allows("capa", "approve")).isTrue();
            assertThat(Role.TENANT_ADMIN.allows("platform", "admin")).isFalse();
            assertThat(Role.SUPER_ADMIN.allows("platform", "admin")).isTrue();
            assertThat(Role.QUALITY_MANAGER.allows("integrations", "admin")).isFalse();
        }

        @Test
        void credentialsAreEncryptedWithRandomIv() {
            CredentialCipher c = new CredentialCipher("test-key");
            String a = c.encrypt("secret"), b = c.encrypt("secret");
            assertThat(a).isNotEqualTo(b);
            assertThat(c.decrypt(a)).isEqualTo("secret");
            assertThatThrownBy(() -> new CredentialCipher("other-key").decrypt(a)).isInstanceOf(IllegalStateException.class);
        }
    }
}
