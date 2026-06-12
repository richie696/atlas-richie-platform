package com.richie.component.oauth.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2ErrorResponseTest {

    @Test
    void builder_createsObject() {
        OAuth2ErrorResponse response = OAuth2ErrorResponse.builder()
                .error("invalid_client")
                .errorDescription("Client authentication failed")
                .errorUri("https://docs.example.com/error")
                .build();

        assertThat(response.getError()).isEqualTo("invalid_client");
        assertThat(response.getErrorDescription()).isEqualTo("Client authentication failed");
        assertThat(response.getErrorUri()).isEqualTo("https://docs.example.com/error");
    }

    @Test
    void defaultConstructor_createsEmptyObject() {
        OAuth2ErrorResponse response = new OAuth2ErrorResponse();
        assertThat(response.getError()).isNull();
        assertThat(response.getErrorDescription()).isNull();
        assertThat(response.getErrorUri()).isNull();
    }

    @Test
    void allArgsConstructor_createsObject() {
        OAuth2ErrorResponse response = new OAuth2ErrorResponse("error", "desc", "uri");

        assertThat(response.getError()).isEqualTo("error");
        assertThat(response.getErrorDescription()).isEqualTo("desc");
        assertThat(response.getErrorUri()).isEqualTo("uri");
    }

    @Test
    void setters_updateFields() {
        OAuth2ErrorResponse response = new OAuth2ErrorResponse();
        response.setError("new-error");
        response.setErrorDescription("new-desc");
        response.setErrorUri("new-uri");

        assertThat(response.getError()).isEqualTo("new-error");
        assertThat(response.getErrorDescription()).isEqualTo("new-desc");
        assertThat(response.getErrorUri()).isEqualTo("new-uri");
    }

    @Test
    void equals_and_hashCode_work() {
        OAuth2ErrorResponse response1 = OAuth2ErrorResponse.builder()
                .error("invalid_client")
                .errorDescription("desc")
                .errorUri("uri")
                .build();

        OAuth2ErrorResponse response2 = OAuth2ErrorResponse.builder()
                .error("invalid_client")
                .errorDescription("desc")
                .errorUri("uri")
                .build();

        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    void toString_containsAllFields() {
        OAuth2ErrorResponse response = OAuth2ErrorResponse.builder()
                .error("error")
                .errorDescription("desc")
                .errorUri("uri")
                .build();

        String str = response.toString();
        assertThat(str).contains("error");
        assertThat(str).contains("desc");
        assertThat(str).contains("uri");
    }
}
