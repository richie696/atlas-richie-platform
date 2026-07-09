/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.nats.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NATS 异常类单元测试。
 *
 * <p>验证异常构造、cause 链以及 {@link NatsRpcException} 工厂方法的语义区分。</p>
 */
class NatsExceptionTest {

    @Test
    void natsException_shouldHoldMessageAndCause() {
        Throwable cause = new IllegalStateException("upstream");
        NatsException ex = new NatsException("wrapped", cause);

        assertThat(ex.getMessage()).isEqualTo("wrapped");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void natsException_singleArg_shouldNotHaveCause() {
        NatsException ex = new NatsException("oops");
        assertThat(ex.getMessage()).isEqualTo("oops");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void natsConnectionException_shouldExtendNatsException() {
        Throwable cause = new java.io.IOException("connect failed");
        NatsConnectionException ex = new NatsConnectionException("connect error", cause);

        assertThat(ex).isInstanceOf(NatsException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("connect error");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void natsConnectionException_singleArg_shouldNotHaveCause() {
        NatsConnectionException ex = new NatsConnectionException("no conn");
        assertThat(ex.getMessage()).isEqualTo("no conn");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void natsSerializationException_shouldExtendNatsException() {
        Throwable cause = new com.fasterxml.jackson.core.JsonParseException(null, "bad json");
        NatsSerializationException ex = new NatsSerializationException("serialize failed", cause);

        assertThat(ex).isInstanceOf(NatsException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("serialize failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void natsRpcException_timeoutFactory_shouldMarkAsTimeout() {
        Throwable cause = new java.util.concurrent.TimeoutException("5s elapsed");
        NatsRpcException ex = NatsRpcException.timeout("rpc.subject", cause);

        assertThat(ex).isInstanceOf(NatsException.class);
        assertThat(ex.isTimeout()).isTrue();
        assertThat(ex.isNoResponders()).isFalse();
        assertThat(ex.getMessage()).contains("timed out").contains("rpc.subject");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void natsRpcException_noRespondersFactory_shouldMarkAsNoResponders() {
        Throwable cause = new RuntimeException("503 No Responders");
        NatsRpcException ex = NatsRpcException.noResponders("rpc.subject", cause);

        assertThat(ex.isTimeout()).isFalse();
        assertThat(ex.isNoResponders()).isTrue();
        assertThat(ex.getMessage()).contains("No responders").contains("rpc.subject");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void natsRpcException_otherFactory_shouldNotMarkAsEither() {
        Throwable cause = new RuntimeException("network error");
        NatsRpcException ex = NatsRpcException.other("rpc.subject", cause);

        assertThat(ex.isTimeout()).isFalse();
        assertThat(ex.isNoResponders()).isFalse();
        assertThat(ex.getMessage()).contains("RPC request failed").contains("rpc.subject");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void natsRpcException_explicitConstructor_shouldPreserveFlags() {
        Throwable cause = new RuntimeException("custom");
        NatsRpcException ex = new NatsRpcException("custom message", cause, false, true);

        assertThat(ex.getMessage()).isEqualTo("custom message");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.isTimeout()).isFalse();
        assertThat(ex.isNoResponders()).isTrue();
    }
}
