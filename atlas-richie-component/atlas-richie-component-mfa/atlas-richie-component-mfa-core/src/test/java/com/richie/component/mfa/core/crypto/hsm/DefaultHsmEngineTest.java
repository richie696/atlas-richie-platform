package com.richie.component.mfa.core.crypto.hsm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultHsmEngineTest {

    @Test
    void placeholderEngine_returnsPlaintextAndUnavailable() {
        DefaultHsmEngine engine = new DefaultHsmEngine();
        assertThat(engine.encrypt("secret")).isEqualTo("secret");
        assertThat(engine.decrypt("secret")).isEqualTo("secret");
        assertThat(engine.isAvailable()).isFalse();
    }
}
