package com.richie.component.liquibase.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiquibasePropertiesTest {

    @Test
    void defaults_enableMigrationWithScanDisabled() {
        LiquibaseProperties properties = new LiquibaseProperties();

        assertThat(properties.isEnable()).isTrue();
        assertThat(properties.isEnableScan()).isFalse();
        assertThat(properties.isDryRun()).isFalse();
        assertThat(properties.getChangeLogs()).isNotEmpty();
    }
}
