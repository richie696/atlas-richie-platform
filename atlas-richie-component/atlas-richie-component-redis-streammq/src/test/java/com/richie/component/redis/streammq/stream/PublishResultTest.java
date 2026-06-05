package com.richie.component.redis.streammq.stream;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import static org.assertj.core.api.Assertions.assertThat;

class PublishResultTest {

    @Test
    void successFactory_setsSuccessFlag() {
        PublishResult<String> result = PublishResult.success("event");

        assertThat(result.success()).isTrue();
        assertThat(result.event()).isEqualTo("event");
        assertThat(result.failureReason()).isNull();
        assertThat(result.error()).isNull();
    }

    @Test
    void failedFactory_setsFailureDetails() {
        RuntimeException error = new RuntimeException("boom");
        PublishResult<String> result = PublishResult.failed("event", PublishFailureReason.EXCEPTION, error);

        assertThat(result.success()).isFalse();
        assertThat(result.event()).isEqualTo("event");
        assertThat(result.failureReason()).isEqualTo(PublishFailureReason.EXCEPTION);
        assertThat(result.error()).isSameAs(error);
    }

    @Test
    void fromEmit_mapsReactorResults() {
        assertThat(PublishResult.fromEmit(Sinks.EmitResult.OK, "ok", "test").success()).isTrue();
        assertThat(PublishResult.fromEmit(Sinks.EmitResult.FAIL_OVERFLOW, "x", "test").failureReason())
                .isEqualTo(PublishFailureReason.BUFFER_OVERFLOW);
        assertThat(PublishResult.fromEmit(Sinks.EmitResult.FAIL_TERMINATED, "x", "test").failureReason())
                .isEqualTo(PublishFailureReason.SINK_TERMINATED);
        assertThat(PublishResult.fromEmit(Sinks.EmitResult.FAIL_CANCELLED, "x", "test").failureReason())
                .isEqualTo(PublishFailureReason.OPERATION_CANCELLED);
        assertThat(PublishResult.fromEmit(Sinks.EmitResult.FAIL_NON_SERIALIZED, "x", "test").failureReason())
                .isEqualTo(PublishFailureReason.NON_SERIALIZED_ACCESS);
    }
}
