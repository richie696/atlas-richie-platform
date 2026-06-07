package com.richie.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DuplicateSubmitConfig}.
 */
@DisplayName("DuplicateSubmitConfig")
class DuplicateSubmitConfigTest {

    @Nested
    @DisplayName("defaults")
    class DefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            DuplicateSubmitConfig config = new DuplicateSubmitConfig();
            assertThat(config.isEnabled()).isFalse();
            assertThat(config.getIncludePaths()).isNull();
            assertThat(config.getExcludePaths()).isNull();
            assertThat(config.getTimeWindow()).isEqualTo(3000L);
            assertThat(config.getCacheExpire()).isEqualTo(10000L);
            assertThat(config.isEnableBodyHash()).isTrue();
            assertThat(config.isEnableUserLevel()).isTrue();
            assertThat(config.isEnableIpLevel()).isTrue();
            assertThat(config.getErrorMessage()).isEqualTo("请求过于频繁，请稍后再试");
            assertThat(config.getErrorCode()).isEqualTo("DUPLICATE_SUBMIT");
        }
    }

    @Nested
    @DisplayName("setters")
    class SettersTest {

        @Test
        @DisplayName("setEnabled should update value")
        void setEnabledShouldUpdateValue() {
            DuplicateSubmitConfig config = new DuplicateSubmitConfig();
            config.setEnabled(true);
            assertThat(config.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("setIncludePaths should update value")
        void setIncludePathsShouldUpdateValue() {
            DuplicateSubmitConfig config = new DuplicateSubmitConfig();
            String[] paths = {"/api/**"};
            config.setIncludePaths(paths);
            assertThat(config.getIncludePaths()).isEqualTo(paths);
        }

        @Test
        @DisplayName("setExcludePaths should update value")
        void setExcludePathsShouldUpdateValue() {
            DuplicateSubmitConfig config = new DuplicateSubmitConfig();
            String[] paths = {"/actuator/**"};
            config.setExcludePaths(paths);
            assertThat(config.getExcludePaths()).isEqualTo(paths);
        }

        @Test
        @DisplayName("setTimeWindow should update value")
        void setTimeWindowShouldUpdateValue() {
            DuplicateSubmitConfig config = new DuplicateSubmitConfig();
            config.setTimeWindow(5000L);
            assertThat(config.getTimeWindow()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("setCacheExpire should update value")
        void setCacheExpireShouldUpdateValue() {
            DuplicateSubmitConfig config = new DuplicateSubmitConfig();
            config.setCacheExpire(20000L);
            assertThat(config.getCacheExpire()).isEqualTo(20000L);
        }

        @Test
        @DisplayName("setEnableBodyHash should update value")
        void setEnableBodyHashShouldUpdateValue() {
            DuplicateSubmitConfig config = new DuplicateSubmitConfig();
            config.setEnableBodyHash(false);
            assertThat(config.isEnableBodyHash()).isFalse();
        }

        @Test
        @DisplayName("setEnableUserLevel should update value")
        void setEnableUserLevelShouldUpdateValue() {
            DuplicateSubmitConfig config = new DuplicateSubmitConfig();
            config.setEnableUserLevel(false);
            assertThat(config.isEnableUserLevel()).isFalse();
        }

        @Test
        @DisplayName("setEnableIpLevel should update value")
        void setEnableIpLevelShouldUpdateValue() {
            DuplicateSubmitConfig config = new DuplicateSubmitConfig();
            config.setEnableIpLevel(false);
            assertThat(config.isEnableIpLevel()).isFalse();
        }

        @Test
        @DisplayName("setErrorMessage should update value")
        void setErrorMessageShouldUpdateValue() {
            DuplicateSubmitConfig config = new DuplicateSubmitConfig();
            config.setErrorMessage("Custom error");
            assertThat(config.getErrorMessage()).isEqualTo("Custom error");
        }

        @Test
        @DisplayName("setErrorCode should update value")
        void setErrorCodeShouldUpdateValue() {
            DuplicateSubmitConfig config = new DuplicateSubmitConfig();
            config.setErrorCode("CUSTOM_CODE");
            assertThat(config.getErrorCode()).isEqualTo("CUSTOM_CODE");
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            DuplicateSubmitConfig config = new DuplicateSubmitConfig();
            config.setEnabled(true);
            config.setIncludePaths(new String[]{"/api/submit/**"});
            config.setExcludePaths(new String[]{"/api/public/**"});
            config.setTimeWindow(6000L);
            config.setCacheExpire(18000L);
            config.setEnableBodyHash(false);
            config.setEnableUserLevel(false);
            config.setEnableIpLevel(false);
            config.setErrorMessage("Too many requests");
            config.setErrorCode("TOO_MANY_REQUESTS");

            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getIncludePaths()).containsExactly("/api/submit/**");
            assertThat(config.getExcludePaths()).containsExactly("/api/public/**");
            assertThat(config.getTimeWindow()).isEqualTo(6000L);
            assertThat(config.getCacheExpire()).isEqualTo(18000L);
            assertThat(config.isEnableBodyHash()).isFalse();
            assertThat(config.isEnableUserLevel()).isFalse();
            assertThat(config.isEnableIpLevel()).isFalse();
            assertThat(config.getErrorMessage()).isEqualTo("Too many requests");
            assertThat(config.getErrorCode()).isEqualTo("TOO_MANY_REQUESTS");
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("two instances with same values should be equal")
        void twoInstancesWithSameValuesShouldBeEqual() {
            DuplicateSubmitConfig a = new DuplicateSubmitConfig();
            a.setEnabled(true);
            a.setTimeWindow(5000L);
            DuplicateSubmitConfig b = new DuplicateSubmitConfig();
            b.setEnabled(true);
            b.setTimeWindow(5000L);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two instances with different values should not be equal")
        void twoInstancesWithDifferentValuesShouldNotBeEqual() {
            DuplicateSubmitConfig a = new DuplicateSubmitConfig();
            a.setEnabled(true);
            DuplicateSubmitConfig b = new DuplicateSubmitConfig();
            b.setEnabled(false);

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString should contain field names")
        void toStringShouldContainFieldNames() {
            DuplicateSubmitConfig config = new DuplicateSubmitConfig();
            String str = config.toString();

            assertThat(str).contains("DuplicateSubmitConfig");
            assertThat(str).contains("enabled");
            assertThat(str).contains("timeWindow");
            assertThat(str).contains("errorCode");
        }
    }
}
