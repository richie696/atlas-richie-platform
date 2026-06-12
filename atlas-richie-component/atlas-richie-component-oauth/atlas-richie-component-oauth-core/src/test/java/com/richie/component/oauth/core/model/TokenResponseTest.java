package com.richie.component.oauth.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenResponseTest {

    @Test
    void builder_createsObject() {
        TokenResponse response = TokenResponse.builder()
                .accessToken("access-token-123")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .refreshToken("refresh-token-456")
                .scope("read write")
                .build();

        assertThat(response.getAccessToken()).isEqualTo("access-token-123");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token-456");
        assertThat(response.getScope()).isEqualTo("read write");
    }

    @Test
    void defaultConstructor_createsEmptyObject() {
        TokenResponse response = new TokenResponse();
        assertThat(response.getAccessToken()).isNull();
        assertThat(response.getTokenType()).isNull();
        assertThat(response.getExpiresIn()).isNull();
        assertThat(response.getRefreshToken()).isNull();
        assertThat(response.getScope()).isNull();
    }

    @Test
    void allArgsConstructor_createsObject() {
        TokenResponse response = new TokenResponse("at-123", "Bearer", 7200L, "rt-456", "read");

        assertThat(response.getAccessToken()).isEqualTo("at-123");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(7200L);
        assertThat(response.getRefreshToken()).isEqualTo("rt-456");
        assertThat(response.getScope()).isEqualTo("read");
    }

    @Test
    void setters_updateFields() {
        TokenResponse response = new TokenResponse();
        response.setAccessToken("new-access-token");
        response.setTokenType("Bearer");
        response.setExpiresIn(1800L);
        response.setRefreshToken("new-refresh-token");
        response.setScope("write");

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(1800L);
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.getScope()).isEqualTo("write");
    }

    @Test
    void equals_and_hashCode_work() {
        TokenResponse response1 = TokenResponse.builder()
                .accessToken("at")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .refreshToken("rt")
                .scope("read")
                .build();

        TokenResponse response2 = TokenResponse.builder()
                .accessToken("at")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .refreshToken("rt")
                .scope("read")
                .build();

        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    void toString_containsAllFields() {
        TokenResponse response = TokenResponse.builder()
                .accessToken("at")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .refreshToken("rt")
                .scope("read")
                .build();

        String str = response.toString();
        assertThat(str).contains("at");
        assertThat(str).contains("Bearer");
        assertThat(str).contains("3600");
        assertThat(str).contains("rt");
        assertThat(str).contains("read");
    }
}
