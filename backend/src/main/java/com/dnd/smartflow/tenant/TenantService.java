package com.dnd.smartflow.tenant;

import com.dnd.smartflow.audit.AuditService;
import com.dnd.smartflow.common.security.CredentialCipher;
import com.dnd.smartflow.common.security.Modules;
import com.dnd.smartflow.common.tenant.TenantContext;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.qhse.domain.InspectionTemplate;
import com.dnd.smartflow.qhse.domain.InspectionTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TenantService {
    private final TenantRepository tenants;
    private final IndustryProfileCatalog catalog;
    private final InspectionTemplateRepository templates;
    private final CredentialCipher cipher;
    private final AuditService audit;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    @Cacheable(value = "tenantModules", key = "#tenantId")
    @Transactional(readOnly = true)
    public Set<String> enabledModules(UUID tenantId) {
        return tenants.findById(tenantId)
                .map(t -> (Set<String>) new LinkedHashSet<>(t.getSettings().getEnabledModules()))
                .orElse(Set.of());
    }

    @Transactional(readOnly = true)
    public Tenant current() {
        return tenants.findById(TenantContext.require()).orElseThrow(() -> ApiException.notFound("Tenant"));
    }

    @Transactional(readOnly = true)
    public Tenant get(UUID id) {
        return tenants.findById(id).orElseThrow(() -> ApiException.notFound("Tenant"));
    }

    @CacheEvict(value = "tenantModules", allEntries = true)
    @Transactional
    public Tenant updateCurrent(TenantConfigRequest req) {
        Tenant t = current();
        if (req.name() != null && !req.name().isBlank()) t.setName(req.name());
        if (req.defaultLanguage() != null) t.setDefaultLanguage(req.defaultLanguage());
        if (req.timezone() != null) t.setTimezone(req.timezone());
        if (req.settings() != null) {
            TenantSettings incoming = req.settings();
            List<String> modules = incoming.getEnabledModules().stream().filter(Modules.OPTIONAL::contains).distinct().toList();
            incoming.setEnabledModules(new ArrayList<>(modules));
            // Never let clients overwrite the encrypted API key with a masked value.
            String existingKey = t.getSettings().getLlm() != null ? t.getSettings().getLlm().getApiKeyEncrypted() : null;
            if (incoming.getLlm() == null) incoming.setLlm(new TenantSettings.LlmSettings());
            incoming.getLlm().setApiKeyEncrypted(existingKey);
            t.setSettings(incoming);
        }
        if (req.llmApiKey() != null) {
            t.getSettings().getLlm().setApiKeyEncrypted(req.llmApiKey().isBlank() ? null : cipher.encrypt(req.llmApiKey()));
        }
        audit.log("TENANT_CONFIG_UPDATED", "Tenant", t.getId(), Map.of("name", t.getName()));
        return tenants.save(t);
    }

    /**
     * Applies an industry template: settings and inspection templates. Existing business data is untouched.
     * Not transactional on purpose: the Hibernate session binds its tenant when it opens, so the template
     * installation runs in its own transaction once the target tenant is bound.
     */
    @CacheEvict(value = "tenantModules", allEntries = true)
    public Tenant applyIndustryProfile(UUID tenantId, String profileCode) {
        Tenant updated = tx.execute(s -> {
            Tenant t = get(tenantId);
            TenantSettings fresh = catalog.settingsFor(profileCode);
            fresh.setLlm(t.getSettings().getLlm());
            t.setIndustryProfile(profileCode);
            t.setSettings(fresh);
            return tenants.save(t);
        });
        TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(s -> {
            installTemplates(profileCode);
            audit.log("INDUSTRY_PROFILE_APPLIED", "Tenant", tenantId, Map.of("profile", profileCode));
        }));
        return updated;
    }

    @SuppressWarnings("unchecked")
    public int installTemplates(String profileCode) {
        Set<String> existing = new HashSet<>();
        templates.findAll().forEach(x -> existing.add(x.getCode()));
        int created = 0;
        for (String code : catalog.templateCodesFor(profileCode)) {
            if (existing.contains(code)) continue;
            Map<String, Object> def = catalog.template(code).orElse(null);
            if (def == null) continue;
            InspectionTemplate it = new InspectionTemplate();
            it.setCode(code);
            it.setName((String) def.get("name"));
            it.setDomain((String) def.get("domain"));
            it.setInspectionType((String) def.get("inspectionType"));
            it.setIndustryProfile(profileCode);
            it.setDescription((String) def.get("description"));
            it.setPassingScore(((Number) def.getOrDefault("passingScore", 80)).doubleValue());
            it.setQuestions((List<Map<String, Object>>) def.get("questions"));
            it.setTemplateVersion(1);
            it.setActive(true);
            templates.save(it);
            created++;
        }
        return created;
    }

    public String decryptLlmKey(Tenant t) {
        return t.getSettings().getLlm() == null ? null : cipher.decrypt(t.getSettings().getLlm().getApiKeyEncrypted());
    }

    public record TenantConfigRequest(String name, String defaultLanguage, String timezone, TenantSettings settings, String llmApiKey) {
    }
}
