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
package com.richie.component.ai.config;

import com.richie.component.ai.service.impl.AiMultimodalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link AiMultimodalCloudBridge} 测试（不依赖 spring-cloud-context）。
 * <p>
 * 由于 {@code EnvironmentChangeEvent} 类不在 ai 组件的 classpath，本测试用代理事件类（同名）
 * 反射验证 {@link AiMultimodalCloudBridge#extractKeys} 与
 * {@link AiMultimodalCloudBridge#shouldReload} 的纯逻辑，并通过 Mockito 验证
 * {@link AiMultimodalServiceImpl#refresh()} 的触发时机。
 * <p>
 * 设计镜像 {@code com.richie.component.web.core.reload.HotReloadCloudBridgeTest}。
 */
class AiMultimodalCloudBridgeTest {

    private AiMultimodalServiceImpl multimodalService;
    private AiMultimodalCloudBridge bridge;

    @BeforeEach
    void setUp() {
        multimodalService = mock(AiMultimodalServiceImpl.class);
        bridge = new AiMultimodalCloudBridge(multimodalService);
    }

    @Test
    void environmentChangeEventClass_matchesSpringCloudConstant() {
        assertThat(AiMultimodalCloudBridge.ENVIRONMENT_CHANGE_EVENT_CLASS)
                .isEqualTo("org.springframework.cloud.context.environment.EnvironmentChangeEvent");
    }

    @Test
    void keyPrefix_targetsPlatformComponentAiNamespace() {
        assertThat(AiMultimodalCloudBridge.KEY_PREFIX).isEqualTo("platform.component.ai.");
    }

    @Test
    void supportsEventType_unrelatedClass_returnsFalse() {
        assertThat(bridge.supportsEventType(ApplicationEvent.class)).isFalse();
        assertThat(bridge.supportsEventType(ProxyEnvironmentChangeEvent.class)).isFalse();
    }

    @Test
    void supportsEventType_nullSafe() {
        assertThat(bridge.supportsEventType(null)).isFalse();
    }

    @Test
    void supportsSourceType_acceptsAny() {
        assertThat(bridge.supportsSourceType(String.class)).isTrue();
        assertThat(bridge.supportsSourceType(null)).isTrue();
    }

    @Test
    void onApplicationEvent_rerankKeyChanged_triggersRefresh() {
        Set<String> keys = new HashSet<>();
        keys.add("platform.component.ai.rerank.bailian.api-key");
        bridge.onApplicationEvent(new ProxyEnvironmentChangeEvent(keys));

        verify(multimodalService, times(1)).refresh();
    }

    @Test
    void onApplicationEvent_imageKeyChanged_triggersRefresh() {
        Set<String> keys = new HashSet<>();
        keys.add("platform.component.ai.image.wanx.model");
        bridge.onApplicationEvent(new ProxyEnvironmentChangeEvent(keys));

        verify(multimodalService, times(1)).refresh();
    }

    @Test
    void onApplicationEvent_ttsAndSttKeysChanged_triggersRefresh() {
        Set<String> keys = new HashSet<>();
        keys.add("platform.component.ai.tts.hunyuan.app-id");
        keys.add("platform.component.ai.stt.pangu.appcode");
        bridge.onApplicationEvent(new ProxyEnvironmentChangeEvent(keys));

        verify(multimodalService, times(1)).refresh();
    }

    @Test
    void onApplicationEvent_mixedRelevantAndUnrelevant_triggersRefresh() {
        Set<String> keys = new HashSet<>();
        keys.add("spring.datasource.url");
        keys.add("platform.component.ai.rerank.bailian.api-key");
        keys.add("logging.level.root");
        bridge.onApplicationEvent(new ProxyEnvironmentChangeEvent(keys));

        verify(multimodalService, times(1)).refresh();
    }

    @Test
    void onApplicationEvent_unrelatedKeys_doesNotTriggerRefresh() {
        Set<String> keys = new HashSet<>();
        keys.add("spring.datasource.url");
        keys.add("spring.rabbitmq.host");
        keys.add("logging.level.root");
        bridge.onApplicationEvent(new ProxyEnvironmentChangeEvent(keys));

        verifyNoInteractions(multimodalService);
    }

    @Test
    void onApplicationEvent_emptyKeys_conservativeTriggersRefresh() {
        bridge.onApplicationEvent(new ProxyEnvironmentChangeEvent(Set.of()));

        verify(multimodalService, times(1)).refresh();
    }

    @Test
    void onApplicationEvent_nullKeys_conservativeTriggersRefresh() {
        // ProxyEvent with null keys means reflection-like failure: be conservative.
        bridge.onApplicationEvent(new ProxyEnvironmentChangeEvent(null));

        verify(multimodalService, times(1)).refresh();
    }

    @Test
    void onApplicationEvent_nullEvent_isNoOp() {
        bridge.onApplicationEvent(null);

        verifyNoInteractions(multimodalService);
    }

    @Test
    void onApplicationEvent_refreshThrows_isIsolatedAndDoesNotPropagate() {
        org.mockito.Mockito.doThrow(new RuntimeException("simulated failure"))
                .when(multimodalService).refresh();

        Set<String> keys = new HashSet<>();
        keys.add("platform.component.ai.rerank.bailian.api-key");

        // refresh() 内已有 vendor-level try-catch，此处模拟"意外抛出"路径。
        // 桥接器须捕获并 WARN，不得污染 Spring 事件总线。
        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> bridge.onApplicationEvent(new ProxyEnvironmentChangeEvent(keys)));

        verify(multimodalService, times(1)).refresh();
    }

    @Test
    void shouldReload_nullKeys_conservative() {
        assertThat(AiMultimodalCloudBridge.shouldReload(null)).isTrue();
    }

    @Test
    void shouldReload_emptyKeys_conservative() {
        assertThat(AiMultimodalCloudBridge.shouldReload(Set.of())).isTrue();
    }

    @Test
    void shouldReload_relevantPrefix_triggers() {
        assertThat(AiMultimodalCloudBridge.shouldReload(
                Set.of("platform.component.ai.rerank.bailian"))).isTrue();
        assertThat(AiMultimodalCloudBridge.shouldReload(
                Set.of("platform.component.ai.image.foo", "platform.component.ai.tts.bar"))).isTrue();
        assertThat(AiMultimodalCloudBridge.shouldReload(
                Set.of("spring.x", "platform.component.ai.stt.pangu"))).isTrue();
    }

    @Test
    void shouldReload_unrelatedKeys_skips() {
        assertThat(AiMultimodalCloudBridge.shouldReload(
                Set.of("spring.datasource.url", "spring.rabbitmq.host"))).isFalse();
        assertThat(AiMultimodalCloudBridge.shouldReload(
                Set.of("richie.web.rate-limit.permits-per-second"))).isFalse();
    }

    @Test
    void extractKeys_reflectsGetKeys() {
        Set<String> keys = Set.of("platform.component.ai.rerank.x", "platform.component.ai.image.y");
        ProxyEnvironmentChangeEvent event = new ProxyEnvironmentChangeEvent(keys);
        Set<String> extracted = AiMultimodalCloudBridge.extractKeys(event);
        assertThat(extracted).containsExactlyInAnyOrderElementsOf(keys);
    }

    @Test
    void extractKeys_unrelatedEvent_returnsNull() {
        ApplicationEvent event = new ApplicationEvent("source") {};
        Set<String> extracted = AiMultimodalCloudBridge.extractKeys(event);
        assertThat(extracted).isNull();
    }

    /**
     * 模拟 spring-cloud-context {@code EnvironmentChangeEvent} 的代理事件。
     * <p>本类拥有 {@code public Set<String> getKeys()} 方法（与 spring-cloud 同名），
     * 类名<strong>不</strong>匹配
     * {@link AiMultimodalCloudBridge#ENVIRONMENT_CHANGE_EVENT_CLASS}，因此
     * {@code supportsEventType} 返回 false；测试通过直接调 {@code onApplicationEvent}
     * 验证 extractKeys / shouldReload / refresh() 触发逻辑。
     */
    public static final class ProxyEnvironmentChangeEvent extends ApplicationEvent {
        private static final long serialVersionUID = 1L;
        private final Set<String> keys;

        public ProxyEnvironmentChangeEvent(Set<String> keys) {
            super("proxy-source");
            this.keys = keys;
        }

        public Set<String> getKeys() {
            return keys;
        }
    }
}