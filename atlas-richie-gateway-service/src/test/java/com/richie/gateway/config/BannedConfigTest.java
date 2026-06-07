package com.richie.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BannedConfig}.
 */
@DisplayName("BannedConfig")
class BannedConfigTest {

    @Nested
    @DisplayName("defaults")
    class DefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            BannedConfig config = new BannedConfig();
            assertThat(config.getSecurityBlockTimeUnit()).isEqualTo(TimeUnit.HOURS);
            assertThat(config.getSecurityBlockTime()).isEqualTo(1);
            assertThat(config.getPermanent()).isFalse();
            assertThat(config.getPermanentPath()).isEqualTo("platform:gateway:security:permanent");
        }
    }

    @Nested
    @DisplayName("setters")
    class SettersTest {

        @Test
        @DisplayName("setSecurityBlockTimeUnit should update value")
        void setSecurityBlockTimeUnitShouldUpdateValue() {
            BannedConfig config = new BannedConfig();
            config.setSecurityBlockTimeUnit(TimeUnit.DAYS);
            assertThat(config.getSecurityBlockTimeUnit()).isEqualTo(TimeUnit.DAYS);
        }

        @Test
        @DisplayName("setSecurityBlockTime should update value")
        void setSecurityBlockTimeShouldUpdateValue() {
            BannedConfig config = new BannedConfig();
            config.setSecurityBlockTime(24);
            assertThat(config.getSecurityBlockTime()).isEqualTo(24);
        }

        @Test
        @DisplayName("setPermanent should update value")
        void setPermanentShouldUpdateValue() {
            BannedConfig config = new BannedConfig();
            config.setPermanent(true);
            assertThat(config.getPermanent()).isTrue();
        }

        @Test
        @DisplayName("setPermanentPath should update value")
        void setPermanentPathShouldUpdateValue() {
            BannedConfig config = new BannedConfig();
            config.setPermanentPath("custom:path");
            assertThat(config.getPermanentPath()).isEqualTo("custom:path");
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            BannedConfig config = new BannedConfig();
            config.setSecurityBlockTimeUnit(TimeUnit.MINUTES);
            config.setSecurityBlockTime(30);
            config.setPermanent(true);
            config.setPermanentPath("custom:permanent:path");

            assertThat(config.getSecurityBlockTimeUnit()).isEqualTo(TimeUnit.MINUTES);
            assertThat(config.getSecurityBlockTime()).isEqualTo(30);
            assertThat(config.getPermanent()).isTrue();
            assertThat(config.getPermanentPath()).isEqualTo("custom:permanent:path");
        }
    }

    @Nested
    @DisplayName("getSecurityBlockTimeMillis")
    class GetSecurityBlockTimeMillisTest {

        @Test
        @DisplayName("should compute correct millis from HOURS unit")
        void shouldComputeCorrectMillisFromHoursUnit() {
            BannedConfig config = new BannedConfig();
            config.setSecurityBlockTimeUnit(TimeUnit.HOURS);
            config.setSecurityBlockTime(2);
            // 2 hours = 2 * 60 * 60 * 1000 = 7200000 ms
            assertThat(config.getSecurityBlockTimeMillis()).isEqualTo(7200000L);
        }

        @Test
        @DisplayName("should compute correct millis from MINUTES unit")
        void shouldComputeCorrectMillisFromMinutesUnit() {
            BannedConfig config = new BannedConfig();
            config.setSecurityBlockTimeUnit(TimeUnit.MINUTES);
            config.setSecurityBlockTime(30);
            // 30 minutes = 30 * 60 * 1000 = 1800000 ms
            assertThat(config.getSecurityBlockTimeMillis()).isEqualTo(1800000L);
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("two instances with same values should be equal")
        void twoInstancesWithSameValuesShouldBeEqual() {
            BannedConfig a = new BannedConfig();
            a.setSecurityBlockTime(5);
            a.setPermanent(true);
            BannedConfig b = new BannedConfig();
            b.setSecurityBlockTime(5);
            b.setPermanent(true);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two instances with different values should not be equal")
        void twoInstancesWithDifferentValuesShouldNotBeEqual() {
            BannedConfig a = new BannedConfig();
            a.setPermanent(true);
            BannedConfig b = new BannedConfig();
            b.setPermanent(false);

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString should contain field names")
        void toStringShouldContainFieldNames() {
            BannedConfig config = new BannedConfig();
            String str = config.toString();

            assertThat(str).contains("BannedConfig");
            assertThat(str).contains("securityBlockTimeUnit");
            assertThat(str).contains("securityBlockTime");
            assertThat(str).contains("permanent");
        }
    }
}
