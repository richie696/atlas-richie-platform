package com.richie.component.concurrency.algorithm;

import com.richie.component.concurrency.algorithm.Retryer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Retryer}.
 *
 * <p>Verifies retry behavior, fallback handling, exception filtering,
 * backoff bounds and the non-instantiable utility contract.
 */
class RetryerTest {

    @Test
    void execute_shouldSucceedOnFirstAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        String result = Retryer.of(Duration.ofMillis(1))
                .maxAttempts(3)
                .jitter(false)
                .execute(() -> {
                    attempts.incrementAndGet();
                    return "ok";
                });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void execute_shouldRetryAndSucceed() {
        AtomicInteger attempts = new AtomicInteger();

        String result = Retryer.of(Duration.ofMillis(1))
                .maxAttempts(5)
                .jitter(false)
                .execute(() -> {
                    int n = attempts.incrementAndGet();
                    if (n < 3) {
                        throw new IllegalStateException("transient failure " + n);
                    }
                    return "recovered";
                });

        assertThat(result).isEqualTo("recovered");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void execute_shouldThrowAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> Retryer.of(Duration.ofMillis(1))
                .maxAttempts(3)
                .jitter(false)
                .execute((Callable<String>) () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("always fails");
                }))
                .isInstanceOf(RetryExhaustedException.class)
                .hasMessageContaining("always fails");

        assertThat(attempts).hasValue(3);
    }

    @Test
    void execute_withFallback_shouldReturnFallback() {
        AtomicInteger attempts = new AtomicInteger();

        String result = Retryer.of(Duration.ofMillis(1))
                .maxAttempts(3)
                .jitter(false)
                .execute(
                        () -> {
                            attempts.incrementAndGet();
                            throw new IllegalStateException("boom");
                        },
                        "default");

        assertThat(result).isEqualTo("default");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void execute_withFallback_onInterrupt_shouldReturnFallback() {
        AtomicInteger attempts = new AtomicInteger();
        Thread.currentThread().interrupt();

        try {
            String result = Retryer.of(Duration.ofMillis(1))
                    .maxAttempts(3)
                    .jitter(false)
                    .execute(
                            () -> {
                                attempts.incrementAndGet();
                                return "never";
                            },
                            "fallback-value");

            assertThat(result).isEqualTo("fallback-value");
            assertThat(attempts)
                    .as("interrupt should short-circuit the retry loop")
                    .hasValueLessThan(3);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void execute_shouldNotRetryOnNonMatchingException() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> Retryer.of(Duration.ofMillis(1))
                .maxAttempts(5)
                .jitter(false)
                .retryOn(IOException.class)
                .execute((Callable<String>) () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("non-retryable");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("non-retryable");

        assertThat(attempts).hasValue(1);
    }

    @Test
    void execute_shouldRetryOnMatchingException() {
        AtomicInteger attempts = new AtomicInteger();

        String result = Retryer.of(Duration.ofMillis(1))
                .maxAttempts(4)
                .jitter(false)
                .retryOn(IOException.class)
                .execute(() -> {
                    int n = attempts.incrementAndGet();
                    if (n < 2) {
                        throw new IOException("transient io " + n);
                    }
                    return "ok";
                });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void execute_runnable_shouldComplete() {
        AtomicInteger attempts = new AtomicInteger();

        Retryer.of(Duration.ofMillis(1))
                .maxAttempts(3)
                .jitter(false)
                .execute((Runnable) () -> {
                    int n = attempts.incrementAndGet();
                    if (n < 2) {
                        throw new IllegalStateException("retry me");
                    }
                });

        assertThat(attempts).hasValue(2);
    }

    @Test
    void execute_shouldRespectMaxBackoff() {
        long start = System.nanoTime();

        assertThatThrownBy(() -> Retryer.of(Duration.ofMillis(1))
                .maxAttempts(5)
                .jitter(false)
                .maxBackoff(Duration.ofMillis(5))
                .execute((Runnable) () -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(RetryExhaustedException.class);

        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertThat(elapsedMillis).isLessThan(100L);
    }

    @Test
    void constructor_shouldNotBeInstantiable() throws NoSuchMethodException {
        Constructor<Retryer> constructor = Retryer.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers()))
                .as("Retryer constructor must be private")
                .isTrue();
    }
}
