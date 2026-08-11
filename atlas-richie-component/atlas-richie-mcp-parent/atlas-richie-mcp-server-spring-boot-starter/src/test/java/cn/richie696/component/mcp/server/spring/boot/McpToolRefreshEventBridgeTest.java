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

import cn.richie696.component.mcp.api.server.McpToolDefinitionChangeEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 验证 {@link McpToolRefreshEventBridge} 的事件类型过滤、EnvironmentChangeEvent 字段匹配、
 * 自定义 Payload 事件、异常降级与监听顺序。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpToolRefreshEventBridge 事件桥接")
class McpToolRefreshEventBridgeTest {

    @Nested
    @DisplayName("supportsEventType 过滤")
    class SupportsEventType {

        @Test
        @DisplayName("EnvironmentChangeEvent（按类名匹配）命中")
        void environmentChangeEventByClassName() {
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(null);
            assertThat(bridge.supportsEventType(EnvironmentChangeEvent.class)).isTrue();
        }

        @Test
        @DisplayName("PayloadApplicationEvent 子类命中")
        void payloadApplicationEventSubclass() {
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(null);
            assertThat(bridge.supportsEventType(PayloadApplicationEvent.class)).isTrue();
            assertThat(bridge.supportsEventType(MyPayloadEvent.class)).isTrue();
        }

        @Test
        @DisplayName("非匹配事件类型（其他 ApplicationEvent）不命中")
        void otherEventTypeNotSupported() {
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(null);
            assertThat(bridge.supportsEventType(ApplicationEvent.class)).isFalse();
            assertThat(bridge.supportsEventType(org.springframework.context.event.ContextRefreshedEvent.class)).isFalse();
        }

        @Test
        @DisplayName("null event type 不命中")
        void nullEventTypeNotSupported() {
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(null);
            assertThat(bridge.supportsEventType(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("getOrder 顺序")
    class GetOrder {

        @Test
        @DisplayName("getOrder 返回 Integer.MAX_VALUE - 100")
        void orderIsHighPriority() {
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(null);
            assertThat(bridge.getOrder()).isEqualTo(Integer.MAX_VALUE - 100);
        }
    }

    @Nested
    @DisplayName("onApplicationEvent 事件过滤")
    class OnApplicationEvent {

        @Test
        @DisplayName("EnvironmentChangeEvent 携带 MCP 工具 key 时触发 refresh")
        void triggersRefreshOnMatchingKeys() {
            McpSpringToolDefinitionRefresher refresher = mock(McpSpringToolDefinitionRefresher.class);
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(refresher);
            EnvironmentChangeEvent event = new EnvironmentChangeEvent(
                    Set.of(McpServerProperties.PREFIX + ".tools.definitions.foo"));

            bridge.onApplicationEvent(event);

            verify(refresher).refresh();
        }

        @Test
        @DisplayName("EnvironmentChangeEvent 携带非 MCP 工具 key 时不触发 refresh")
        void skipsNonMatchingKeys() {
            McpSpringToolDefinitionRefresher refresher = mock(McpSpringToolDefinitionRefresher.class);
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(refresher);
            EnvironmentChangeEvent event = new EnvironmentChangeEvent(Set.of("some.other.key"));

            bridge.onApplicationEvent(event);

            verify(refresher, never()).refresh();
        }

        @Test
        @DisplayName("EnvironmentChangeEvent 携带空 key 集合时按全部处理触发 refresh")
        void emptyKeysTriggersRefresh() {
            McpSpringToolDefinitionRefresher refresher = mock(McpSpringToolDefinitionRefresher.class);
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(refresher);
            EnvironmentChangeEvent event = new EnvironmentChangeEvent(Set.of());

            bridge.onApplicationEvent(event);

            verify(refresher).refresh();
        }

        @Test
        @DisplayName("PayloadApplicationEvent 携带 McpToolDefinitionChangeEvent 触发 refresh")
        void payloadWithToolChangeEventTriggersRefresh() {
            McpSpringToolDefinitionRefresher refresher = mock(McpSpringToolDefinitionRefresher.class);
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(refresher);
            PayloadApplicationEvent<McpToolDefinitionChangeEvent> event =
                    new PayloadApplicationEvent<>(this, new McpToolDefinitionChangeEvent(
                            "nacos", "v1", null));

            bridge.onApplicationEvent(event);

            verify(refresher).refresh();
        }

        @Test
        @DisplayName("PayloadApplicationEvent 携带其他类型 payload 不触发 refresh")
        void payloadWithOtherTypeDoesNotTrigger() {
            McpSpringToolDefinitionRefresher refresher = mock(McpSpringToolDefinitionRefresher.class);
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(refresher);
            PayloadApplicationEvent<String> event = new PayloadApplicationEvent<>(this, "random");

            bridge.onApplicationEvent(event);

            verify(refresher, never()).refresh();
        }

        @Test
        @DisplayName("非匹配事件类型不触发 refresh")
        void otherEventTypeIgnored() {
            McpSpringToolDefinitionRefresher refresher = mock(McpSpringToolDefinitionRefresher.class);
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(refresher);
            ApplicationEvent event = new ApplicationEvent(this) {};

            bridge.onApplicationEvent(event);

            verify(refresher, never()).refresh();
        }

        @Test
        @DisplayName("refresh 抛 RuntimeException 时被桥接器吞掉，事件链不中断")
        void runtimeExceptionSwallowedAndLogged() {
            McpSpringToolDefinitionRefresher refresher = mock(McpSpringToolDefinitionRefresher.class);
            doThrow(new IllegalStateException("bind failed")).when(refresher).refresh();
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(refresher);
            EnvironmentChangeEvent event = new EnvironmentChangeEvent(Set.of(
                    McpServerProperties.PREFIX + ".tools"));

            bridge.onApplicationEvent(event);

            verify(refresher).refresh();
        }

        @Test
        @DisplayName("refresher 为 null 时 MCP 工具 payload 事件不会 NPE（被 shouldRefresh 跳过）")
        void nullRefresherWithPayloadEvent() {
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(null);
            PayloadApplicationEvent<McpToolDefinitionChangeEvent> event =
                    new PayloadApplicationEvent<>(this, new McpToolDefinitionChangeEvent(
                            "source", "rev", null));

            bridge.onApplicationEvent(event);
        }

        @Test
        @DisplayName("refresher 为 null 且为 EnvironmentChangeEvent 时不抛错（被 shouldRefresh 跳过）")
        void nullRefresherWithEnvironmentChangeEvent() {
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(null);
            EnvironmentChangeEvent event = new EnvironmentChangeEvent(Set.of("any.key"));

            bridge.onApplicationEvent(event);
        }
    }

    @Nested
    @DisplayName("SmartApplicationListener 接口一致性")
    class ListenerInterface {

        @Test
        @DisplayName("实现 SmartApplicationListener")
        void implementsSmartApplicationListener() {
            McpToolRefreshEventBridge bridge = new McpToolRefreshEventBridge(null);
            assertThat(bridge).isInstanceOf(SmartApplicationListener.class);
        }
    }

    private static final class MyPayloadEvent extends PayloadApplicationEvent<String> {
        MyPayloadEvent() {
            super(new Object(), "payload");
        }
    }
}
