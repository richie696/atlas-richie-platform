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
package com.richie.component.nats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NatsConstants} 单元测试。
 *
 * <p>验证组件常量值：Header 命名空间、超时、追踪器名、Redis Key 前缀、MDC Key。</p>
 */
class NatsConstantsTest {

    @Test
    void headerKeys_shouldUseNatsNamespacePrefix() {
        assertThat(NatsConstants.HEADER_PREFIX).isEqualTo("nats-");
        assertThat(NatsConstants.HEADER_MESSAGE_ID).isEqualTo("nats-message-id");
        assertThat(NatsConstants.HEADER_TRACE_ID).isEqualTo("nats-trace-id");
        assertThat(NatsConstants.HEADER_SEND_TIME).isEqualTo("nats-send-time");
    }

    @Test
    void defaultTimeouts_shouldHaveProductionReasonableValues() {
        assertThat(NatsConstants.DEFAULT_RPC_TIMEOUT_MS).isEqualTo(5_000L);
        assertThat(NatsConstants.DEFAULT_IDEMPOTENT_TTL_MS).isEqualTo(120_000L);
        assertThat(NatsConstants.DEFAULT_DRAIN_TIMEOUT_SECONDS).isEqualTo(30L);
    }

    @Test
    void tracerMetadata_shouldIdentifyAtlasRichieNats() {
        assertThat(NatsConstants.TRACER_NAME).isEqualTo("atlas-richie-nats");
        assertThat(NatsConstants.TRACER_VERSION).isEqualTo("1.0.0");
    }

    @Test
    void idempotentKeyPrefix_shouldAvoidCollisionWithOtherComponents() {
        assertThat(NatsConstants.IDEMPOTENT_KEY_PREFIX).isEqualTo("nats:idempotent:");
    }

    @Test
    void mdcKeys_shouldMatchLoggingComponentConventions() {
        // MDC Key 通常与 logging 组件约定保持一致
        assertThat(NatsConstants.MDC_TRACE_ID).isEqualTo("traceId");
        assertThat(NatsConstants.MDC_SPAN_ID).isEqualTo("spanId");
    }

    @Test
    void headerMessageId_shouldBeDerivableFromPrefix() {
        // 防止重构后常量值错位
        assertThat(NatsConstants.HEADER_MESSAGE_ID)
                .startsWith(NatsConstants.HEADER_PREFIX)
                .endsWith("message-id");
        assertThat(NatsConstants.HEADER_TRACE_ID)
                .startsWith(NatsConstants.HEADER_PREFIX)
                .endsWith("trace-id");
        assertThat(NatsConstants.HEADER_SEND_TIME)
                .startsWith(NatsConstants.HEADER_PREFIX)
                .endsWith("send-time");
    }
}
