package com.richie.component.web.core.degrade;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Trigger} 枚举值完整性测试。
 */
class TriggerTest {

    @Test
    void enumValues_count() {
        assertThat(Trigger.values()).hasSize(3);
    }

    @Test
    void enumValues_names() {
        assertThat(Trigger.EXCEPTION.name()).isEqualTo("EXCEPTION");
        assertThat(Trigger.HIGH_LATENCY.name()).isEqualTo("HIGH_LATENCY");
        assertThat(Trigger.CUSTOM.name()).isEqualTo("CUSTOM");
    }

    @Test
    void valueOf_roundtrip() {
        for (Trigger t : Trigger.values()) {
            assertThat(Trigger.valueOf(t.name())).isSameAs(t);
        }
    }
}