/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.topology.VectorStoreId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorQueryContractsTest {

    @Test
    void resolvesCoreProviderStoreAndRequestPrecedence() {
        VectorQueryDefaults provider = new VectorQueryDefaults(
                20, null, 40, null, null, Set.of("content"));
        VectorQueryDefaults store = new VectorQueryDefaults(
                null, 0.2D, 60, Duration.ofSeconds(20), null, Set.of());
        VectorQueryResolver resolver = new VectorQueryResolver(provider, store);

        ResolvedVectorQuery defaults = resolver.resolve(VectorQueryRequest.of("synthetic query"));
        assertThat(defaults.topK()).isEqualTo(20);
        assertThat(defaults.minScore()).isEqualTo(0.2D);
        assertThat(defaults.candidateLimit()).isEqualTo(60);
        assertThat(defaults.timeout()).isEqualTo(Duration.ofSeconds(20));
        assertThat(defaults.returnFields()).containsExactly("content");
        assertThat(resolver.sourceFor("topK", false)).isEqualTo(VectorParameterSource.PROVIDER_DEFAULT);
        assertThat(resolver.sourceFor("minScore", false)).isEqualTo(VectorParameterSource.STORE_DEFAULT);
        assertThat(resolver.sourceFor("timeout", true)).isEqualTo(VectorParameterSource.REQUEST);

        ResolvedVectorQuery perCall = resolver.resolve(new VectorQueryRequest(
                "synthetic query", 5, 0.5D, null, Set.of("id"), 15,
                Duration.ofSeconds(5), VectorConsistencyPreference.STRONG, null));
        assertThat(perCall.topK()).isEqualTo(5);
        assertThat(perCall.candidateLimit()).isEqualTo(15);
        assertThat(perCall.consistency()).isEqualTo(VectorConsistencyPreference.STRONG);
        assertThatThrownBy(() -> perCall.returnFields().add("secret"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidAndOversizedQueriesWithStableCodes() {
        VectorQueryResolver resolver = new VectorQueryResolver(null, null);

        assertThatThrownBy(() -> resolver.resolve(new VectorQueryRequest(
                "q", 10, null, null, Set.of(), 5, null, null, null)))
                .isInstanceOfSatisfying(VectorQueryValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(VectorQueryErrorCode.INVALID_VALUE));
        assertThatThrownBy(() -> resolver.resolve(new VectorQueryRequest(
                "q", VectorQueryResolver.MAX_TOP_K + 1, null, null, Set.of(), null, null, null, null)))
                .isInstanceOfSatisfying(VectorQueryValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(VectorQueryErrorCode.LIMIT_EXCEEDED));
    }

    @Test
    void receiptIsImmutableAndContainsNoQueryOrPhysicalTarget() {
        VectorSearchExecutionReceipt receipt = new VectorSearchExecutionReceipt(
                VectorStoreId.of("synthetic-store"),
                VectorProvider.MILVUS,
                "1.0",
                "1.0",
                "0123456789abcdef",
                List.of(new VectorParameterEvidence(
                        "topK", "5", "10", "5", "5",
                        VectorParameterSource.REQUEST, VectorParameterDisposition.APPLIED, "sdk-request")));

        assertThatThrownBy(() -> receipt.parameters().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(receipt.adapterVersion()).isEqualTo("1.0");
        assertThat(receipt.capabilityVersion()).isEqualTo("1.0");
        assertThat(receipt.toString())
                .doesNotContain("synthetic query")
                .doesNotContain("physical-index")
                .doesNotContain("credential");
    }
}
