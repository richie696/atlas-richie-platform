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
