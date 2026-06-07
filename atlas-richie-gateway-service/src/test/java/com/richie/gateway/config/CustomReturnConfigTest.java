package com.richie.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CustomReturnConfig}.
 */
@DisplayName("CustomReturnConfig")
class CustomReturnConfigTest {

    @Nested
    @DisplayName("defaults")
    class DefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            CustomReturnConfig config = new CustomReturnConfig();
            assertThat(config.getStatus()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
            assertThat(config.getErrorMessage()).isEqualTo("请求过于频繁，请稍后再试。");
        }
    }

    @Nested
    @DisplayName("setters")
    class SettersTest {

        @Test
        @DisplayName("setStatus should update value")
        void setStatusShouldUpdateValue() {
            CustomReturnConfig config = new CustomReturnConfig();
            config.setStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
            assertThat(config.getStatus()).isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        }

        @Test
        @DisplayName("setErrorMessage should update value")
        void setErrorMessageShouldUpdateValue() {
            CustomReturnConfig config = new CustomReturnConfig();
            config.setErrorMessage("Custom message");
            assertThat(config.getErrorMessage()).isEqualTo("Custom message");
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            CustomReturnConfig config = new CustomReturnConfig();
            config.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
            config.setErrorMessage("Custom error");

            assertThat(config.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
            assertThat(config.getErrorMessage()).isEqualTo("Custom error");
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("two instances with same values should be equal")
        void twoInstancesWithSameValuesShouldBeEqual() {
            CustomReturnConfig a = new CustomReturnConfig();
            a.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
            CustomReturnConfig b = new CustomReturnConfig();
            b.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two instances with different values should not be equal")
        void twoInstancesWithDifferentValuesShouldNotBeEqual() {
            CustomReturnConfig a = new CustomReturnConfig();
            a.setStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
            CustomReturnConfig b = new CustomReturnConfig();
            b.setStatus(org.springframework.http.HttpStatus.NOT_FOUND);

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString should contain field names")
        void toStringShouldContainFieldNames() {
            CustomReturnConfig config = new CustomReturnConfig();
            String str = config.toString();

            assertThat(str).contains("CustomReturnConfig");
            assertThat(str).contains("status");
            assertThat(str).contains("errorMessage");
        }
    }
}
