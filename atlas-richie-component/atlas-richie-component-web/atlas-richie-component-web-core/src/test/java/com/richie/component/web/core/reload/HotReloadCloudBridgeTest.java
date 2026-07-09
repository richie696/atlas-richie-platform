/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.reload;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HotReloadCloudBridge} 测试（不依赖 spring-cloud-context）。
 * <p>
 * 由于 {@code EnvironmentChangeEvent} 类不在 classpath，本测试用代理事件类（同名）
 * 反射验证 {@link HotReloadCloudBridge#extractKeys} 与 {@link HotReloadCloudBridge#shouldReload} 的纯逻辑。
 */
class HotReloadCloudBridgeTest {

    private DefaultHotReloadRegistry registry;
    private HotReloadCloudBridge bridge;

    @BeforeEach
    void setUp() {
        registry = new DefaultHotReloadRegistry();
        bridge = new HotReloadCloudBridge(registry);
        registry.register("config", new Reloadable<String>() {
            @Override public String currentState() { return "v1"; }
            @Override public void accept(String newState) {}
            @Override public String name() { return "config"; }
        });
    }

    @Test
    void supportsEventType_realSpringCloudEventType_returnsTrue_byName() {
        assertThat(HotReloadCloudBridge.ENVIRONMENT_CHANGE_EVENT_CLASS)
                .isEqualTo("org.springframework.cloud.context.environment.EnvironmentChangeEvent");
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
    void supportsSourceType_any() {
        assertThat(bridge.supportsSourceType(String.class)).isTrue();
        assertThat(bridge.supportsSourceType(null)).isTrue();
    }

    @Test
    void onApplicationEvent_relevantKeys_directInvocationTriggersReload() {
        Set<String> keys = new HashSet<>();
        keys.add("richie.web.rate-limit.permits-per-second");
        ProxyEnvironmentChangeEvent event = new ProxyEnvironmentChangeEvent(keys);

        bridge.onApplicationEvent(event);

        assertThat(registry.names()).containsExactly("config");
    }

    @Test
    void onApplicationEvent_unrelatedKeys_directInvocationSkipped() {
        Set<String> keys = new HashSet<>();
        keys.add("spring.datasource.url");
        ProxyEnvironmentChangeEvent event = new ProxyEnvironmentChangeEvent(keys);

        bridge.onApplicationEvent(event);
        assertThat(registry.names()).containsExactly("config");
    }

    @Test
    void shouldReload_nullKeys_conservative() {
        assertThat(HotReloadCloudBridge.shouldReload(null)).isTrue();
    }

    @Test
    void shouldReload_emptyKeys_conservative() {
        assertThat(HotReloadCloudBridge.shouldReload(Set.of())).isTrue();
    }

    @Test
    void shouldReload_relevantPrefix_triggers() {
        assertThat(HotReloadCloudBridge.shouldReload(Set.of("richie.web.circuit-breaker.failures"))).isTrue();
        assertThat(HotReloadCloudBridge.shouldReload(Set.of("spring.x", "richie.web.y"))).isTrue();
    }

    @Test
    void shouldReload_unrelatedKeys_skips() {
        assertThat(HotReloadCloudBridge.shouldReload(Set.of("spring.datasource.url", "spring.rabbitmq.host"))).isFalse();
    }

    @Test
    void extractKeys_reflectsGetKeys() {
        Set<String> keys = Set.of("richie.web.x", "richie.web.y");
        ProxyEnvironmentChangeEvent event = new ProxyEnvironmentChangeEvent(keys);
        Set<String> extracted = HotReloadCloudBridge.extractKeys(event);
        assertThat(extracted).containsExactlyInAnyOrderElementsOf(keys);
    }

    @Test
    void extractKeys_unrelatedEvent_returnsNull() {
        ApplicationEvent event = new ApplicationEvent("source") {};
        Set<String> extracted = HotReloadCloudBridge.extractKeys(event);
        assertThat(extracted).isNull();
    }

    @Test
    void onApplicationEvent_nullEvent_isNoOp() {
        bridge.onApplicationEvent(null);
        assertThat(registry.names()).containsExactly("config");
    }

    /**
     * 模拟 spring-cloud-context {@code EnvironmentChangeEvent} 的代理事件。
     * <p>本类拥有 {@code public Set<String> getKeys()} 方法（与 spring-cloud 同名），
     * 类名<strong>不</strong>匹配
     * {@link HotReloadCloudBridge#ENVIRONMENT_CHANGE_EVENT_CLASS}，因此
     * {@code supportsEventType} 返回 false；测试通过直接调 {@code onApplicationEvent} 验证
     * extractKeys / shouldReload 逻辑。
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