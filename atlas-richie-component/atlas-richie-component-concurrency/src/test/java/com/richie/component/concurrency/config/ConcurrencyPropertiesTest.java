/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.concurrency.config;

import com.richie.component.concurrency.config.properties.CircuitBreakerProperties;
import com.richie.component.concurrency.config.properties.RateLimiterProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConcurrencyProperties}.
 *
 * <p>Verifies that the {@code @ConfigurationProperties(prefix = "platform.concurrency")}
 * binding correctly resolves the nested {@link RateLimiterProperties} and
 * {@link CircuitBreakerProperties} groups from the documented YAML/property
 * namespace, and that all defaults match the documented contract.</p>
 *
 * <p>Uses Spring Boot's {@link ApplicationContextRunner} to drive a minimal
 * {@code ApplicationContext} that only registers {@link ConcurrencyProperties}
 * via {@link EnableConfigurationProperties}, keeping each scenario fast and isolated.</p>
 */
class ConcurrencyPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EnablePropertiesConfig.class);

    /**
     * Minimal configuration that triggers Spring Boot's
     * {@code @ConfigurationProperties} binding for {@link ConcurrencyProperties}.
     *
     * <p>The {@code @EnableConfigurationProperties} annotation causes Spring Boot
     * to instantiate the {@link ConcurrencyProperties} bean and populate every
     * field from the {@code Environment} via standard property binding rules.</p>
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ConcurrencyProperties.class)
    static class EnablePropertiesConfig {
    }

    // ========================================================================================
    // Class-level binding contract
    // ========================================================================================

    @Test
    @DisplayName("Class-level @ConfigurationProperties binds to 'platform.concurrency'")
    void testConfigurationPropertiesPrefix() {
        ConfigurationProperties annotation = ConcurrencyProperties.class.getAnnotation(ConfigurationProperties.class);

        assertThat(annotation)
                .as("ConcurrencyProperties must be annotated with @ConfigurationProperties")
                .isNotNull();
        assertThat(annotation.prefix())
                .as("prefix must be 'platform.concurrency' for YAML binding under platform.concurrency.*")
                .isEqualTo("platform.concurrency");
    }

    @Test
    @DisplayName("Default bean exposes both nested groups as non-null instances")
    void testNestedGroupsAreNonNullByDefault() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(ConcurrencyProperties.class);
            ConcurrencyProperties props = ctx.getBean(ConcurrencyProperties.class);

            assertThat(props.getRateLimiter())
                    .as("rateLimiter nested group must never be null so the field can be safely chained")
                    .isNotNull();
            assertThat(props.getCircuitBreaker())
                    .as("circuitBreaker nested group must never be null so the field can be safely chained")
                    .isNotNull();
        });
    }

    // ========================================================================================
    // RateLimiter defaults & binding
    // ========================================================================================

    @Nested
    @DisplayName("RateLimiter nested group")
    class RateLimiterDefaults {

        @Test
        @DisplayName("Defaults: enabled=false, permits-per-second=100")
        void testDefaults() {
            contextRunner.run(ctx -> {
                RateLimiterProperties rateLimiter =
                        ctx.getBean(ConcurrencyProperties.class).getRateLimiter();

                assertThat(rateLimiter.isEnabled())
                        .as("enabled must default to false — the rate limiter is opt-in to keep the bean footprint minimal")
                        .isFalse();
                assertThat(rateLimiter.getPermitsPerSecond())
                        .as("permitsPerSecond must default to 100 to match the historical hard-coded value")
                        .isEqualTo(100);
            });
        }

        @Test
        @DisplayName("platform.concurrency.rate-limiter.* properties override defaults")
        void testOverrides() {
            contextRunner
                    .withPropertyValues(
                            "platform.concurrency.rate-limiter.enabled=true",
                            "platform.concurrency.rate-limiter.permits-per-second=250")
                    .run(ctx -> {
                        RateLimiterProperties rateLimiter =
                                ctx.getBean(ConcurrencyProperties.class).getRateLimiter();

                        assertThat(rateLimiter.isEnabled()).isTrue();
                        assertThat(rateLimiter.getPermitsPerSecond()).isEqualTo(250);
                    });
        }

        @Test
        @DisplayName("Getters and setters reflect the values written by the setters")
        void testGettersAndSetters() {
            RateLimiterProperties rateLimiter = new RateLimiterProperties();

            rateLimiter.setEnabled(true);
            rateLimiter.setPermitsPerSecond(500);

            assertThat(rateLimiter.isEnabled()).isTrue();
            assertThat(rateLimiter.getPermitsPerSecond()).isEqualTo(500);
        }
    }

    // ========================================================================================
    // CircuitBreaker defaults & binding
    // ========================================================================================

    @Nested
    @DisplayName("CircuitBreaker nested group")
    class CircuitBreakerDefaults {

        @Test
        @DisplayName("Defaults: enabled=false, failure-rate-threshold=0.5, sliding-window-size=10, wait-duration=30s, half-open-max-successes=3")
        void testDefaults() {
            contextRunner.run(ctx -> {
                CircuitBreakerProperties cb =
                        ctx.getBean(ConcurrencyProperties.class).getCircuitBreaker();

                assertThat(cb.isEnabled())
                        .as("enabled must default to false — the circuit breaker is opt-in to avoid unexpected downstream impact")
                        .isFalse();
                assertThat(cb.getFailureRateThreshold())
                        .as("failureRateThreshold must default to 0.5 (50%%) — matches the historical hard-coded value")
                        .isEqualTo(0.5);
                assertThat(cb.getSlidingWindowSize())
                        .as("slidingWindowSize must default to 10 — the CircuitBreaker.Builder minimum")
                        .isEqualTo(10);
                assertThat(cb.getWaitDuration())
                        .as("waitDuration must default to 30 seconds — matches the historical hard-coded value")
                        .isEqualTo(Duration.ofSeconds(30));
                assertThat(cb.getHalfOpenMaxSuccesses())
                        .as("halfOpenMaxSuccesses must default to 3 — reserved for future multi-probe semantics")
                        .isEqualTo(3);
            });
        }

        @Test
        @DisplayName("platform.concurrency.circuit-breaker.* properties override defaults")
        void testOverrides() {
            contextRunner
                    .withPropertyValues(
                            "platform.concurrency.circuit-breaker.enabled=true",
                            "platform.concurrency.circuit-breaker.failure-rate-threshold=0.75",
                            "platform.concurrency.circuit-breaker.sliding-window-size=200",
                            "platform.concurrency.circuit-breaker.wait-duration=PT15S",
                            "platform.concurrency.circuit-breaker.half-open-max-successes=5")
                    .run(ctx -> {
                        CircuitBreakerProperties cb =
                                ctx.getBean(ConcurrencyProperties.class).getCircuitBreaker();

                        assertThat(cb.isEnabled()).isTrue();
                        assertThat(cb.getFailureRateThreshold()).isEqualTo(0.75);
                        assertThat(cb.getSlidingWindowSize()).isEqualTo(200);
                        assertThat(cb.getWaitDuration()).isEqualTo(Duration.ofSeconds(15));
                        assertThat(cb.getHalfOpenMaxSuccesses()).isEqualTo(5);
                    });
        }

        @Test
        @DisplayName("Getters and setters reflect the values written by the setters")
        void testGettersAndSetters() {
            CircuitBreakerProperties cb = new CircuitBreakerProperties();

            cb.setEnabled(true);
            cb.setFailureRateThreshold(0.8);
            cb.setSlidingWindowSize(50);
            cb.setWaitDuration(Duration.ofSeconds(60));
            cb.setHalfOpenMaxSuccesses(7);

            assertThat(cb.isEnabled()).isTrue();
            assertThat(cb.getFailureRateThreshold()).isEqualTo(0.8);
            assertThat(cb.getSlidingWindowSize()).isEqualTo(50);
            assertThat(cb.getWaitDuration()).isEqualTo(Duration.ofSeconds(60));
            assertThat(cb.getHalfOpenMaxSuccesses()).isEqualTo(7);
        }
    }

    // ========================================================================================
    // Cross-group independence
    // ========================================================================================

    @Test
    @DisplayName("Nested groups are independent: changing one does not affect the others")
    void testNestedGroupsAreIndependent() {
        contextRunner.run(ctx -> {
            ConcurrencyProperties props = ctx.getBean(ConcurrencyProperties.class);

            props.getRateLimiter().setPermitsPerSecond(777);
            props.getCircuitBreaker().setSlidingWindowSize(99);

            assertThat(props.getRateLimiter().isEnabled())
                    .as("changing rate-limiter.permits-per-second must not enable the rate limiter")
                    .isFalse();
            assertThat(props.getCircuitBreaker().getFailureRateThreshold())
                    .as("changing circuit-breaker.sliding-window-size must not change failure-rate-threshold")
                    .isEqualTo(0.5);
        });
    }
}
