/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
