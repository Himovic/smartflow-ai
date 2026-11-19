package com.dnd.smartflow.qhse.service;

import com.dnd.smartflow.qhse.domain.NonConformity;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic severity rules for non-conformities. The reporter may propose a severity; the rules can
 * only escalate it (food-safety hazards, customer impact, quantities) so that critical issues are never
 * under-classified.
 */
public final class NcRules {
    private static final Set<String> CRITICAL_CATEGORIES = Set.of("FOREIGN_BODY", "CONTAMINATION", "ALLERGEN", "MICROBIOLOGICAL",
            "PACKAGING_INTEGRITY", "LABELLING_ALLERGEN", "STERILITY", "PRODUCT_SAFETY");

    private NcRules() {
    }

    public static String suggestSeverity(NonConformity nc) {
        String category = nc.getCategory() == null ? "" : nc.getCategory().toUpperCase(Locale.ROOT);
        if (CRITICAL_CATEGORIES.contains(category)) return "CRITICAL";
        int qty = nc.getQuantityAffected() == null ? 0 : nc.getQuantityAffected();
        if ("CUSTOMER_COMPLAINT".equals(nc.getType()) && nc.getBatchId() != null) return qty > 5000 ? "CRITICAL" : "MAJOR";
        if (qty > 10000) return "CRITICAL";
        if (qty > 1000) return "MAJOR";
        return "MINOR";
    }

    public static String escalate(String proposed, String suggested) {
        return rank(suggested) > rank(proposed) ? suggested : proposed;
    }

    public static int rank(String severity) {
        if (severity == null) return 0;
        return switch (severity) {
            case "CRITICAL" -> 3;
            case "MAJOR" -> 2;
            case "MINOR" -> 1;
            default -> 0;
        };
    }

    public static LocalDate defaultDueDate(String severity, LocalDate from) {
        return from.plusDays(switch (severity) {
            case "CRITICAL" -> 7;
            case "MAJOR" -> 15;
            default -> 30;
        });
    }
}
