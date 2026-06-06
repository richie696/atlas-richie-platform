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
