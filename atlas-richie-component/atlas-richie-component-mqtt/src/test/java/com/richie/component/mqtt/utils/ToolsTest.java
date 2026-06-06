package com.richie.component.mqtt.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolsTest {

    @Test
    void macSignature_isDeterministic() throws Exception {
        String first = Tools.macSignature("client-1", "secret");
        String second = Tools.macSignature("client-1", "secret");

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotBlank();
    }
}
