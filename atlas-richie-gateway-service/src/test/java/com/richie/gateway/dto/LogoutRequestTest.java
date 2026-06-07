package com.richie.gateway.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LogoutRequest}.
 */
@DisplayName("LogoutRequest Tests")
class LogoutRequestTest {

    @Nested
    @DisplayName("Default Constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should have null mfaToken by default")
        void shouldHaveNullMfaTokenByDefault() {
            LogoutRequest request = new LogoutRequest();
            assertThat(request.getMfaToken()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter and Getter")
    class SetterAndGetter {

        @Test
        @DisplayName("should set and get mfaToken")
        void shouldSetAndGetMfaToken() {
            LogoutRequest request = new LogoutRequest();
            request.setMfaToken("mfa-temp-token-123");

            assertThat(request.getMfaToken()).isEqualTo("mfa-temp-token-123");
        }

        @Test
        @DisplayName("should allow overwriting mfaToken")
        void shouldAllowOverwritingMfaToken() {
            LogoutRequest request = new LogoutRequest();
            request.setMfaToken("first");
            request.setMfaToken("second");

            assertThat(request.getMfaToken()).isEqualTo("second");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal to another request with same mfaToken")
        void shouldBeEqualToAnotherRequestWithSameMfaToken() {
            LogoutRequest r1 = new LogoutRequest();
            r1.setMfaToken("token");
            LogoutRequest r2 = new LogoutRequest();
            r2.setMfaToken("token");

            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when mfaToken differs")
        void shouldNotBeEqualWhenMfaTokenDiffers() {
            LogoutRequest r1 = new LogoutRequest();
            r1.setMfaToken("token-a");
            LogoutRequest r2 = new LogoutRequest();
            r2.setMfaToken("token-b");

            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        @DisplayName("should be equal to itself")
        void shouldBeEqualToItself() {
            LogoutRequest request = new LogoutRequest();
            request.setMfaToken("token");

            assertThat(request).isEqualTo(request);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("should contain mfaToken field name in toString output")
        void shouldContainMfaTokenFieldNameInToStringOutput() {
            LogoutRequest request = new LogoutRequest();
            request.setMfaToken("token-value");

            assertThat(request.toString()).contains("mfaToken");
        }
    }
}
