package com.dnd.smartflow.qhse.service;

import java.util.*;

/**
 * Scores a digital checklist. Supported question types: YES_NO, PASS_FAIL, NUMERIC (min/max tolerance),
 * TEXT, MULTI_SELECT (optional {@code failOptions}), PHOTO and SIGNATURE. Conditional questions
 * ({@code condition: {questionId, equals}}) are only evaluated when their condition holds. "N/A" answers
 * are excluded from the score. A failed critical question fails the whole inspection.
 */
public final class ChecklistScorer {
    private ChecklistScorer() {
    }

    public record Failure(String questionId, String text, boolean critical, boolean createNc, Object value, String comment) {
    }

    public record Evaluation(double score, String result, List<Failure> failures, List<Map<String, Object>> answers, List<String> missing) {
    }

    @SuppressWarnings("unchecked")
    public static Evaluation evaluate(List<Map<String, Object>> questions, List<Map<String, Object>> rawAnswers, double passingScore) {
        Map<String, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> a : rawAnswers == null ? List.<Map<String, Object>>of() : rawAnswers) {
            byId.put(String.valueOf(a.get("questionId")), a);
        }
        double total = 0, earned = 0;
        boolean criticalFail = false;
        List<Failure> failures = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> q : questions == null ? List.<Map<String, Object>>of() : questions) {
            String id = String.valueOf(q.get("id"));
            Map<String, Object> cond = (Map<String, Object>) q.get("condition");
            if (cond != null) {
                Map<String, Object> dep = byId.get(String.valueOf(cond.get("questionId")));
                Object depValue = dep == null ? null : dep.get("value");
                if (depValue == null || !String.valueOf(depValue).equalsIgnoreCase(String.valueOf(cond.get("equals")))) continue;
            }
            Map<String, Object> a = byId.get(id);
            Object value = a == null ? null : a.get("value");
            boolean blank = value == null || (value instanceof String s && s.isBlank()) || (value instanceof Collection<?> c && c.isEmpty());
            if (blank) {
                if (Boolean.TRUE.equals(q.get("required"))) missing.add(String.valueOf(q.get("text")));
                continue;
            }
            Map<String, Object> ans = new LinkedHashMap<>(a);
            String type = String.valueOf(q.getOrDefault("type", "YES_NO"));
            Boolean passed = passed(type, q, value);
            if ("N/A".equalsIgnoreCase(String.valueOf(value))) passed = null;
            ans.put("passed", passed);
            out.add(ans);
            if (passed == null) continue;
            double w = q.get("weight") instanceof Number n ? n.doubleValue() : 1.0;
            total += w;
            if (passed) earned += w;
            else {
                boolean critical = Boolean.TRUE.equals(q.get("critical"));
                criticalFail |= critical;
                failures.add(new Failure(id, String.valueOf(q.get("text")), critical,
                        critical || Boolean.TRUE.equals(q.get("createNc")), value, a.get("comment") == null ? null : String.valueOf(a.get("comment"))));
            }
        }
        double score = total == 0 ? 100.0 : Math.round(earned / total * 1000.0) / 10.0;
        String result;
        if (criticalFail) result = "FAIL";
        else if (score >= passingScore) result = "PASS";
        else if (score >= passingScore - 15) result = "CONDITIONAL";
        else result = "FAIL";
        return new Evaluation(score, result, failures, out, missing);
    }

    @SuppressWarnings("unchecked")
    static Boolean passed(String type, Map<String, Object> q, Object value) {
        String v = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "YES_NO" -> {
                String expected = String.valueOf(q.getOrDefault("expected", "YES")).toUpperCase(Locale.ROOT);
                if (value instanceof Boolean b) yield b == expected.equals("YES");
                yield v.equals(expected) || (expected.equals("YES") && v.equals("TRUE"));
            }
            case "PASS_FAIL" -> v.equals("PASS") || v.equals("TRUE");
            case "NUMERIC" -> {
                double d;
                try {
                    d = value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value).replace(',', '.'));
                } catch (NumberFormatException e) {
                    yield false;
                }
                boolean ok = true;
                if (q.get("min") instanceof Number min) ok &= d >= min.doubleValue();
                if (q.get("max") instanceof Number max) ok &= d <= max.doubleValue();
                yield (q.get("min") == null && q.get("max") == null) ? null : ok;
            }
            case "MULTI_SELECT" -> {
                List<String> fails = (List<String>) q.get("failOptions");
                if (fails == null || fails.isEmpty()) yield null;
                Collection<?> chosen = value instanceof Collection<?> c ? c : List.of(value);
                yield chosen.stream().map(String::valueOf).noneMatch(fails::contains);
            }
            default -> null; // TEXT, PHOTO, SIGNATURE are evidence, not scored
        };
    }
}
