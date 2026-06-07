package com.richie.gateway.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OAuth2TokenDTO}.
 */
@DisplayName("OAuth2TokenDTO Tests")
class OAuth2TokenDTOTest {

    @Nested
    @DisplayName("Default Constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should have all fields null by default")
        void shouldHaveAllFieldsNullByDefault() {
            OAuth2TokenDTO dto = new OAuth2TokenDTO();
            assertThat(dto.getToken()).isNull();
            assertThat(dto.getTokenTypeHint()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter and Getter")
    class SetterAndGetter {

        @Test
        @DisplayName("should set and get token")
        void shouldSetAndGetToken() {
            OAuth2TokenDTO dto = new OAuth2TokenDTO();
            dto.setToken("access-token-value");

            assertThat(dto.getToken()).isEqualTo("access-token-value");
        }

        @Test
        @DisplayName("should set and get tokenTypeHint")
        void shouldSetAndGetTokenTypeHint() {
            OAuth2TokenDTO dto = new OAuth2TokenDTO();
            dto.setTokenTypeHint("access_token");

            assertThat(dto.getTokenTypeHint()).isEqualTo("access_token");
        }

        @Test
        @DisplayName("should set and get both fields")
        void shouldSetAndGetBothFields() {
            OAuth2TokenDTO dto = new OAuth2TokenDTO();
            dto.setToken("refresh-token-value");
            dto.setTokenTypeHint("refresh_token");

            assertThat(dto.getToken()).isEqualTo("refresh-token-value");
            assertThat(dto.getTokenTypeHint()).isEqualTo("refresh_token");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal when fields match")
        void shouldBeEqualWhenFieldsMatch() {
            OAuth2TokenDTO d1 = buildDto("t", "access_token");
            OAuth2TokenDTO d2 = buildDto("t", "access_token");

            assertThat(d1).isEqualTo(d2);
            assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when token differs")
        void shouldNotBeEqualWhenTokenDiffers() {
            OAuth2TokenDTO d1 = buildDto("t1", "access_token");
            OAuth2TokenDTO d2 = buildDto("t2", "access_token");

            assertThat(d1).isNotEqualTo(d2);
        }

        @Test
        @DisplayName("should not be equal when tokenTypeHint differs")
        void shouldNotBeEqualWhenTokenTypeHintDiffers() {
            OAuth2TokenDTO d1 = buildDto("t", "access_token");
            OAuth2TokenDTO d2 = buildDto("t", "refresh_token");

            assertThat(d1).isNotEqualTo(d2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("should contain field names in toString output")
        void shouldContainFieldNamesInToStringOutput() {
            OAuth2TokenDTO dto = buildDto("t", "access_token");

            String str = dto.toString();
            assertThat(str).contains("token");
            assertThat(str).contains("tokenTypeHint");
        }
    }

    private static OAuth2TokenDTO buildDto(String token, String tokenTypeHint) {
        OAuth2TokenDTO dto = new OAuth2TokenDTO();
        dto.setToken(token);
        dto.setTokenTypeHint(tokenTypeHint);
        return dto;
    }
}
