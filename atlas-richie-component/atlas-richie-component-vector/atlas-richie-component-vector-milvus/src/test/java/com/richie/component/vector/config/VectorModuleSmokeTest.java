package com.richie.component.vector.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorModuleSmokeTest {

    @Test
    void modulePackage_shouldBeDefined() {
        assertThat(getClass().getPackageName()).isEqualTo("com.richie.component.vector.config");
    }
}
