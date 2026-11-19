package com.dnd.smartflow.qhse.service;

/** 5x5 risk matrix: score = probability × severity, both rated 1..5. */
public final class RiskMatrix {
    private RiskMatrix() {
    }

    public static int score(Integer probability, Integer severity) {
        if (probability == null || severity == null) return 0;
        if (probability < 1 || probability > 5 || severity < 1 || severity > 5) {
            throw new IllegalArgumentException("Probability and severity must be between 1 and 5");
        }
        return probability * severity;
    }

    public static String level(int score) {
        if (score <= 0) return null;
        if (score <= 4) return "LOW";
        if (score <= 9) return "MEDIUM";
        if (score <= 14) return "HIGH";
        return "CRITICAL";
    }
}
