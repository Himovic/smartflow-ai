package com.dnd.smartflow.common.crud;

import com.dnd.smartflow.common.security.CurrentUser;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Builds JPA specifications from query parameters: {@code attr=value} (comma separated for IN),
 * {@code attr_from}/{@code attr_to} ranges, {@code attr_null=true|false} and free-text {@code q}.
 * Unknown parameters are ignored, so parameters can never inject arbitrary JPQL.
 */
public final class SpecificationFactory {
    static final Set<String> RESERVED = Set.of("page", "size", "sort", "q", "format", "access_token");
    static final List<String> SEARCHABLE = List.of("code", "name", "title", "reference", "sku", "batchNumber", "plateNumber",
            "firstName", "lastName", "matricule", "orderNumber", "deliveryNumber", "email", "city", "category", "wasteType",
            "area", "description", "hazard");

    private SpecificationFactory() {
    }

    public static <E> Specification<E> fromParams(Metamodel mm, Class<E> type, Map<String, String> params) {
        EntityType<E> et = mm.entity(type);
        Map<String, Class<?>> attrs = new HashMap<>();
        for (Attribute<? super E, ?> a : et.getAttributes()) attrs.put(a.getName(), a.getJavaType());

        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            params.forEach((key, raw) -> {
                if (raw == null || raw.isBlank() || RESERVED.contains(key)) return;
                String attr = key;
                String op = "eq";
                if (key.endsWith("_from")) { attr = key.substring(0, key.length() - 5); op = "from"; }
                else if (key.endsWith("_to")) { attr = key.substring(0, key.length() - 3); op = "to"; }
                else if (key.endsWith("_null")) { attr = key.substring(0, key.length() - 5); op = "null"; }
                Class<?> jt = attrs.get(attr);
                if (jt == null || attr.equals("tenantId")) return;
                Path<Object> path = root.get(attr);
                switch (op) {
                    case "null" -> ps.add(Boolean.parseBoolean(raw) ? cb.isNull(path) : cb.isNotNull(path));
                    case "from" -> ps.add(range(cb, path, jt, raw, true));
                    case "to" -> ps.add(range(cb, path, jt, raw, false));
                    default -> {
                        String[] values = raw.split(",");
                        if (values.length == 1) ps.add(cb.equal(path, convert(jt, values[0].trim())));
                        else {
                            CriteriaBuilder.In<Object> in = cb.in(path);
                            for (String v : values) in.value(convert(jt, v.trim()));
                            ps.add(in);
                        }
                    }
                }
            });
            String q = params.get("q");
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                List<Predicate> any = new ArrayList<>();
                for (String s : SEARCHABLE) {
                    if (attrs.get(s) == String.class) any.add(cb.like(cb.lower(root.get(s)), like, '\\'));
                }
                if (!any.isEmpty()) ps.add(cb.or(any.toArray(Predicate[]::new)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    /** Restricts site-bound records to the sites the user is allowed to see. */
    public static <E> Specification<E> siteScope(Metamodel mm, Class<E> type) {
        boolean hasSite = mm.entity(type).getAttributes().stream().anyMatch(a -> a.getName().equals("siteId"));
        return (root, query, cb) -> {
            Optional<CurrentUser> u = CurrentUser.find();
            if (!hasSite || u.isEmpty() || u.get().allSites()) return cb.conjunction();
            List<UUID> sites = u.get().siteIds();
            if (sites.isEmpty()) return cb.isNull(root.get("siteId"));
            return cb.or(cb.isNull(root.get("siteId")), root.get("siteId").in(sites));
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate range(CriteriaBuilder cb, Path<Object> path, Class<?> jt, String raw, boolean from) {
        Comparable value = (Comparable) convert(jt, raw);
        Expression<Comparable> e = (Expression) path;
        return from ? cb.greaterThanOrEqualTo(e, value) : cb.lessThanOrEqualTo(e, value);
    }

    static Object convert(Class<?> jt, String v) {
        try {
            if (jt == String.class) return v;
            if (jt == UUID.class) return UUID.fromString(v);
            if (jt == Integer.class || jt == int.class) return Integer.valueOf(v);
            if (jt == Long.class || jt == long.class) return Long.valueOf(v);
            if (jt == Double.class || jt == double.class) return Double.valueOf(v);
            if (jt == BigDecimal.class) return new BigDecimal(v);
            if (jt == Boolean.class || jt == boolean.class) return Boolean.valueOf(v);
            if (jt == LocalDate.class) return LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v);
            if (jt == Instant.class) return v.length() == 10 ? LocalDate.parse(v).atStartOfDay().toInstant(java.time.ZoneOffset.UTC) : Instant.parse(v);
            if (jt.isEnum()) return Enum.valueOf((Class<Enum>) jt.asSubclass(Enum.class), v);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid filter value '" + v + "'");
        }
        throw new IllegalArgumentException("Unsupported filter type");
    }
}
