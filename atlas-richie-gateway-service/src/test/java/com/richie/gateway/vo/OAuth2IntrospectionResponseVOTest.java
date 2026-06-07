package com.richie.gateway.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OAuth2IntrospectionResponseVO Tests")
class OAuth2IntrospectionResponseVOTest {

    @Nested
    @DisplayName("Default Constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should have all null fields and false active by default")
        void shouldHaveAllNullFieldsAndFalseActiveByDefault() {
            OAuth2IntrospectionResponseVO vo = new OAuth2IntrospectionResponseVO();
            assertThat(vo.isActive()).isFalse();
            assertThat(vo.getClientId()).isNull();
            assertThat(vo.getTokenType()).isNull();
            assertThat(vo.getScope()).isNull();
        }
    }

    @Nested
    @DisplayName("AllArgsConstructor")
    class AllArgsConstructor {

        @Test
        @DisplayName("should create instance with all args constructor")
        void shouldCreateInstanceWithAllArgsConstructor() {
            OAuth2IntrospectionResponseVO vo = new OAuth2IntrospectionResponseVO(true, "client-123", "Bearer", "read write");

            assertThat(vo.isActive()).isTrue();
            assertThat(vo.getClientId()).isEqualTo("client-123");
            assertThat(vo.getTokenType()).isEqualTo("Bearer");
            assertThat(vo.getScope()).isEqualTo("read write");
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderPattern {

        @Test
        @DisplayName("should create instance with builder")
        void shouldCreateInstanceWithBuilder() {
            OAuth2IntrospectionResponseVO vo = OAuth2IntrospectionResponseVO.builder()
                    .active(true)
                    .clientId("client-abc")
                    .tokenType("Bearer")
                    .scope("read")
                    .build();

            assertThat(vo.isActive()).isTrue();
            assertThat(vo.getClientId()).isEqualTo("client-abc");
            assertThat(vo.getTokenType()).isEqualTo("Bearer");
            assertThat(vo.getScope()).isEqualTo("read");
        }

        @Test
        @DisplayName("should build with active false")
        void shouldBuildWithActiveFalse() {
            OAuth2IntrospectionResponseVO vo = OAuth2IntrospectionResponseVO.builder()
                    .active(false)
                    .build();

            assertThat(vo.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("Setter and Getter")
    class SetterAndGetter {

        @Test
        @DisplayName("should set and get all fields correctly")
        void shouldSetAndGetAllFieldsCorrectly() {
            OAuth2IntrospectionResponseVO vo = new OAuth2IntrospectionResponseVO();
            vo.setActive(true);
            vo.setClientId("my-client");
            vo.setTokenType("Bearer");
            vo.setScope("admin");

            assertThat(vo.isActive()).isTrue();
            assertThat(vo.getClientId()).isEqualTo("my-client");
            assertThat(vo.getTokenType()).isEqualTo("Bearer");
            assertThat(vo.getScope()).isEqualTo("admin");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal to another VO with same values")
        void shouldBeEqualToAnotherVoWithSameValues() {
            OAuth2IntrospectionResponseVO vo1 = OAuth2IntrospectionResponseVO.builder()
                    .active(true)
                    .clientId("client")
                    .build();
            OAuth2IntrospectionResponseVO vo2 = OAuth2IntrospectionResponseVO.builder()
                    .active(true)
                    .clientId("client")
                    .build();

            assertThat(vo1).isEqualTo(vo2);
            assertThat(vo1.hashCode()).isEqualTo(vo2.hashCode());
        }

        @Test
        @DisplayName("should not be equal to another VO with different active status")
        void shouldNotBeEqualToAnotherVoWithDifferentActiveStatus() {
            OAuth2IntrospectionResponseVO vo1 = OAuth2IntrospectionResponseVO.builder().active(true).build();
            OAuth2IntrospectionResponseVO vo2 = OAuth2IntrospectionResponseVO.builder().active(false).build();

            assertThat(vo1).isNotEqualTo(vo2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("should contain all fields in toString output")
        void shouldContainAllFieldsInToStringOutput() {
            OAuth2IntrospectionResponseVO vo = OAuth2IntrospectionResponseVO.builder()
                    .active(true)
                    .clientId("client")
                    .tokenType("Bearer")
                    .scope("read")
                    .build();

            String str = vo.toString();
            assertThat(str).contains("active");
            assertThat(str).contains("clientId");
            assertThat(str).contains("tokenType");
            assertThat(str).contains("scope");
        }
    }
}
