package com.dnd.smartflow.tenant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Configurable, industry-dependent behaviour of a tenant, persisted as JSON. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TenantSettings {
    private List<String> enabledModules = new ArrayList<>();
    /** Business terminology overrides, e.g. {"batch": "Lot", "site": "Plant"}. */
    private Map<String, String> terminology = new LinkedHashMap<>();
    private List<String> riskCategories = new ArrayList<>();
    private List<String> hazardCategories = new ArrayList<>();
    private List<String> qualityCriteria = new ArrayList<>();
    private List<String> ncTypes = new ArrayList<>();
    private List<String> incidentTypes = new ArrayList<>();
    private List<String> environmentalIndicators = new ArrayList<>();
    private List<String> referenceStandards = new ArrayList<>();
    private List<String> operationalUnits = new ArrayList<>();
    /** Emission factors used for carbon accounting (kg CO2e per unit). */
    private Map<String, Double> emissionFactors = new LinkedHashMap<>();
    private String currency = "DZD";
    private boolean publicQrAccess = true;
    private LlmSettings llm = new LlmSettings();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LlmSettings {
        /** NONE, OPENAI, OPENAI_COMPATIBLE, OLLAMA */
        private String provider = "NONE";
        private String baseUrl;
        private String model;
        /** AES-GCM encrypted API key, never returned to clients. */
        private String apiKeyEncrypted;
    }
}
