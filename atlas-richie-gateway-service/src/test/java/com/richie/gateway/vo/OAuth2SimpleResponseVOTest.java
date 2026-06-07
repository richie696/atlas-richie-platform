package com.richie.gateway.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OAuth2SimpleResponseVO}.
 */
@DisplayName("OAuth2SimpleResponseVO Tests")
class OAuth2SimpleResponseVOTest {

    @Nested
    @DisplayName("Default Constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should have all null fields by default")
        void shouldHaveAllNullFieldsByDefault() {
            OAuth2SimpleResponseVO vo = new OAuth2SimpleResponseVO();
            assertThat(vo.getCode()).isNull();
            assertThat(vo.getMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("AllArgsConstructor")
    class AllArgsConstructor {

        @Test
        @DisplayName("should create instance with all args constructor")
        void shouldCreateInstanceWithAllArgsConstructor() {
            OAuth2SimpleResponseVO vo = new OAuth2SimpleResponseVO("SUCCESS", "Operation completed");

            assertThat(vo.getCode()).isEqualTo("SUCCESS");
            assertThat(vo.getMessage()).isEqualTo("Operation completed");
        }
    }

    @Nested
    @DisplayName("Setter and Getter")
    class SetterAndGetter {

        @Test
        @DisplayName("should set and get all fields correctly")
        void shouldSetAndGetAllFieldsCorrectly() {
            OAuth2SimpleResponseVO vo = new OAuth2SimpleResponseVO();
            vo.setCode("ERROR");
            vo.setMessage("Something went wrong");

            assertThat(vo.getCode()).isEqualTo("ERROR");
            assertThat(vo.getMessage()).isEqualTo("Something went wrong");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal to another VO with same values")
        void shouldBeEqualToAnotherVoWithSameValues() {
            OAuth2SimpleResponseVO vo1 = new OAuth2SimpleResponseVO("OK", "done");
            OAuth2SimpleResponseVO vo2 = new OAuth2SimpleResponseVO("OK", "done");

            assertThat(vo1).isEqualTo(vo2);
            assertThat(vo1.hashCode()).isEqualTo(vo2.hashCode());
        }

        @Test
        @DisplayName("should not be equal to another VO with different values")
        void shouldNotBeEqualToAnotherVoWithDifferentValues() {
            OAuth2SimpleResponseVO vo1 = new OAuth2SimpleResponseVO("A", "msg");
            OAuth2SimpleResponseVO vo2 = new OAuth2SimpleResponseVO("B", "msg");

            assertThat(vo1).isNotEqualTo(vo2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("should contain all fields in toString output")
        void shouldContainAllFieldsInToStringOutput() {
            OAuth2SimpleResponseVO vo = new OAuth2SimpleResponseVO("code", "message");

            String str = vo.toString();
            assertThat(str).contains("code");
            assertThat(str).contains("message");
        }
    }
}