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
package cn.richie696.component.mcp.server.spring.boot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpToolRefreshStatus} record 访问器与不可变语义。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpToolRefreshStatus 状态快照")
class McpToolRefreshStatusTest {

    @Nested
    @DisplayName("accessor")
    class Accessor {

        @Test
        @DisplayName("四个 record component 透传")
        void componentsPassThrough() {
            Instant now = Instant.now();
            McpToolRefreshStatus status = new McpToolRefreshStatus(true, 7L, now, null);

            assertThat(status.successful()).isTrue();
            assertThat(status.registryRevision()).isEqualTo(7L);
            assertThat(status.attemptedAt()).isEqualTo(now);
            assertThat(status.failureMessage()).isNull();
        }

        @Test
        @DisplayName("失败状态携带 failureMessage 摘要")
        void failureStateCarriesMessage() {
            Instant now = Instant.now();
            McpToolRefreshStatus status = new McpToolRefreshStatus(false, 3L, now, "bind failed");

            assertThat(status.successful()).isFalse();
            assertThat(status.failureMessage()).isEqualTo("bind failed");
        }

        @Test
        @DisplayName("record 相等性按全部 component 比较")
        void equalityBasedOnAllComponents() {
            Instant now = Instant.parse("2026-08-11T00:00:00Z");
            McpToolRefreshStatus a = new McpToolRefreshStatus(true, 1L, now, null);
            McpToolRefreshStatus b = new McpToolRefreshStatus(true, 1L, now, null);
            McpToolRefreshStatus c = new McpToolRefreshStatus(false, 1L, now, null);

            assertThat(a).isEqualTo(b);
            assertThat(a).isNotEqualTo(c);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }
}
