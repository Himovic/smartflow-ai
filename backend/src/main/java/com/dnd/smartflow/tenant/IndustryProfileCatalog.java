package com.dnd.smartflow.tenant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

/**
 * Loads configurable industry profiles (modules, terminology, risk categories, inspection templates…)
 * from {@code config/industry-profiles.json}. Profiles are templates: applying one to a tenant copies the
 * values into the tenant configuration where they can then be adjusted independently.
 */
@Component
@RequiredArgsConstructor
public class IndustryProfileCatalog {
    private final ObjectMapper mapper;
    private Map<String, Map<String, Object>> profiles = new LinkedHashMap<>();
    private Map<String, Map<String, Object>> templates = new LinkedHashMap<>();

    @PostConstruct
    @SuppressWarnings("unchecked")
    void load() throws Exception {
        try (InputStream in = new ClassPathResource("config/industry-profiles.json").getInputStream()) {
            Map<String, Object> root = mapper.readValue(in, new TypeReference<>() {});
            for (Map<String, Object> p : (List<Map<String, Object>>) root.get("profiles")) {
                profiles.put((String) p.get("code"), p);
            }
            for (Map<String, Object> t : (List<Map<String, Object>>) root.get("inspectionTemplates")) {
                templates.put((String) t.get("code"), t);
            }
        }
    }

    public Collection<Map<String, Object>> profiles() {
        return profiles.values();
    }

    public Optional<Map<String, Object>> profile(String code) {
        return Optional.ofNullable(profiles.get(code));
    }

    public Optional<Map<String, Object>> template(String code) {
        return Optional.ofNullable(templates.get(code));
    }

    @SuppressWarnings("unchecked")
    public TenantSettings settingsFor(String code) {
        Map<String, Object> p = profiles.get(code);
        if (p == null) throw new IllegalArgumentException("Unknown industry profile " + code);
        Map<String, Object> s = (Map<String, Object>) p.get("settings");
        return mapper.convertValue(s, TenantSettings.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> templateCodesFor(String code) {
        return (List<String>) profiles.getOrDefault(code, Map.of()).getOrDefault("inspectionTemplates", List.of());
    }
}
