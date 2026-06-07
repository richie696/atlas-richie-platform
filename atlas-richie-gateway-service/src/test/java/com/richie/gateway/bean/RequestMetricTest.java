package com.richie.gateway.bean;

import com.richie.gateway.config.SecurityFilterConfig;
import com.richie.gateway.enums.SecurityRuleEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RequestMetric Tests")
class RequestMetricTest {

    @Nested
    @DisplayName("Default Constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should have default values")
        void shouldHaveDefaultValues() {
            RequestMetric metric = new RequestMetric();
            assertThat(metric.getIp()).isNull();
            assertThat(metric.getTime()).isEqualTo(0L);
            assertThat(metric.getCount()).isEqualTo(0);
            assertThat(metric.getRule()).isNull();
            assertThat(metric.getBlockTime()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter and Getter")
    class SetterAndGetter {

        @Test
        @DisplayName("should set and get ip correctly")
        void shouldSetAndGetIpCorrectly() {
            RequestMetric metric = new RequestMetric();
            metric.setIp("192.168.1.100");
            assertThat(metric.getIp()).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("should set and get time correctly")
        void shouldSetAndGetTimeCorrectly() {
            RequestMetric metric = new RequestMetric();
            metric.setTime(1234567890L);
            assertThat(metric.getTime()).isEqualTo(1234567890L);
        }

        @Test
        @DisplayName("should set and get count correctly")
        void shouldSetAndGetCountCorrectly() {
            RequestMetric metric = new RequestMetric();
            metric.setCount(42);
            assertThat(metric.getCount()).isEqualTo(42);
        }

        @Test
        @DisplayName("should set and get rule correctly")
        void shouldSetAndGetRuleCorrectly() {
            RequestMetric metric = new RequestMetric();
            metric.setRule(SecurityRuleEnum.BANNED_IP);
            assertThat(metric.getRule()).isEqualTo(SecurityRuleEnum.BANNED_IP);
        }

        @Test
        @DisplayName("should set and get blockTime correctly")
        void shouldSetAndGetBlockTimeCorrectly() {
            RequestMetric metric = new RequestMetric();
            Date blockTime = new Date();
            metric.setBlockTime(blockTime);
            assertThat(metric.getBlockTime()).isEqualTo(blockTime);
        }
    }

    @Nested
    @DisplayName("Chainable Setters")
    class ChainableSetters {

        @Test
        @DisplayName("should support chainable setters")
        void shouldSupportChainableSetters() {
            RequestMetric metric = new RequestMetric()
                    .setIp("10.0.0.1")
                    .setTime(1000L)
                    .setCount(5)
                    .setRule(SecurityRuleEnum.REDIRECT)
                    .setBlockTime(new Date());

            assertThat(metric.getIp()).isEqualTo("10.0.0.1");
            assertThat(metric.getTime()).isEqualTo(1000L);
            assertThat(metric.getCount()).isEqualTo(5);
            assertThat(metric.getRule()).isEqualTo(SecurityRuleEnum.REDIRECT);
            assertThat(metric.getBlockTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Add Count Method")
    class AddCountMethod {

        @Test
        @DisplayName("should increment count starting from 0")
        void shouldIncrementCountStartingFrom0() {
            RequestMetric metric = new RequestMetric();
            assertThat(metric.getCount()).isEqualTo(0);

            int result = metric.addCount();
            assertThat(result).isEqualTo(1);
            assertThat(metric.getCount()).isEqualTo(1);

            result = metric.addCount();
            assertThat(result).isEqualTo(2);
            assertThat(metric.getCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Reset Time Method")
    class ResetTimeMethod {

        @Test
        @DisplayName("should reset time and count when time interval exceeded")
        void shouldResetTimeAndCountWhenTimeIntervalExceeded() {
            RequestMetric metric = new RequestMetric();
            metric.setTime(0L);
            metric.setCount(10);

            SecurityFilterConfig config = new SecurityFilterConfig();
            config.setSecurityTimeIntervalUnit(TimeUnit.MINUTES);
            config.setSecurityTimeIntervalValue(1);

            metric.resetTime(config);

            assertThat(metric.getTime()).isGreaterThan(0L);
            assertThat(metric.getCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should not reset time and count when within time interval")
        void shouldNotResetTimeAndCountWhenWithinTimeInterval() {
            RequestMetric metric = new RequestMetric();
            long currentTime = System.currentTimeMillis();
            metric.setTime(currentTime);
            metric.setCount(5);

            SecurityFilterConfig config = new SecurityFilterConfig();
            config.setSecurityTimeIntervalUnit(TimeUnit.MINUTES);
            config.setSecurityTimeIntervalValue(10);

            long originalTime = metric.getTime();
            int originalCount = metric.getCount();

            metric.resetTime(config);

            assertThat(metric.getTime()).isEqualTo(originalTime);
            assertThat(metric.getCount()).isEqualTo(originalCount);
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal to another RequestMetric with same values")
        void shouldBeEqualToAnotherRequestMetricWithSameValues() {
            RequestMetric m1 = new RequestMetric();
            m1.setIp("ip");
            m1.setTime(100L);
            m1.setCount(1);
            m1.setRule(SecurityRuleEnum.BANNED_IP);

            RequestMetric m2 = new RequestMetric();
            m2.setIp("ip");
            m2.setTime(100L);
            m2.setCount(1);
            m2.setRule(SecurityRuleEnum.BANNED_IP);

            assertThat(m1).isEqualTo(m2);
            assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
        }

        @Test
        @DisplayName("should not be equal to another RequestMetric with different values")
        void shouldNotBeEqualToAnotherRequestMetricWithDifferentValues() {
            RequestMetric m1 = new RequestMetric();
            m1.setIp("ip1");

            RequestMetric m2 = new RequestMetric();
            m2.setIp("ip2");

            assertThat(m1).isNotEqualTo(m2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("should contain all fields in toString output")
        void shouldContainAllFieldsInToStringOutput() {
            RequestMetric metric = new RequestMetric();
            metric.setIp("192.168.1.1");
            metric.setTime(1000L);
            metric.setCount(5);
            metric.setRule(SecurityRuleEnum.CUSTOM_HTTP_STATUS);

            String str = metric.toString();
            assertThat(str).contains("ip");
            assertThat(str).contains("time");
            assertThat(str).contains("count");
            assertThat(str).contains("rule");
        }
    }
}
