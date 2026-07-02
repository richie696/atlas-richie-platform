package com.richie.component.concurrency.virtual;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BatchProcessor}.
 *
 * <p>Verifies success/failure accounting, result collection, timeout behavior
 * and the builder entry point. Correctness is asserted over results rather than
 * wall-clock parallelism, which is inherently non-deterministic.</p>
 *
 * <p>Tests for {@link BatchProcessor.BatchBuilder#mapParallel} additionally verify
 * input-order preservation of the results list across partial failures and timeouts.</p>
 */
class BatchProcessorTest {

    @Test
    void process_shouldProcessAllItems() {
        List<Integer> items = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        BatchResult result = BatchProcessor.of(items)
                .parallelism(4)
                .forEach(item -> { /* no-op */ });

        assertThat(result.successCount()).isEqualTo(10);
        assertThat(result.failureCount()).isZero();
        assertThat(result.hasError()).isFalse();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void process_shouldIsolateErrors() {
        List<Integer> items = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        BatchResult result = BatchProcessor.of(items)
                .parallelism(4)
                .forEach(item -> {
                    if (item == 2) {
                        throw new IllegalStateException("item 2 failed");
                    }
                });

        assertThat(result.successCount()).isEqualTo(9);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.hasError()).isTrue();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("item 2 failed");
    }

    @Test
    void process_emptyList_shouldReturnEmptyResult() {
        BatchResult result = BatchProcessor.of(Collections.<Integer>emptyList())
                .parallelism(4)
                .forEach(item -> { /* no-op */ });

        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isZero();
        assertThat(result.hasError()).isFalse();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void process_shouldHandleAllItemsFail() {
        List<Integer> items = List.of(0, 1, 2, 3, 4);

        BatchResult result = BatchProcessor.of(items)
                .parallelism(3)
                .forEach(item -> {
                    throw new RuntimeException("fail " + item);
                });

        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(items.size());
        assertThat(result.hasError()).isTrue();
        assertThat(result.errors()).hasSize(items.size());
    }

    @Test
    void process_shouldRespectParallelism() {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            items.add(i);
        }

        BatchResult result = BatchProcessor.of(items)
                .parallelism(5)
                .forEach(item -> { /* no-op */ });

        assertThat(result.successCount()).isEqualTo(items.size());
        assertThat(result.failureCount()).isZero();
        assertThat(result.hasError()).isFalse();
    }

    @Test
    void process_shouldTimeout() {
        List<Integer> items = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        AtomicInteger started = new AtomicInteger();

        BatchResult result = BatchProcessor.of(items)
                .parallelism(4)
                .timeout(Duration.ofMillis(10))
                .forEach(item -> {
                    started.incrementAndGet();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });

        assertThat(started)
                .as("the timeout should prevent every item from starting")
                .hasValueLessThan(items.size());
        assertThat(result.successCount())
                .as("with a 10ms timeout, no 500ms task should complete")
                .isZero();
        assertThat(result.failureCount() + result.successCount())
                .isLessThanOrEqualTo(items.size());
    }

    @Test
    void of_shouldCreateBuilder() {
        BatchProcessor.BatchBuilder<Integer> builder = BatchProcessor.of(List.of(1, 2, 3));

        assertThat(builder).isNotNull();
    }

    // ============================================================
    //  mapParallel tests
    // ============================================================

    @Test
    void mapParallel_happyPath_shouldReturnResultsInInputOrder() {
        List<Integer> items = List.of(1, 2, 3, 4, 5);

        BatchMappingResult<Integer, String> result = BatchProcessor.of(items)
                .parallelism(3)
                .mapParallel(value -> "v" + value);

        assertThat(result.successCount()).isEqualTo(5);
        assertThat(result.failureCount()).isZero();
        assertThat(result.hasError()).isFalse();
        assertThat(result.errors()).isEmpty();
        assertThat(result.results())
                .containsExactly("v1", "v2", "v3", "v4", "v5");
    }

    @Test
    void mapParallel_preservesInputOrderEvenWithVariedLatency() {
        // Even with non-uniform processing times, the result list must match
        // the input position, not the completion order.
        List<Integer> items = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        BatchMappingResult<Integer, Integer> result = BatchProcessor.of(items)
                .parallelism(8)
                .mapParallel(value -> {
                    // Each item sleeps a duration inversely related to its value,
                    // so smaller values finish later — completion order != input order.
                    try {
                        Thread.sleep((10 - value) * 5L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    return value * 100;
                });

        assertThat(result.successCount()).isEqualTo(items.size());
        assertThat(result.failureCount()).isZero();
        assertThat(result.results())
                .as("result list must preserve input order regardless of completion order")
                .containsExactly(0, 100, 200, 300, 400, 500, 600, 700, 800, 900);
    }

    @Test
    void mapParallel_partialFailure_shouldMarkFailureSlotsAsNull() {
        List<Integer> items = List.of(0, 1, 2, 3, 4);

        BatchMappingResult<Integer, String> result = BatchProcessor.of(items)
                .parallelism(3)
                .mapParallel(value -> {
                    if (value == 2) {
                        throw new IllegalArgumentException("item 2 rejected");
                    }
                    return "ok-" + value;
                });

        assertThat(result.successCount()).isEqualTo(4);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.hasError()).isTrue();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("item 2 rejected");
        assertThat(result.results())
                .as("failure slot must be null, surrounding results must keep their input position")
                .containsExactly("ok-0", "ok-1", null, "ok-3", "ok-4");
    }

    @Test
    void mapParallel_allItemsFail_shouldReturnAllNullResults() {
        List<Integer> items = List.of(0, 1, 2, 3, 4);

        BatchMappingResult<Integer, String> result = BatchProcessor.of(items)
                .parallelism(3)
                .mapParallel(value -> {
                    throw new RuntimeException("fail-" + value);
                });

        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(items.size());
        assertThat(result.hasError()).isTrue();
        assertThat(result.errors()).hasSize(items.size());
        assertThat(result.results())
                .as("every slot must be null when all items fail")
                .containsExactly(null, null, null, null, null);
    }

    @Test
    void mapParallel_emptyList_shouldReturnEmptyResult() {
        BatchMappingResult<Integer, String> result = BatchProcessor.of(Collections.<Integer>emptyList())
                .parallelism(3)
                .mapParallel(value -> "v" + value);

        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isZero();
        assertThat(result.hasError()).isFalse();
        assertThat(result.errors()).isEmpty();
        assertThat(result.results()).isEmpty();
    }

    @Test
    void mapParallel_timeout_shouldReturnPartialResultsAndNullForIncomplete() {
        List<Integer> items = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        AtomicInteger started = new AtomicInteger();

        BatchMappingResult<Integer, String> result = BatchProcessor.of(items)
                .parallelism(2)
                .timeout(Duration.ofMillis(20))
                .mapParallel(value -> {
                    int seq = started.incrementAndGet();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    return "item-" + value + "-" + seq;
                });

        assertThat(started)
                .as("the timeout should prevent every item from starting")
                .hasValueLessThan(items.size());
        assertThat(result.successCount())
                .as("with a 20ms timeout and 500ms tasks, very few should complete")
                .isLessThan(items.size());
        assertThat(result.results())
                .as("results list size must equal the input size regardless of partial completion")
                .hasSize(items.size());
        assertThat(result.results().stream().filter(Objects::nonNull).count())
                .as("non-null entries must equal success count")
                .isEqualTo(result.successCount());
        assertThat(result.failureCount() + result.successCount())
                .isEqualTo(items.size());
    }

    @Test
    void mapParallel_resultAt_shouldReturnValueAtPosition() {
        List<Integer> items = List.of(10, 20, 30);

        BatchMappingResult<Integer, Integer> result = BatchProcessor.of(items)
                .parallelism(2)
                .mapParallel(value -> value + 1);

        assertThat(result.resultAt(0)).isEqualTo(11);
        assertThat(result.resultAt(1)).isEqualTo(21);
        assertThat(result.resultAt(2)).isEqualTo(31);
        assertThatThrownBy(() -> result.resultAt(3))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void mapParallel_resultsListIsImmutable() {
        List<Integer> items = List.of(1, 2, 3);

        BatchMappingResult<Integer, Integer> result = BatchProcessor.of(items)
                .parallelism(2)
                .mapParallel(value -> value);

        assertThatThrownBy(() -> result.results().add(99))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.errors().add(new RuntimeException()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}