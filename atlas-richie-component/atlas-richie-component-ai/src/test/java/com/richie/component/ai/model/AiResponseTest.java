/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ai.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiResponseTest {

    @Test
    void successFactory_shouldPopulateCoreFields() {
        AiResponse response = AiResponse.success("hello", "stub-model", "OPENAI");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isEqualTo("hello");
        assertThat(response.getModelName()).isEqualTo("stub-model");
        assertThat(response.getProvider()).isEqualTo("OPENAI");
        assertThat(response.getResponseTime()).isNotNull();
    }

    @Test
    void failureFactory_shouldPopulateErrorFields() {
        AiResponse response = AiResponse.failure("boom", "CALL_FAILED");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("boom");
        assertThat(response.getErrorCode()).isEqualTo("CALL_FAILED");
    }
}
