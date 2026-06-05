package com.richie.component.cache.operations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SetCapacityLimitsTest {

    @Test
    void thresholds_matchReadmeGuidance() {
        assertThat(SetCapacityLimits.SET_RECOMMENDED_MAX_ELEMENTS).isEqualTo(5_000L);
        assertThat(SetCapacityLimits.SET_HARD_MAX_ELEMENTS).isEqualTo(10_000L);
    }

    @Test
    void exceedsRecommended_at5000() {
        assertThat(SetCapacityLimits.exceedsRecommended(4_999L)).isFalse();
        assertThat(SetCapacityLimits.exceedsRecommended(5_000L)).isTrue();
    }

    @Test
    void exceedsHardLimit_at10000() {
        assertThat(SetCapacityLimits.exceedsHardLimit(9_999L)).isFalse();
        assertThat(SetCapacityLimits.exceedsHardLimit(10_000L)).isTrue();
    }
}
