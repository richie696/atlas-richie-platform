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
package com.richie.component.web.core.reload;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Spring Cloud Config {@code EnvironmentChangeEvent} 桥接器（README.md §4.6 增量补全）。
 * <p>
 * <strong>设计原则</strong>：web-core 不引入 spring-cloud 依赖。
 * 当下游业务方引入 {@code spring-cloud-context}（典型通过 {@code spring-cloud-starter-config}）
 * 时，本桥接器自动激活：监听 {@code EnvironmentChangeEvent} 触发 {@link HotReloadRegistry#reloadAll()}。
 * <p>
 * 使用 {@link SmartApplicationListener} 而非 {@code ApplicationListener<ApplicationEvent>}，
 * 通过 {@link #supportsEventType(Class)} 在分发层完成过滤，避免每个 ApplicationEvent 都进入本类
 * 浪费 CPU。
 *
 * <h2>工作机制</h2>
 * <ol>
 *   <li>{@link #supportsEventType(Class)} 判断事件类名匹配
 *       {@value #ENVIRONMENT_CHANGE_EVENT_CLASS}</li>
 *   <li>匹配后反射调 {@code getKeys()} 获取变更 key 集合</li>
 *   <li>任一 key 以 {@link #KEY_PREFIX} 开头或 keys 为 null/empty → 全量 reload</li>
 * </ol>
 *
 * <h2>失败隔离</h2>
 * <p>反射失败仅 warn，不抛异常，避免污染 Spring 事件总线。
 *
 * @author richie696
 * @since 2026-07
 */
public class HotReloadCloudBridge implements SmartApplicationListener {

    private static final Logger log = LoggerFactory.getLogger(HotReloadCloudBridge.class);

    /**
     * Spring Cloud {@code EnvironmentChangeEvent} 全限定类名（不引入编译期依赖）。
     */
    public static final String ENVIRONMENT_CHANGE_EVENT_CLASS =
            "org.springframework.cloud.context.environment.EnvironmentChangeEvent";

    /**
     * 当变更的任一 key 以此前缀开头时，认为与 richie 防护体系相关。
     * <p>示例：{@code richie.web.rate-limit.permits-per-second=20} 触发 reload。
     */
    public static final String KEY_PREFIX = "richie.web.";

    private final HotReloadRegistry registry;

    public HotReloadCloudBridge(HotReloadRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        if (eventType == null) {
            return false;
        }
        return ENVIRONMENT_CHANGE_EVENT_CLASS.equals(eventType.getName());
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 100;
    }

    @Override
    public void onApplicationEvent(@Nullable ApplicationEvent event) {
        if (event == null) {
            log.debug("HotReloadCloudBridge received null event, ignoring");
            return;
        }
        Set<String> keys = extractKeys(event);
        if (log.isDebugEnabled()) {
            log.debug("HotReloadCloudBridge received EnvironmentChangeEvent: keys={}", keys);
        }
        if (shouldReload(keys)) {
            int count = registry.reloadAll();
            log.info("HotReloadCloudBridge triggered reloadAll (keys={}): reloaded={}", keys, count);
        }
    }

    /**
     * 反射提取 {@code EnvironmentChangeEvent.getKeys()}（避免编译期依赖 spring-cloud-context）。
     *
     * @return keys；失败时返回 null（视为整体变更，需 reload）
     */
    @SuppressWarnings("unchecked")
    static Set<String> extractKeys(ApplicationEvent event) {
        try {
            Method getKeys = event.getClass().getMethod("getKeys");
            Object result = getKeys.invoke(event);
            if (result instanceof Set) {
                return (Set<String>) result;
            }
            return null;
        } catch (ReflectiveOperationException e) {
            log.warn("HotReloadCloudBridge: cannot reflect getKeys() from {}: {}",
                    event.getClass().getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 决策是否触发 reload：
     * <ul>
     *   <li>keys 为 null（反射失败）：保守触发 reload</li>
     *   <li>keys 为空：保守触发 reload（通常意味着任意 key）</li>
     *   <li>任一 key 以 {@link #KEY_PREFIX} 开头：触发 reload</li>
     *   <li>其它：忽略（与本组件无关）</li>
     * </ul>
     */
    static boolean shouldReload(Set<String> keys) {
        if (keys == null) {
            return true;
        }
        if (keys.isEmpty()) {
            return true;
        }
        for (String key : keys) {
            if (key != null && key.startsWith(KEY_PREFIX)) {
                return true;
            }
        }
        return false;
    }
}