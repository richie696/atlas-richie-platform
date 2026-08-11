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

import cn.richie696.component.mcp.server.tool.McpToolRefreshResult;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 验证 {@link McpSpringToolDefinitionRefresher} 的同步锁、绑定路径、异常包装与状态快照。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpSpringToolDefinitionRefresher 刷新器")
class McpSpringToolDefinitionRefresherTest {

    @Nested
    @DisplayName("构造校验")
    class Construction {

        @Test
        @DisplayName("任一参数为 null 都抛 NullPointerException")
        void nullArgsRejected() {
            McpToolRegistry registry = new McpToolRegistry();
            McpToolRegistrationAssembler assembler = mock(McpToolRegistrationAssembler.class);
            Environment environment = new MockEnvironment();
            McpServerProperties properties = new McpServerProperties();

            assertThatThrownBy(() -> new McpSpringToolDefinitionRefresher(null, assembler, environment, properties))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("registry");
            assertThatThrownBy(() -> new McpSpringToolDefinitionRefresher(registry, null, environment, properties))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("assembler");
            assertThatThrownBy(() -> new McpSpringToolDefinitionRefresher(registry, assembler, null, properties))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("environment");
            assertThatThrownBy(() -> new McpSpringToolDefinitionRefresher(registry, assembler, environment, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("initialProperties");
        }

        @Test
        @DisplayName("构造后初始状态为成功（successful=true, failureMessage=null），版本号与 registry 同步")
        void initialStatusSnapshot() {
            McpToolRegistry registry = new McpToolRegistry();
            long initialRevision = registry.revision();
            McpSpringToolDefinitionRefresher refresher = new McpSpringToolDefinitionRefresher(
                    registry, mock(McpToolRegistrationAssembler.class),
                    new MockEnvironment(), new McpServerProperties());

            McpToolRefreshStatus status = refresher.status();

            assertThat(status.successful()).isTrue();
            assertThat(status.registryRevision()).isEqualTo(initialRevision);
            assertThat(status.attemptedAt()).isNotNull();
            assertThat(status.failureMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("refresh() 成功路径")
    class RefreshSuccess {

        @Test
        @DisplayName("refresh 拿到绑定并调用 assembler.updateProperties + registry.replaceAll，返回结果与状态更新")
        void refreshRebindsAndReplaces() {
            MockEnvironment environment = new MockEnvironment();
            environment.setProperty("platform.component.mcp.server.path", "/mcp");
            McpToolRegistry registry = mock(McpToolRegistry.class);
            given(registry.revision()).willReturn(1L);
            McpToolRegistrationAssembler assembler = mock(McpToolRegistrationAssembler.class);
            McpToolRefreshResult result = new McpToolRefreshResult(
                    0, 1, Set.of("a"), Set.of(), Set.of());
            given(assembler.assemble()).willReturn(java.util.List.of());
            given(registry.replaceAll(any())).willReturn(result);
            McpSpringToolDefinitionRefresher refresher = new McpSpringToolDefinitionRefresher(
                    registry, assembler, environment, new McpServerProperties());

            McpToolRefreshResult returned = refresher.refresh();

            assertThat(returned).isSameAs(result);
            assertThat(refresher.status().successful()).isTrue();
            assertThat(refresher.status().registryRevision()).isEqualTo(result.newRevision());
            assertThat(refresher.status().failureMessage()).isNull();
            verify(assembler).updateProperties(any());
            verify(registry).replaceAll(any());
        }

        @Test
        @DisplayName("Binder.get 抛出 RuntimeException 时状态记为失败，异常原样抛出")
        void binderFailurePropagates() {
            McpToolRegistry registry = new McpToolRegistry();
            McpToolRegistrationAssembler assembler = mock(McpToolRegistrationAssembler.class);
            Environment environment = new MockEnvironment();
            McpSpringToolDefinitionRefresher refresher = new McpSpringToolDefinitionRefresher(
                    registry, assembler, environment, new McpServerProperties());

            try (MockedStatic<Binder> binder = mockStatic(Binder.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
                binder.when(() -> Binder.get(eq(environment)))
                        .thenThrow(new IllegalStateException("bind failed"));
                assertThatThrownBy(refresher::refresh)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("bind failed");

                assertThat(refresher.status().successful()).isFalse();
                assertThat(refresher.status().failureMessage()).isEqualTo("bind failed");
            }
        }

        @Test
        @DisplayName("Binder.get 抛出异常但 message 为 null 时 failureMessage 使用异常类名")
        void binderFailureNullMessageFallsBackToClassName() {
            McpToolRegistry registry = new McpToolRegistry();
            McpToolRegistrationAssembler assembler = mock(McpToolRegistrationAssembler.class);
            Environment environment = new MockEnvironment();
            McpSpringToolDefinitionRefresher refresher = new McpSpringToolDefinitionRefresher(
                    registry, assembler, environment, new McpServerProperties());

            try (MockedStatic<Binder> binder = mockStatic(Binder.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
                binder.when(() -> Binder.get(eq(environment)))
                        .thenThrow(new IllegalStateException());
                assertThatThrownBy(refresher::refresh)
                        .isInstanceOf(IllegalStateException.class);

                assertThat(refresher.status().failureMessage()).isEqualTo("IllegalStateException");
            }
        }

        @Test
        @DisplayName("assembler.updateProperties 失败时状态记为失败，异常原样抛出")
        void assemblerUpdateFailurePropagates() {
            McpToolRegistry registry = new McpToolRegistry();
            McpToolRegistrationAssembler assembler = mock(McpToolRegistrationAssembler.class);
            doThrow(new IllegalArgumentException("bad config"))
                    .when(assembler).updateProperties(any());
            Environment environment = new MockEnvironment();
            McpSpringToolDefinitionRefresher refresher = new McpSpringToolDefinitionRefresher(
                    registry, assembler, environment, new McpServerProperties());

            assertThatThrownBy(refresher::refresh)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("bad config");

            assertThat(refresher.status().successful()).isFalse();
            assertThat(refresher.status().failureMessage()).isEqualTo("bad config");
        }

        @Test
        @DisplayName("registry.replaceAll 失败时状态记为失败，异常原样抛出")
        void registryReplaceFailurePropagates() {
            McpToolRegistry registry = mock(McpToolRegistry.class);
            given(registry.revision()).willReturn(0L);
            McpToolRegistrationAssembler assembler = mock(McpToolRegistrationAssembler.class);
            given(assembler.assemble()).willReturn(java.util.List.of());
            doThrow(new IllegalStateException("replace failed"))
                    .when(registry).replaceAll(any());
            Environment environment = new MockEnvironment();
            McpSpringToolDefinitionRefresher refresher = new McpSpringToolDefinitionRefresher(
                    registry, assembler, environment, new McpServerProperties());

            assertThatThrownBy(refresher::refresh)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("replace failed");

            assertThat(refresher.status().successful()).isFalse();
            assertThat(refresher.status().failureMessage()).isEqualTo("replace failed");
        }
    }

    @Nested
    @DisplayName("refresh() 同步锁")
    class Synchronization {

        @Test
        @DisplayName("refresh() 进入时获取对象锁，并发调用串行化")
        void refreshSerializesConcurrentCalls() throws Exception {
            McpToolRegistry registry = new McpToolRegistry();
            McpToolRegistrationAssembler assembler = mock(McpToolRegistrationAssembler.class);
            given(assembler.assemble()).willReturn(java.util.List.of());
            McpSpringToolDefinitionRefresher refresher = new McpSpringToolDefinitionRefresher(
                    registry, assembler, new MockEnvironment(), new McpServerProperties());

            AtomicReference<Throwable> secondError = new AtomicReference<>();
            CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                try {
                    refresher.refresh();
                } catch (Throwable throwable) {
                    secondError.set(throwable);
                }
            });
            Thread.sleep(50);
            task.get();

            assertThat(secondError.get()).isNull();
            verify(assembler, times(1)).updateProperties(any());
        }
    }

    @Nested
    @DisplayName("Bindable / 协作")
    class CooperativeBinding {

        @Test
        @DisplayName("Binder.get(...).bind 返回 Optional.empty 时回退到 initialProperties")
        void emptyBindingFallsBackToInitialProperties() {
            MockEnvironment environment = new MockEnvironment();
            McpToolRegistry registry = new McpToolRegistry();
            McpToolRegistrationAssembler assembler = mock(McpToolRegistrationAssembler.class);
            McpServerProperties initial = new McpServerProperties();
            initial.setPath("/initial");
            McpSpringToolDefinitionRefresher refresher = new McpSpringToolDefinitionRefresher(
                    registry, assembler, environment, initial);

            org.springframework.boot.context.properties.bind.Binder mockedBinder =
                    mock(org.springframework.boot.context.properties.bind.Binder.class);
            @SuppressWarnings("unchecked")
            BindResult<McpServerProperties> emptyResult = mock(BindResult.class);
            given(emptyResult.orElse(any())).willAnswer(invocation -> {
                McpServerProperties fallback = invocation.getArgument(0);
                return fallback;
            });
            given(mockedBinder.bind(eq(McpServerProperties.PREFIX),
                    any(Bindable.class))).willReturn(emptyResult);

            try (MockedStatic<Binder> binder = mockStatic(Binder.class,
                    org.mockito.Mockito.CALLS_REAL_METHODS)) {
                binder.when(() -> Binder.get(eq(environment))).thenReturn(mockedBinder);
                refresher.refresh();
            }

            org.mockito.ArgumentCaptor<McpServerProperties> captor =
                    org.mockito.ArgumentCaptor.forClass(McpServerProperties.class);
            verify(assembler).updateProperties(captor.capture());
            assertThat(captor.getValue().getPath()).isEqualTo("/initial");
        }
    }
}
