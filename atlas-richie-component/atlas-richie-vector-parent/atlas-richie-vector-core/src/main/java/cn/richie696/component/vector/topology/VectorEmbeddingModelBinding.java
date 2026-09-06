/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import cn.richie696.component.vector.model.Modality;
import org.springframework.ai.embedding.EmbeddingModel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Exact store-to-model binding with a credential-free identity fingerprint. */
public record VectorEmbeddingModelBinding(
        String beanName,
        EmbeddingModel model,
        int dimensions,
        EmbeddingNormalization normalization,
        Set<Modality> modalities,
        String fingerprint) {

    public VectorEmbeddingModelBinding {
        if (beanName == null || beanName.isBlank()) {
            throw new IllegalArgumentException("embedding model beanName must not be blank");
        }
        Objects.requireNonNull(model, "embedding model must not be null");
        if (dimensions < 0) {
            throw new IllegalArgumentException("embedding model dimensions must not be negative");
        }
        Objects.requireNonNull(normalization, "embedding normalization must not be null");
        if (modalities == null || modalities.isEmpty() || modalities.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("embedding modalities must not be empty");
        }
        modalities = Set.copyOf(modalities);
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new IllegalArgumentException("embedding model fingerprint must not be blank");
        }
    }

    public static VectorEmbeddingModelBinding of(String beanName, EmbeddingModel model) {
        Objects.requireNonNull(model, "embedding model must not be null");
        int dimensions = Math.max(model.dimensions(), 0);
        return create(beanName, model, dimensions, EmbeddingNormalization.UNSPECIFIED, Set.of(Modality.TEXT));
    }

    public VectorEmbeddingModelBinding withContract(
            EmbeddingNormalization normalization,
            Set<Modality> modalities) {
        return create(beanName, model, dimensions, normalization, modalities);
    }

    private static VectorEmbeddingModelBinding create(
            String beanName,
            EmbeddingModel model,
            int dimensions,
            EmbeddingNormalization normalization,
            Set<Modality> modalities) {
        Set<Modality> copiedModalities = Set.copyOf(modalities);
        String identity = beanName + "|" + model.getClass().getName() + "|" + dimensions + "|"
                + normalization + "|" + new TreeSet<>(copiedModalities);
        return new VectorEmbeddingModelBinding(
                beanName, model, dimensions, normalization, copiedModalities, fingerprint(identity));
    }

    private static String fingerprint(String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    @Override
    public String toString() {
        return "VectorEmbeddingModelBinding[beanName=" + beanName
                + ", model=<redacted>, dimensions=" + dimensions
                + ", normalization=" + normalization
                + ", modalities=" + modalities
                + ", fingerprint=" + fingerprint + "]";
    }
}
