package com.richie.component.messaging.kinesis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KinesisModuleSmokeTest {

    @Test
    void modulePackage_shouldBeDefined() {
        assertThat(getClass().getPackageName()).isEqualTo("com.richie.component.messaging.kinesis");
    }
}
