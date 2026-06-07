package com.richie.gateway.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth2ErrorResponseVO Tests")
class OAuth2ErrorResponseVOTest {

    @Nested
    @DisplayName("Default Constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should have all null fields by default")
        void shouldHaveAllNullFieldsByDefault() {
            OAuth2ErrorResponseVO vo = new OAuth2ErrorResponseVO();
            assertThat(vo.getError()).isNull();
            assertThat(vo.getErrorDescription()).isNull();
            assertThat(vo.getErrorUri()).isNull();
        }
    }

    @Nested
    @DisplayName("AllArgsConstructor")
    class AllArgsConstructor {

        @Test
        @DisplayName("should create instance with all args constructor")
        void shouldCreateInstanceWithAllArgsConstructor() {
            OAuth2ErrorResponseVO vo = new OAuth2ErrorResponseVO("invalid_request", "Missing required parameter", "https://docs.example.com/error");

            assertThat(vo.getError()).isEqualTo("invalid_request");
            assertThat(vo.getErrorDescription()).isEqualTo("Missing required parameter");
            assertThat(vo.getErrorUri()).isEqualTo("https://docs.example.com/error");
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPattern {

        @Test
        @DisplayName("should create instance with builder")
        void shouldCreateInstanceWithBuilder() {
            OAuth2ErrorResponseVO vo = OAuth2ErrorResponseVO.builder()
                    .error("invalid_client")
                    .errorDescription("Client authentication failed")
                    .errorUri("https://docs.example.com/invalid_client")
                    .build();

            assertThat(vo.getError()).isEqualTo("invalid_client");
            assertThat(vo.getErrorDescription()).isEqualTo("Client authentication failed");
            assertThat(vo.getErrorUri()).isEqualTo("https://docs.example.com/invalid_client");
        }

        @Test
        @DisplayName("should build with only error code")
        void shouldBuildWithOnlyErrorCode() {
            OAuth2ErrorResponseVO vo = OAuth2ErrorResponseVO.builder()
                    .error("error_code")
                    .build();

            assertThat(vo.getError()).isEqualTo("error_code");
            assertThat(vo.getErrorDescription()).isNull();
            assertThat(vo.getErrorUri()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter and Getter")
    class SetterAndGetter {

        @Test
        @DisplayName("should set and get all fields correctly")
        void shouldSetAndGetAllFieldsCorrectly() {
            OAuth2ErrorResponseVO vo = new OAuth2ErrorResponseVO();
            vo.setError("error");
            vo.setErrorDescription("description");
            vo.setErrorUri("uri");

            assertThat(vo.getError()).isEqualTo("error");
            assertThat(vo.getErrorDescription()).isEqualTo("description");
            assertThat(vo.getErrorUri()).isEqualTo("uri");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal to another VO with same values")
        void shouldBeEqualToAnotherVoWithSameValues() {
            OAuth2ErrorResponseVO vo1 = OAuth2ErrorResponseVO.builder()
                    .error("err")
                    .errorDescription("desc")
                    .build();
            OAuth2ErrorResponseVO vo2 = OAuth2ErrorResponseVO.builder()
                    .error("err")
                    .errorDescription("desc")
                    .build();

            assertThat(vo1).isEqualTo(vo2);
            assertThat(vo1.hashCode()).isEqualTo(vo2.hashCode());
        }

        @Test
        @DisplayName("should not be equal to another VO with different values")
        void shouldNotBeEqualToAnotherVoWithDifferentValues() {
            OAuth2ErrorResponseVO vo1 = new OAuth2ErrorResponseVO("err1", "desc", null);
            OAuth2ErrorResponseVO vo2 = new OAuth2ErrorResponseVO("err2", "desc", null);

            assertThat(vo1).isNotEqualTo(vo2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("should contain all fields in toString output")
        void shouldContainAllFieldsInToStringOutput() {
            OAuth2ErrorResponseVO vo = OAuth2ErrorResponseVO.builder()
                    .error("error")
                    .errorDescription("description")
                    .errorUri("uri")
                    .build();

            String str = vo.toString();
            assertThat(str).contains("error");
            assertThat(str).contains("errorDescription");
            assertThat(str).contains("errorUri");
        }
    }
}
