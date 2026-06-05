package com.richie.component.http.httpclient5;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpModuleSmokeTest {

    @Test
    void modulePackage_shouldBeDefined() {
        assertThat(getClass().getPackageName()).isEqualTo("com.richie.component.http.httpclient5");
    }
}
