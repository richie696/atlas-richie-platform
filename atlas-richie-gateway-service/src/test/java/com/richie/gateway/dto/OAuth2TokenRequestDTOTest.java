package com.richie.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OAuth2TokenRequestDTO}.
 */
@DisplayName("OAuth2TokenRequestDTO Tests")
class OAuth2TokenRequestDTOTest {

    @Nested
    @DisplayName("Default Constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should have all fields null by default")
        void shouldHaveAllFieldsNullByDefault() {
            OAuth2TokenRequestDTO dto = new OAuth2TokenRequestDTO();
            assertThat(dto.getGrantType()).isNull();
            assertThat(dto.getClientId()).isNull();
            assertThat(dto.getClientSecret()).isNull();
            assertThat(dto.getScope()).isNull();
            assertThat(dto.getRefreshToken()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter and Getter")
    class SetterAndGetter {

        @Test
        @DisplayName("should set and get all fields")
        void shouldSetAndGetAllFields() {
            OAuth2TokenRequestDTO dto = new OAuth2TokenRequestDTO();
            dto.setGrantType("client_credentials");
            dto.setClientId("client-001");
            dto.setClientSecret("secret-abc");
            dto.setScope("read write");
            dto.setRefreshToken("refresh-token-xyz");

            assertThat(dto.getGrantType()).isEqualTo("client_credentials");
            assertThat(dto.getClientId()).isEqualTo("client-001");
            assertThat(dto.getClientSecret()).isEqualTo("secret-abc");
            assertThat(dto.getScope()).isEqualTo("read write");
            assertThat(dto.getRefreshToken()).isEqualTo("refresh-token-xyz");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal when all fields match")
        void shouldBeEqualWhenAllFieldsMatch() {
            OAuth2TokenRequestDTO d1 = buildDto("client_credentials", "c1", "s1", "read", null);
            OAuth2TokenRequestDTO d2 = buildDto("client_credentials", "c1", "s1", "read", null);

            assertThat(d1).isEqualTo(d2);
            assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when grantType differs")
        void shouldNotBeEqualWhenGrantTypeDiffers() {
            OAuth2TokenRequestDTO d1 = buildDto("client_credentials", "c", "s", null, null);
            OAuth2TokenRequestDTO d2 = buildDto("refresh_token", "c", "s", null, null);

            assertThat(d1).isNotEqualTo(d2);
        }

        @Test
        @DisplayName("should not be equal when clientSecret differs")
        void shouldNotBeEqualWhenClientSecretDiffers() {
            OAuth2TokenRequestDTO d1 = buildDto("client_credentials", "c", "s1", null, null);
            OAuth2TokenRequestDTO d2 = buildDto("client_credentials", "c", "s2", null, null);

            assertThat(d1).isNotEqualTo(d2);
        }

        @Test
        @DisplayName("should not be equal when refreshToken differs")
        void shouldNotBeEqualWhenRefreshTokenDiffers() {
            OAuth2TokenRequestDTO d1 = buildDto("refresh_token", "c", "s", null, "rt1");
            OAuth2TokenRequestDTO d2 = buildDto("refresh_token", "c", "s", null, "rt2");

            assertThat(d1).isNotEqualTo(d2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("should contain all field names in toString output")
        void shouldContainAllFieldNamesInToStringOutput() {
            OAuth2TokenRequestDTO dto = buildDto("client_credentials", "c", "s", "read", null);

            String str = dto.toString();
            assertThat(str).contains("grantType");
            assertThat(str).contains("clientId");
            assertThat(str).contains("clientSecret");
            assertThat(str).contains("scope");
            assertThat(str).contains("refreshToken");
        }
    }

    @Nested
    @DisplayName("JsonProperty Mapping")
    class JsonPropertyMapping {

        @Test
        @DisplayName("should map grantType field to snake_case grant_type")
        void shouldMapGrantTypeFieldToSnakeCase() throws NoSuchFieldException {
            Field field = OAuth2TokenRequestDTO.class.getDeclaredField("grantType");
            JsonProperty annotation = field.getAnnotation(JsonProperty.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("grant_type");
        }

        @Test
        @DisplayName("should map clientId field to snake_case client_id")
        void shouldMapClientIdFieldToSnakeCase() throws NoSuchFieldException {
            Field field = OAuth2TokenRequestDTO.class.getDeclaredField("clientId");
            JsonProperty annotation = field.getAnnotation(JsonProperty.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("client_id");
        }

        @Test
        @DisplayName("should map clientSecret field to snake_case client_secret")
        void shouldMapClientSecretFieldToSnakeCase() throws NoSuchFieldException {
            Field field = OAuth2TokenRequestDTO.class.getDeclaredField("clientSecret");
            JsonProperty annotation = field.getAnnotation(JsonProperty.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("client_secret");
        }

        @Test
        @DisplayName("should map refreshToken field to snake_case refresh_token")
        void shouldMapRefreshTokenFieldToSnakeCase() throws NoSuchFieldException {
            Field field = OAuth2TokenRequestDTO.class.getDeclaredField("refreshToken");
            JsonProperty annotation = field.getAnnotation(JsonProperty.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("refresh_token");
        }

        @Test
        @DisplayName("should serialize to snake_case JSON keys")
        void shouldSerializeToSnakeCaseJsonKeys() throws Exception {
            OAuth2TokenRequestDTO dto = buildDto("client_credentials", "client-001", "secret-abc", "read", null);

            String json = new ObjectMapper().writeValueAsString(dto);

            assertThat(json).contains("\"grant_type\":\"client_credentials\"");
            assertThat(json).contains("\"client_id\":\"client-001\"");
            assertThat(json).contains("\"client_secret\":\"secret-abc\"");
            assertThat(json).contains("\"scope\":\"read\"");
        }

        @Test
        @DisplayName("should deserialize from snake_case JSON keys")
        void shouldDeserializeFromSnakeCaseJsonKeys() throws Exception {
            String json = "{\"grant_type\":\"refresh_token\",\"client_id\":\"c\",\"client_secret\":\"s\","
                    + "\"scope\":\"read\",\"refresh_token\":\"rt-value\"}";

            OAuth2TokenRequestDTO dto = new ObjectMapper().readValue(json, OAuth2TokenRequestDTO.class);

            assertThat(dto.getGrantType()).isEqualTo("refresh_token");
            assertThat(dto.getClientId()).isEqualTo("c");
            assertThat(dto.getClientSecret()).isEqualTo("s");
            assertThat(dto.getScope()).isEqualTo("read");
            assertThat(dto.getRefreshToken()).isEqualTo("rt-value");
        }
    }

    private static OAuth2TokenRequestDTO buildDto(String grantType, String clientId, String clientSecret,
                                                  String scope, String refreshToken) {
        OAuth2TokenRequestDTO dto = new OAuth2TokenRequestDTO();
        dto.setGrantType(grantType);
        dto.setClientId(clientId);
        dto.setClientSecret(clientSecret);
        dto.setScope(scope);
        dto.setRefreshToken(refreshToken);
        return dto;
    }
}
