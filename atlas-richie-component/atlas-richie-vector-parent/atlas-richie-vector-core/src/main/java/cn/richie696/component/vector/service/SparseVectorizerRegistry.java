/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.vector.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable lookup of application-provided sparse encoders.
 *
 * <p>The registry keeps Spring bean discovery at the component boundary. Provider adapters
 * receive this small, vendor-neutral collaborator instead of reaching into an application
 * context. A dense-only Store never resolves an encoder.</p>
 */
public final class SparseVectorizerRegistry {

    private final Map<String, SparseVectorizer> vectorizers;

    public SparseVectorizerRegistry(Map<String, SparseVectorizer> vectorizers) {
        Map<String, SparseVectorizer> copy = new LinkedHashMap<>();
        (vectorizers == null ? Map.<String, SparseVectorizer>of() : vectorizers).forEach((name, vectorizer) -> {
            if (name == null || name.isBlank() || vectorizer == null) return;
            copy.put(name, vectorizer);
        });
        this.vectorizers = Map.copyOf(copy);
    }

    public SparseVectorizer require(String beanName) {
        String name = Objects.requireNonNull(beanName, "beanName").trim();
        SparseVectorizer vectorizer = vectorizers.get(name);
        if (vectorizer == null) {
            throw new IllegalArgumentException("No SparseVectorizer is registered with name '" + name + "'");
        }
        return vectorizer;
    }

    public boolean contains(String beanName) {
        return beanName != null && vectorizers.containsKey(beanName.trim());
    }

    public Map<String, SparseVectorizer> asMap() {
        return vectorizers;
    }
}
