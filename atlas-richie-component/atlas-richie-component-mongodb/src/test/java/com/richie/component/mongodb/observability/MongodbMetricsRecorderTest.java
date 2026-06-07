package com.richie.component.mongodb.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MongodbMetricsRecorderTest {

    private MeterRegistry meterRegistry;
    private MongodbMetricsRecorder recorder;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        recorder = new MongodbMetricsRecorder(meterRegistry);
    }

    @Test
    void start_shouldReturnTimerSample() {
        Timer.Sample sample = recorder.start("find", "users");
        assertThat(sample).isNotNull();
    }

    @Test
    void stop_shouldRecordTimerWithCorrectTags() {
        Timer.Sample sample = recorder.start("find", "users");
        recorder.stop(sample, "find", "users", true);

        Timer timer = meterRegistry.find("mongodb.operation.duration")
                .tag("operation", "find")
                .tag("collection", "users")
                .tag("result", "success")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void stop_withError_shouldRecordWithErrorTag() {
        Timer.Sample sample = recorder.start("find", "users");
        recorder.stop(sample, "find", "users", false);

        Timer timer = meterRegistry.find("mongodb.operation.duration")
                .tag("operation", "find")
                .tag("collection", "users")
                .tag("result", "error")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void stop_shouldIncrementOperationCounter() {
        Timer.Sample sample = recorder.start("find", "users");
        recorder.stop(sample, "find", "users", true);

        var counter = meterRegistry.find("mongodb.operation.count")
                .tag("operation", "find")
                .tag("collection", "users")
                .tag("result", "success")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordError_shouldIncrementErrorCounter() {
        IllegalArgumentException ex = new IllegalArgumentException("test");
        recorder.recordError(ex);

        var counter = meterRegistry.find("mongodb.errors")
                .tag("error_type", "IllegalArgumentException")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void multipleOperations_shouldRecordCorrectCounts() {
        Timer.Sample sample1 = recorder.start("find", "users");
        recorder.stop(sample1, "find", "users", true);

        Timer.Sample sample2 = recorder.start("insert", "users");
        recorder.stop(sample2, "insert", "users", true);

        Timer.Sample sample3 = recorder.start("find", "users");
        recorder.stop(sample3, "find", "users", false);

        var successCounter = meterRegistry.find("mongodb.operation.count")
                .tag("result", "success")
                .counters().stream().mapToDouble(c -> c.count()).sum();
        var errorCounter = meterRegistry.find("mongodb.operation.count")
                .tag("result", "error")
                .counters().stream().mapToDouble(c -> c.count()).sum();

        assertThat(successCounter).isEqualTo(2.0);
        assertThat(errorCounter).isEqualTo(1.0);
    }
}
