package com.richie.component.desensitize.core.strategy;

import com.richie.component.desensitize.core.model.MaskType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaskingStrategyRegistryTest {

    @Test
    void requireReturnsRegisteredStrategy() {
        MaskingStrategyRegistry registry = new MaskingStrategyRegistry(List.of(new PhoneMaskingStrategy()));

        assertThat(registry.require(MaskType.PHONE)).isInstanceOf(PhoneMaskingStrategy.class);
    }

    @Test
    void requireThrowsWhenTypeMissing() {
        MaskingStrategyRegistry registry = new MaskingStrategyRegistry(List.of());

        assertThatThrownBy(() -> registry.require(MaskType.PHONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No MaskingStrategy");
    }
}
