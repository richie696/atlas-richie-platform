package com.richie.component.storage.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectStorageStartupProbeTest {

    @Test
    void newProbeObjectKey_usesBasePathAndUniqueSuffix() {
        String key = ObjectStorageStartupProbe.newProbeObjectKey("probe-base");
        assertThat(key).startsWith("probe-base/.richie-storage-probe/");
        assertThat(key).isNotEqualTo(ObjectStorageStartupProbe.newProbeObjectKey("probe-base"));
    }

    @Test
    void content_returnsFixedPayload() {
        assertThat(new String(ObjectStorageStartupProbe.content(), StandardCharsets.UTF_8)).isEqualTo("ok");
    }
}
