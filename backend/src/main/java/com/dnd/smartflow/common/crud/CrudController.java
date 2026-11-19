package com.dnd.smartflow.common.crud;

import com.dnd.smartflow.common.persistence.TenantEntity;
import com.dnd.smartflow.common.persistence.TenantRepository;
import com.dnd.smartflow.common.security.CurrentUser;
import com.dnd.smartflow.common.web.ApiException;
import com.dnd.smartflow.common.web.PageResponse;
import com.dnd.smartflow.reporting.ReportExporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Generic REST resource for a tenant-owned aggregate: paginated/filtered listing, lookup, read, create,
 * update, delete and Excel/PDF export. Enforces module/action permissions, site-level data scope, bean
 * validation, mass-assignment protection and audit logging. Subclasses add business rules through hooks.
 */
public abstract class CrudController<E extends TenantEntity> {
    protected final CrudSupport support;
    protected final TenantRepository<E> repository;
    protected final Class<E> type;
    protected final String module;
    private final BiConsumer<E, E> copy;

    @SuppressWarnings("unchecked")
    protected CrudController(CrudSupport support, TenantRepository<E> repository, Class<E> type, String module) {
        this.support = support;
        this.repository = repository;
        this.type = type;
        this.module = module;
        try {
            Method m = EntityCopier.class.getMethod("copy", type, type);
            this.copy = (src, dst) -> {
                try {
                    m.invoke(support.getCopier(), src, dst);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No EntityCopier.copy for " + type.getSimpleName(), e);
        }
    }

    // ---------------------------------------------------------------- hooks
    /** Reference prefix (e.g. "NC") used when the entity has an empty {@code reference}. */
    protected String referencePrefix() {
        return null;
    }

    protected Sort defaultSort() {
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    protected Specification<E> extraScope() {
        return null;
    }

    protected void beforeCreate(E entity) {
    }

    protected void beforeSave(E entity) {
    }

    protected void afterCreate(E entity) {
    }

    protected void afterUpdate(E before, E entity) {
    }

    protected void beforeDelete(E entity) {
    }

    protected String label(E e) {
        Map<String, Object> m = support.getObjectMapper().convertValue(e, Map.class);
        for (String k : List.of("reference", "code", "sku", "batchNumber", "plateNumber", "orderNumber", "deliveryNumber")) {
            Object v = m.get(k);
            if (v != null) {
                Object n = m.getOrDefault("name", m.get("title"));
                return n != null ? v + " — " + n : String.valueOf(v);
            }
        }
        for (String k : List.of("name", "title", "area")) if (m.get(k) != null) return String.valueOf(m.get(k));
        if (m.get("firstName") != null) return m.get("firstName") + " " + m.get("lastName");
        return String.valueOf(e.getId());
    }

    // ---------------------------------------------------------------- endpoints
    @GetMapping
    @Operation(summary = "List with pagination, sorting (sort=field,asc), free-text search (q) and attribute filters")
    public PageResponse<E> list(@Parameter(hidden = true) @RequestParam Map<String, String> params) {
        support.getPermissions().require(module, "read");
        int page = Integer.parseInt(params.getOrDefault("page", "0"));
        int size = Math.min(500, Integer.parseInt(params.getOrDefault("size", "20")));
        Page<E> result = repository.findAll(spec(params), PageRequest.of(page, size, sort(params.get("sort"))));
        return PageResponse.of(result);
    }

    @GetMapping("/lookup")
    @Operation(summary = "Lightweight id/label list for selectors")
    public List<Map<String, Object>> lookup(@Parameter(hidden = true) @RequestParam Map<String, String> params) {
        support.getPermissions().require(module, "read");
        return repository.findAll(spec(params), PageRequest.of(0, 2000, defaultSort())).stream()
                .map(e -> Map.<String, Object>of("id", e.getId(), "label", label(e))).toList();
    }

    @GetMapping("/{id}")
    public E get(@PathVariable UUID id) {
        support.getPermissions().require(module, "read");
        return load(id);
    }

    @PostMapping
    public E create(@Valid @RequestBody E body) {
        support.getPermissions().require(module, "write");
        return support.getTx().execute(s -> {
            E entity = newInstance();
            copy.accept(body, entity);
            checkSite(entity);
            fillReference(entity);
            beforeCreate(entity);
            beforeSave(entity);
            E saved = repository.save(entity);
            afterCreate(saved);
            support.getAudit().log("CREATE", type.getSimpleName(), saved.getId(), Map.of("label", label(saved)));
            return saved;
        });
    }

    @PutMapping("/{id}")
    public E update(@PathVariable UUID id, @Valid @RequestBody E body) {
        support.getPermissions().require(module, "write");
        return support.getTx().execute(s -> {
            E entity = load(id);
            E before = snapshot(entity);
            if (body.getVersion() != null && entity.getVersion() != null && !body.getVersion().equals(entity.getVersion())) {
                throw ApiException.conflict("CONCURRENT_MODIFICATION", "The record was modified by someone else. Reload and retry.");
            }
            copy.accept(body, entity);
            checkSite(entity);
            beforeSave(entity);
            E saved = repository.save(entity);
            afterUpdate(before, saved);
            support.getAudit().log("UPDATE", type.getSimpleName(), saved.getId(), diff(before, saved));
            return saved;
        });
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable UUID id) {
        support.getPermissions().require(module, "delete");
        support.getTx().executeWithoutResult(s -> {
            E entity = load(id);
            beforeDelete(entity);
            repository.delete(entity);
            support.getAudit().log("DELETE", type.getSimpleName(), id, Map.of("label", label(entity)));
        });
        return Map.of("deleted", id);
    }

    @GetMapping("/export")
    @Operation(summary = "Export the filtered list (format=xlsx|pdf)")
    public ResponseEntity<byte[]> export(@Parameter(hidden = true) @RequestParam Map<String, String> params) {
        support.getPermissions().require(module, "read");
        List<E> rows = repository.findAll(spec(params), PageRequest.of(0, 5000, sort(params.get("sort")))).getContent();
        List<Map<String, Object>> maps = new ArrayList<>();
        for (E e : rows) maps.add(flatten(e));
        List<ReportExporter.Column> cols = exportColumns(maps);
        support.getAudit().log("EXPORT", type.getSimpleName(), null, Map.of("rows", rows.size(), "format", params.getOrDefault("format", "xlsx")));
        return support.getExporter().export(params.getOrDefault("format", "xlsx"), type.getSimpleName() + " export", cols, maps);
    }

    // ---------------------------------------------------------------- helpers
    protected E load(UUID id) {
        E e = repository.findById(id).orElseThrow(() -> ApiException.notFound(type.getSimpleName()));
        Map<String, Object> m = flatten(e);
        Object site = m.get("siteId");
        if (site != null && !CurrentUser.require().canAccessSite(UUID.fromString(site.toString()))) {
            throw ApiException.notFound(type.getSimpleName());
        }
        return e;
    }

    protected Specification<E> spec(Map<String, String> params) {
        Specification<E> s = SpecificationFactory.fromParams(support.getEntityManager().getMetamodel(), type, params)
                .and(SpecificationFactory.siteScope(support.getEntityManager().getMetamodel(), type));
        Specification<E> extra = extraScope();
        return extra == null ? s : s.and(extra);
    }

    private Sort sort(String raw) {
        if (raw == null || raw.isBlank()) return defaultSort();
        String[] p = raw.split(",");
        boolean known = support.getEntityManager().getMetamodel().entity(type).getAttributes().stream()
                .anyMatch(a -> a.getName().equals(p[0]));
        if (!known) return defaultSort();
        return Sort.by(p.length > 1 && p[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, p[0]);
    }

    private void checkSite(E entity) {
        Object site = flatten(entity).get("siteId");
        if (site != null && !CurrentUser.require().canAccessSite(UUID.fromString(site.toString()))) {
            throw ApiException.forbidden("You cannot create or modify records for this site");
        }
    }

    private void fillReference(E entity) {
        if (referencePrefix() == null) return;
        try {
            Method getter = type.getMethod("getReference");
            if (getter.invoke(entity) == null || String.valueOf(getter.invoke(entity)).isBlank()) {
                type.getMethod("setReference", String.class).invoke(entity, support.getReferences().next(referencePrefix()));
            }
        } catch (NoSuchMethodException ignored) {
            // entity has no reference attribute
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected E newInstance() {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private E snapshot(E entity) {
        E copyOf = newInstance();
        copy.accept(entity, copyOf);
        return copyOf;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> flatten(Object e) {
        return support.getObjectMapper().convertValue(e, Map.class);
    }

    private Map<String, Object> diff(E before, E after) {
        Map<String, Object> a = flatten(before);
        Map<String, Object> b = flatten(after);
        Map<String, Object> changes = new LinkedHashMap<>();
        b.forEach((k, v) -> {
            if (List.of("id", "createdAt", "updatedAt", "createdBy", "updatedBy", "version").contains(k)) return;
            if (!Objects.equals(a.get(k), v)) {
                Map<String, Object> c = new HashMap<>();
                c.put("from", a.get(k));
                c.put("to", v);
                changes.put(k, c);
            }
        });
        return changes;
    }

    private List<ReportExporter.Column> exportColumns(List<Map<String, Object>> rows) {
        Set<String> keys = new LinkedHashSet<>();
        if (!rows.isEmpty()) {
            rows.get(0).forEach((k, v) -> {
                if (v instanceof Map || v instanceof List || k.equals("version") || k.equals("signature")) return;
                keys.add(k);
            });
        } else {
            keys.add("id");
        }
        return keys.stream().map(k -> new ReportExporter.Column(k, humanize(k))).toList();
    }

    static String humanize(String key) {
        String s = key.replaceAll("([A-Z])", " $1").replace(" Id", "").trim();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
