/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.vector.model;

import java.util.Locale;
import java.util.Map;

/** Optional Store-level hybrid configuration. Providers map its neutral fields to their SDK schema. */
public record HybridStoreOptions(
        boolean enabled,
        String denseField,
        String sparseField,
        String vectorizerBeanName,
        int candidateLimit,
        FallbackMode fallbackMode) {

    public HybridStoreOptions {
        denseField = required(denseField, "denseField");
        sparseField = required(sparseField, "sparseField");
        if (denseField.equals(sparseField)) {
            throw new IllegalArgumentException("denseField and sparseField must be different");
        }
        // A provider can use an engine-native lexical index (for example RediSearch
        // full text) as its sparse recall side.  Therefore a vectorizer is optional
        // at the shared contract level; providers with a physical sparse-vector
        // field validate it explicitly when they create their schema.
        vectorizerBeanName = optional(vectorizerBeanName);
        if (candidateLimit <= 0) throw new IllegalArgumentException("candidateLimit must be greater than zero");
        fallbackMode = fallbackMode == null ? FallbackMode.REJECT : fallbackMode;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public enum FallbackMode { REJECT, CORE_RRF }

    /**
     * Reads the provider-neutral hybrid declaration from an index's additional fields.
     * Providers remain responsible for validating the fields they can actually map to
     * their physical schema.
     */
    public static HybridStoreOptions fromAdditionalFields(Map<String, Object> fields) {
        Map<String, Object> values = fields == null ? Map.of() : fields;
        boolean enabled = booleanValue(values.get("hybrid-enabled"), false, "hybrid-enabled");
        return new HybridStoreOptions(
                enabled,
                text(values.get("dense-field"), "vector"),
                text(values.get("sparse-field"), "sparse_vector"),
                optionalText(values.get("sparse-vectorizer")),
                integer(values.get("hybrid-candidate-limit"), 50, "hybrid-candidate-limit"),
                fallback(values.get("hybrid-fallback-mode")));
    }

    private static String text(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static String optionalText(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean booleanValue(Object value, boolean fallback, String name) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text)) return false;
        throw new IllegalArgumentException(name + " must be true or false");
    }

    private static int integer(Object value, int fallback, String name) {
        if (value == null) return fallback;
        try {
            return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }

    private static FallbackMode fallback(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return FallbackMode.REJECT;
        try {
            return FallbackMode.valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("hybrid-fallback-mode must be REJECT or CORE_RRF", exception);
        }
    }
}
