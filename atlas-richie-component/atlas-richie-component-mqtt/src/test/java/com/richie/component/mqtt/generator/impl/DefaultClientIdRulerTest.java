package com.richie.component.mqtt.generator.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultClientIdRulerTest {

    @Test
    void getClientId_startsWithPrefix() {
        DefaultClientIdRuler ruler = new DefaultClientIdRuler();
        assertThat(ruler.getClientId()).startsWith("RY-");
    }

    @Test
    void getClientId_generatesDistinctIds() {
        DefaultClientIdRuler ruler = new DefaultClientIdRuler();
        String first = ruler.getClientId();
        String second = ruler.getClientId();
        assertThat(first).isNotEqualTo(second);
        assertThat(first.split("-")).hasSize(4);
    }
}
