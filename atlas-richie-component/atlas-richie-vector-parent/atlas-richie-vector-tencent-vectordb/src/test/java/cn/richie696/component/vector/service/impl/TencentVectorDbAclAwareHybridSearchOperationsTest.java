package cn.richie696.component.vector.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TencentVectorDbAclAwareHybridSearchOperationsTest {

    @Test
    void permitsCoreRrfFallbackOnlyForAnExplicitNativeHybridCapabilityMismatch() {
        assertThat(TencentVectorDbAclAwareHybridSearchOperations.isUnsupportedNativeHybrid(
                new IllegalStateException("hybrid search is not supported by this deployment"))).isTrue();
        assertThat(TencentVectorDbAclAwareHybridSearchOperations.isUnsupportedNativeHybrid(
                new IllegalStateException("invalid parameter: rerank is not implemented"))).isTrue();
    }

    @Test
    void neverTreatsCredentialAuthorizationOrTimeoutFailureAsFallbackEligible() {
        assertThat(TencentVectorDbAclAwareHybridSearchOperations.isUnsupportedNativeHybrid(
                new IllegalStateException("permission denied"))).isFalse();
        assertThat(TencentVectorDbAclAwareHybridSearchOperations.isUnsupportedNativeHybrid(
                new IllegalStateException("request timeout while invoking hybrid search"))).isFalse();
        assertThat(TencentVectorDbAclAwareHybridSearchOperations.isUnsupportedNativeHybrid(
                new IllegalStateException("unauthenticated"))).isFalse();
    }
}
