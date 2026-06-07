package com.richie.gateway.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OAuth2TokenResponseVO}.
 * <p>
 * Validates the OAuth2 token response shape (RFC 6749 snake_case fields via
 * {@code @JsonProperty}), Lombok-generated accessors, builder, equality and
 * toString behavior.
 */
@DisplayName("OAuth2TokenResponseVO Tests")
class OAuth2TokenResponseVOTest {

    @Nested
    @DisplayName("Default Constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should have all null fields by default")
        void shouldHaveAllNullFieldsByDefault() {
            OAuth2TokenResponseVO vo = new OAuth2TokenResponseVO();

            assertThat(vo.getAccessToken()).isNull();
            assertThat(vo.getTokenType()).isNull();
            assertThat(vo.getExpiresIn()).isNull();
            assertThat(vo.getRefreshToken()).isNull();
        }
    }

    @Nested
    @DisplayName("AllArgsConstructor")
    class AllArgsConstructor {

        @Test
        @DisplayName("should create instance with all args constructor")
        void shouldCreateInstanceWithAllArgsConstructor() {
            OAuth2TokenResponseVO vo = new OAuth2TokenResponseVO(
                    "access-token-value",
                    "Bearer",
                    3600L,
                    "refresh-token-value");

            assertThat(vo.getAccessToken()).isEqualTo("access-token-value");
            assertThat(vo.getTokenType()).isEqualTo("Bearer");
            assertThat(vo.getExpiresIn()).isEqualTo(3600L);
            assertThat(vo.getRefreshToken()).isEqualTo("refresh-token-value");
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPattern {

        @Test
        @DisplayName("should create instance with builder")
        void shouldCreateInstanceWithBuilder() {
            OAuth2TokenResponseVO vo = OAuth2TokenResponseVO.builder()
                    .accessToken("at")
                    .tokenType("Bearer")
                    .expiresIn(7200L)
                    .refreshToken("rt")
                    .build();

            assertThat(vo.getAccessToken()).isEqualTo("at");
            assertThat(vo.getTokenType()).isEqualTo("Bearer");
            assertThat(vo.getExpiresIn()).isEqualTo(7200L);
            assertThat(vo.getRefreshToken()).isEqualTo("rt");
        }

        @Test
        @DisplayName("should build with only access token")
        void shouldBuildWithOnlyAccessToken() {
            OAuth2TokenResponseVO vo = OAuth2TokenResponseVO.builder()
                    .accessToken("only-access")
                    .build();

            assertThat(vo.getAccessToken()).isEqualTo("only-access");
            assertThat(vo.getTokenType()).isNull();
            assertThat(vo.getExpiresIn()).isNull();
            assertThat(vo.getRefreshToken()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter and Getter")
    class SetterAndGetter {

        @Test
        @DisplayName("should set and get all fields correctly")
        void shouldSetAndGetAllFieldsCorrectly() {
            OAuth2TokenResponseVO vo = new OAuth2TokenResponseVO();
            vo.setAccessToken("new-access");
            vo.setTokenType("Bearer");
            vo.setExpiresIn(1800L);
            vo.setRefreshToken("new-refresh");

            assertThat(vo.getAccessToken()).isEqualTo("new-access");
            assertThat(vo.getTokenType()).isEqualTo("Bearer");
            assertThat(vo.getExpiresIn()).isEqualTo(1800L);
            assertThat(vo.getRefreshToken()).isEqualTo("new-refresh");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal to another VO with same values")
        void shouldBeEqualToAnotherVoWithSameValues() {
            OAuth2TokenResponseVO vo1 = OAuth2TokenResponseVO.builder()
                    .accessToken("at")
                    .tokenType("Bearer")
                    .expiresIn(3600L)
                    .refreshToken("rt")
                    .build();
            OAuth2TokenResponseVO vo2 = OAuth2TokenResponseVO.builder()
                    .accessToken("at")
                    .tokenType("Bearer")
                    .expiresIn(3600L)
                    .refreshToken("rt")
                    .build();

            assertThat(vo1).isEqualTo(vo2);
            assertThat(vo1.hashCode()).isEqualTo(vo2.hashCode());
        }

        @Test
        @DisplayName("should not be equal to another VO with different access token")
        void shouldNotBeEqualToAnotherVoWithDifferentAccessToken() {
            OAuth2TokenResponseVO vo1 = OAuth2TokenResponseVO.builder().accessToken("at1").build();
            OAuth2TokenResponseVO vo2 = OAuth2TokenResponseVO.builder().accessToken("at2").build();

            assertThat(vo1).isNotEqualTo(vo2);
        }

        @Test
        @DisplayName("should not be equal to another VO with different expires_in")
        void shouldNotBeEqualToAnotherVoWithDifferentExpiresIn() {
            OAuth2TokenResponseVO vo1 = OAuth2TokenResponseVO.builder().expiresIn(3600L).build();
            OAuth2TokenResponseVO vo2 = OAuth2TokenResponseVO.builder().expiresIn(7200L).build();

            assertThat(vo1).isNotEqualTo(vo2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("should contain all fields in toString output")
        void shouldContainAllFieldsInToStringOutput() {
            OAuth2TokenResponseVO vo = OAuth2TokenResponseVO.builder()
                    .accessToken("at")
                    .tokenType("Bearer")
                    .expiresIn(3600L)
                    .refreshToken("rt")
                    .build();

            String str = vo.toString();
            assertThat(str).contains("accessToken");
            assertThat(str).contains("tokenType");
            assertThat(str).contains("expiresIn");
            assertThat(str).contains("refreshToken");
        }
    }
}
