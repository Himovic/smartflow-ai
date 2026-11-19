package com.dnd.smartflow.tenant;

import java.util.LinkedHashMap;
import java.util.Map;

/** Client-safe representation of a tenant (secrets are reduced to flags). */
public final class TenantControllerViews {
    private TenantControllerViews() {
    }

    public static Map<String, Object> view(Tenant t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("code", t.getCode());
        m.put("name", t.getName());
        m.put("industryProfile", t.getIndustryProfile());
        m.put("country", t.getCountry());
        m.put("timezone", t.getTimezone());
        m.put("defaultLanguage", t.getDefaultLanguage());
        m.put("demo", t.isDemo());
        TenantSettings s = t.getSettings();
        Map<String, Object> llm = new LinkedHashMap<>();
        llm.put("provider", s.getLlm().getProvider());
        llm.put("baseUrl", s.getLlm().getBaseUrl());
        llm.put("model", s.getLlm().getModel());
        llm.put("apiKeyConfigured", s.getLlm().getApiKeyEncrypted() != null);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("enabledModules", s.getEnabledModules());
        settings.put("terminology", s.getTerminology());
        settings.put("riskCategories", s.getRiskCategories());
        settings.put("hazardCategories", s.getHazardCategories());
        settings.put("qualityCriteria", s.getQualityCriteria());
        settings.put("ncTypes", s.getNcTypes());
        settings.put("incidentTypes", s.getIncidentTypes());
        settings.put("environmentalIndicators", s.getEnvironmentalIndicators());
        settings.put("referenceStandards", s.getReferenceStandards());
        settings.put("operationalUnits", s.getOperationalUnits());
        settings.put("emissionFactors", s.getEmissionFactors());
        settings.put("currency", s.getCurrency());
        settings.put("publicQrAccess", s.isPublicQrAccess());
        settings.put("llm", llm);
        m.put("settings", settings);
        return m;
    }
}
