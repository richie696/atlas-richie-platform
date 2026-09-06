/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

import cn.richie696.component.vector.topology.VectorScoreDescriptor;
import cn.richie696.component.vector.topology.VectorScoreSemantics;
import cn.richie696.component.vector.topology.VectorScoreStage;
import cn.richie696.component.vector.topology.VectorScoreThresholdKind;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Applies Core &lt; Provider &lt; Store &lt; request precedence and hard safety limits. */
public final class VectorQueryResolver {

    public static final int MAX_TOP_K = 1_000;
    public static final int MAX_CANDIDATES = 2_000;
    public static final int MAX_RETURN_FIELDS = 64;
    public static final Duration MAX_TIMEOUT = Duration.ofMinutes(2);
    private static final Pattern FIELD = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,127}");

    private final VectorQueryDefaults configured;
    private final VectorQueryDefaults providerDefaults;
    private final VectorQueryDefaults storeDefaults;
    private final VectorScoreSemantics scoreSemantics;

    public VectorQueryResolver(VectorQueryDefaults providerDefaults, VectorQueryDefaults storeDefaults) {
        this(providerDefaults, storeDefaults, null);
    }

    public VectorQueryResolver(
            VectorQueryDefaults providerDefaults,
            VectorQueryDefaults storeDefaults,
            VectorScoreSemantics scoreSemantics) {
        this.providerDefaults = providerDefaults;
        this.storeDefaults = storeDefaults;
        this.configured = VectorQueryDefaults.CORE_SAFE.overlay(providerDefaults).overlay(storeDefaults);
        this.scoreSemantics = scoreSemantics;
        validateDefaults(configured);
    }

    public ResolvedVectorQuery resolve(VectorQueryRequest request) {
        if (request == null) {
            throw new VectorQueryValidationException(VectorQueryErrorCode.INVALID_VALUE, "query request is required");
        }
        int topK = request.topK() == null ? configured.topK() : request.topK();
        double minScore = request.minScore() == null ? configured.minScore() : request.minScore();
        int candidateLimit = request.candidateLimit() == null
                ? Math.max(topK, configured.candidateLimit()) : request.candidateLimit();
        Duration timeout = request.timeout() == null ? configured.timeout() : request.timeout();
        VectorConsistencyPreference consistency = request.consistency() == null
                ? configured.consistency() : request.consistency();
        Set<String> returnFields = request.returnFields().isEmpty()
                ? configured.returnFields() : request.returnFields();
        VectorScoreThresholdKind thresholdKind = resolveThresholdKind(request);
        validate(topK, minScore, candidateLimit, timeout, returnFields, thresholdKind);
        return new ResolvedVectorQuery(
                request.query(), topK, minScore, thresholdKind, request.filter(), returnFields, candidateLimit,
                timeout, consistency, request.diversification(), request.providerOptions());
    }

    public VectorQueryDefaults configured() {
        return configured;
    }

    /** Returns the winning configuration layer for a scalar option. */
    public VectorParameterSource sourceFor(String option, boolean requestOverride) {
        if (requestOverride) return VectorParameterSource.REQUEST;
        if (hasValue(storeDefaults, option)) return VectorParameterSource.STORE_DEFAULT;
        if (hasValue(providerDefaults, option)) return VectorParameterSource.PROVIDER_DEFAULT;
        return VectorParameterSource.CORE_DEFAULT;
    }

    private VectorScoreThresholdKind resolveThresholdKind(VectorQueryRequest request) {
        if (request.thresholdKind() != null) {
            return request.thresholdKind();
        }
        if (scoreSemantics == null) {
            return VectorScoreThresholdKind.NORMALIZED_RELEVANCE;
        }
        Optional<VectorScoreDescriptor> descriptor = scoreSemantics.descriptor(VectorScoreStage.FINAL_SCORE);
        if (descriptor.isEmpty()) {
            return VectorScoreThresholdKind.PROVIDER_RAW;
        }
        return descriptor.get().thresholdKind();
    }

    private static boolean hasValue(VectorQueryDefaults defaults, String option) {
        if (defaults == null) return false;
        return switch (option) {
            case "topK" -> defaults.topK() != null;
            case "minScore" -> defaults.minScore() != null;
            case "candidateLimit" -> defaults.candidateLimit() != null;
            case "timeout" -> defaults.timeout() != null;
            case "consistency" -> defaults.consistency() != null;
            case "returnFields" -> !defaults.returnFields().isEmpty();
            default -> false;
        };
    }

    private void validateDefaults(VectorQueryDefaults defaults) {
        if (defaults.topK() == null || defaults.minScore() == null || defaults.candidateLimit() == null
                || defaults.timeout() == null || defaults.consistency() == null) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.INVALID_VALUE, "resolved query defaults must be complete");
        }
        validate(defaults.topK(), defaults.minScore(), Math.max(defaults.topK(), defaults.candidateLimit()),
                defaults.timeout(), defaults.returnFields(),
                Objects.requireNonNullElse(defaults.thresholdKind(), VectorScoreThresholdKind.NORMALIZED_RELEVANCE));
    }

    private void validate(
            int topK, double minScore, int candidateLimit, Duration timeout, Set<String> returnFields,
            VectorScoreThresholdKind thresholdKind) {
        if (topK < 1 || candidateLimit < topK || minScore < 0.0D
                || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.INVALID_VALUE, "advanced vector query contains an invalid value");
        }
        if (topK > MAX_TOP_K || candidateLimit > MAX_CANDIDATES || timeout.compareTo(MAX_TIMEOUT) > 0
                || returnFields.size() > MAX_RETURN_FIELDS) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.LIMIT_EXCEEDED, "advanced vector query exceeds a safety limit");
        }
        if (returnFields.stream().anyMatch(field -> field == null || !FIELD.matcher(field).matches())) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.INVALID_VALUE, "advanced vector query contains an invalid return field");
        }
        validateThreshold(minScore, thresholdKind);
    }

    private void validateThreshold(double minScore, VectorScoreThresholdKind thresholdKind) {
        if (thresholdKind == VectorScoreThresholdKind.NORMALIZED_RELEVANCE) {
            if (minScore > 1.0D) {
                throw new VectorQueryValidationException(
                        VectorQueryErrorCode.INVALID_VALUE,
                        "NORMALIZED_RELEVANCE minScore must be within [0, 1]");
            }
            return;
        }
        if (scoreSemantics != null) {
            Optional<VectorScoreDescriptor> descriptor = scoreSemantics.descriptor(VectorScoreStage.FINAL_SCORE);
            if (descriptor.isPresent()) {
                VectorScoreDescriptor d = descriptor.get();
                if (d.maximum() != null && minScore > d.maximum()) {
                    throw new VectorQueryValidationException(
                            VectorQueryErrorCode.INVALID_VALUE,
                            "PROVIDER_RAW minScore exceeds Store declared maximum " + d.maximum());
                }
                if (d.minimum() != null && minScore < d.minimum()) {
                    throw new VectorQueryValidationException(
                            VectorQueryErrorCode.INVALID_VALUE,
                            "PROVIDER_RAW minScore is below Store declared minimum " + d.minimum());
                }
            }
        }
    }
}
