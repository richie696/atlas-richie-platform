package com.richie.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link IOAuthFilterConfig}.
 */
@DisplayName("IOAuthFilterConfig")
class IOAuthFilterConfigTest {

    @Nested
    @DisplayName("defaults")
    class DefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            IOAuthFilterConfig config = new IOAuthFilterConfig();
            assertThat(config.isEnable()).isFalse();
            assertThat(config.getTokenSecret()).isNull();
            assertThat(config.getDefaultTokenValidDuration()).isEqualTo(2);
            assertThat(config.getDefaultRefreshTokenValidDuration()).isEqualTo(720);
            assertThat(config.isRevokePreviousTokensOnIssue()).isFalse();
            assertThat(config.isEnableDailyIssueLimit()).isTrue();
        }
    }

    @Nested
    @DisplayName("setters")
    class SettersTest {

        @Test
        @DisplayName("setEnable should update value")
        void setEnableShouldUpdateValue() {
            IOAuthFilterConfig config = new IOAuthFilterConfig();
            config.setEnable(true);
            assertThat(config.isEnable()).isTrue();
        }

        @Test
        @DisplayName("setTokenSecret should update value")
        void setTokenSecretShouldUpdateValue() {
            IOAuthFilterConfig config = new IOAuthFilterConfig();
            config.setTokenSecret("my-secret-key");
            assertThat(config.getTokenSecret()).isEqualTo("my-secret-key");
        }

        @Test
        @DisplayName("setDefaultTokenValidDuration should update value")
        void setDefaultTokenValidDurationShouldUpdateValue() {
            IOAuthFilterConfig config = new IOAuthFilterConfig();
            config.setDefaultTokenValidDuration(4);
            assertThat(config.getDefaultTokenValidDuration()).isEqualTo(4);
        }

        @Test
        @DisplayName("setDefaultRefreshTokenValidDuration should update value")
        void setDefaultRefreshTokenValidDurationShouldUpdateValue() {
            IOAuthFilterConfig config = new IOAuthFilterConfig();
            config.setDefaultRefreshTokenValidDuration(360);
            assertThat(config.getDefaultRefreshTokenValidDuration()).isEqualTo(360);
        }

        @Test
        @DisplayName("setRevokePreviousTokensOnIssue should update value")
        void setRevokePreviousTokensOnIssueShouldUpdateValue() {
            IOAuthFilterConfig config = new IOAuthFilterConfig();
            config.setRevokePreviousTokensOnIssue(true);
            assertThat(config.isRevokePreviousTokensOnIssue()).isTrue();
        }

        @Test
        @DisplayName("setEnableDailyIssueLimit should update value")
        void setEnableDailyIssueLimitShouldUpdateValue() {
            IOAuthFilterConfig config = new IOAuthFilterConfig();
            config.setEnableDailyIssueLimit(false);
            assertThat(config.isEnableDailyIssueLimit()).isFalse();
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            IOAuthFilterConfig config = new IOAuthFilterConfig();
            config.setEnable(true);
            config.setTokenSecret("secret");
            config.setDefaultTokenValidDuration(1);
            config.setDefaultRefreshTokenValidDuration(168);
            config.setRevokePreviousTokensOnIssue(true);
            config.setEnableDailyIssueLimit(false);

            assertThat(config.isEnable()).isTrue();
            assertThat(config.getTokenSecret()).isEqualTo("secret");
            assertThat(config.getDefaultTokenValidDuration()).isEqualTo(1);
            assertThat(config.getDefaultRefreshTokenValidDuration()).isEqualTo(168);
            assertThat(config.isRevokePreviousTokensOnIssue()).isTrue();
            assertThat(config.isEnableDailyIssueLimit()).isFalse();
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("two instances with same values should be equal")
        void twoInstancesWithSameValuesShouldBeEqual() {
            IOAuthFilterConfig a = new IOAuthFilterConfig();
            a.setEnable(true);
            a.setDefaultTokenValidDuration(4);
            IOAuthFilterConfig b = new IOAuthFilterConfig();
            b.setEnable(true);
            b.setDefaultTokenValidDuration(4);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two instances with different values should not be equal")
        void twoInstancesWithDifferentValuesShouldNotBeEqual() {
            IOAuthFilterConfig a = new IOAuthFilterConfig();
            a.setEnable(true);
            IOAuthFilterConfig b = new IOAuthFilterConfig();
            b.setEnable(false);

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString should contain field names")
        void toStringShouldContainFieldNames() {
            IOAuthFilterConfig config = new IOAuthFilterConfig();
            String str = config.toString();

            assertThat(str).contains("IOAuthFilterConfig");
            assertThat(str).contains("enable");
            assertThat(str).contains("tokenSecret");
            assertThat(str).contains("defaultTokenValidDuration");
        }
    }
}
