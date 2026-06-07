package com.richie.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HardwareFingerprintConfig}.
 */
@DisplayName("HardwareFingerprintConfig")
class HardwareFingerprintConfigTest {

    @Nested
    @DisplayName("defaults")
    class DefaultsTest {

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaultValues() {
            HardwareFingerprintConfig config = new HardwareFingerprintConfig();
            assertThat(config.getHmacSecret()).isEqualTo("default-secret-key-change-in-production");
            assertThat(config.getTimestampValidDuration()).isEqualTo(300L);
        }
    }

    @Nested
    @DisplayName("setters")
    class SettersTest {

        @Test
        @DisplayName("setHmacSecret should update value")
        void setHmacSecretShouldUpdateValue() {
            HardwareFingerprintConfig config = new HardwareFingerprintConfig();
            config.setHmacSecret("my-secret-key");
            assertThat(config.getHmacSecret()).isEqualTo("my-secret-key");
        }

        @Test
        @DisplayName("setTimestampValidDuration should update value")
        void setTimestampValidDurationShouldUpdateValue() {
            HardwareFingerprintConfig config = new HardwareFingerprintConfig();
            config.setTimestampValidDuration(600L);
            assertThat(config.getTimestampValidDuration()).isEqualTo(600L);
        }

        @Test
        @DisplayName("all setters should work correctly")
        void allSettersShouldWorkCorrectly() {
            HardwareFingerprintConfig config = new HardwareFingerprintConfig();
            config.setHmacSecret("custom-secret");
            config.setTimestampValidDuration(120L);

            assertThat(config.getHmacSecret()).isEqualTo("custom-secret");
            assertThat(config.getTimestampValidDuration()).isEqualTo(120L);
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsHashCodeTest {

        @Test
        @DisplayName("two instances with same values should be equal")
        void twoInstancesWithSameValuesShouldBeEqual() {
            HardwareFingerprintConfig a = new HardwareFingerprintConfig();
            a.setHmacSecret("key").setTimestampValidDuration(300L);
            HardwareFingerprintConfig b = new HardwareFingerprintConfig();
            b.setHmacSecret("key").setTimestampValidDuration(300L);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two instances with different values should not be equal")
        void twoInstancesWithDifferentValuesShouldNotBeEqual() {
            HardwareFingerprintConfig a = new HardwareFingerprintConfig();
            a.setHmacSecret("key-a");
            HardwareFingerprintConfig b = new HardwareFingerprintConfig();
            b.setHmacSecret("key-b");

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString should contain field names")
        void toStringShouldContainFieldNames() {
            HardwareFingerprintConfig config = new HardwareFingerprintConfig();
            String str = config.toString();

            assertThat(str).contains("HardwareFingerprintConfig");
            assertThat(str).contains("hmacSecret");
            assertThat(str).contains("timestampValidDuration");
        }
    }

    @Nested
    @DisplayName("Lombok @Data and @Accessors")
    class LombokTest {

        @Test
        @DisplayName("@Data with chain=true should enable fluent setters")
        void dataWithChainShouldEnableFluentSetters() {
            HardwareFingerprintConfig config = new HardwareFingerprintConfig();
            // @Accessors(chain = true) allows fluent chaining
            HardwareFingerprintConfig result = config.setHmacSecret("test").setTimestampValidDuration(500L);

            assertThat(result).isSameAs(config);
            assertThat(config.getHmacSecret()).isEqualTo("test");
            assertThat(config.getTimestampValidDuration()).isEqualTo(500L);
        }
    }
}
