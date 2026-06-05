package com.richie.component.storage.local.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageModuleSmokeTest {

    @Test
    void modulePackage_shouldBeDefined() {
        assertThat(getClass().getPackageName()).isEqualTo("com.richie.component.storage.local.config");
    }
}
