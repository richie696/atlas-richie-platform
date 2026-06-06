package com.richie.component.logging.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorInfoTest {

    @Test
    void accessors_roundTrip() {
        OperatorInfo info = new OperatorInfo("id-1", "name-1");

        assertThat(info.getId()).isEqualTo("id-1");
        assertThat(info.getName()).isEqualTo("name-1");
    }
}
