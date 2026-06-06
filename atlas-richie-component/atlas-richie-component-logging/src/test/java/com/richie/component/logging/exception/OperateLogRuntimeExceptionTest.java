package com.richie.component.logging.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperateLogRuntimeExceptionTest {

    @Test
    void constructors_retainMessageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        assertThat(new OperateLogRuntimeException()).isInstanceOf(RuntimeException.class);
        assertThat(new OperateLogRuntimeException("failed")).hasMessage("failed");
        assertThat(new OperateLogRuntimeException(cause)).hasCause(cause);
        assertThat(new OperateLogRuntimeException("failed", cause))
                .hasMessage("failed")
                .hasCause(cause);
    }
}
