package com.richie.component.cache.operations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZSetCapacityLimitsTest {

    @Test
    void thresholds_matchReadmeGuidance() {
        assertThat(ZSetCapacityLimits.ZSET_RECOMMENDED_MAX_ELEMENTS).isEqualTo(5_000L);
        assertThat(ZSetCapacityLimits.ZSET_HARD_MAX_ELEMENTS).isEqualTo(10_000L);
    }

    @Test
    void exceedsRecommended_at5000() {
        assertThat(ZSetCapacityLimits.exceedsRecommended(4_999L)).isFalse();
        assertThat(ZSetCapacityLimits.exceedsRecommended(5_000L)).isTrue();
    }

    @Test
    void exceedsHardLimit_at10000() {
        assertThat(ZSetCapacityLimits.exceedsHardLimit(9_999L)).isFalse();
        assertThat(ZSetCapacityLimits.exceedsHardLimit(10_000L)).isTrue();
    }
}
