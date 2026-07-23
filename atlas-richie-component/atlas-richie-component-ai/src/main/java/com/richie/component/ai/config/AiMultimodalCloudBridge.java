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

import com.richie.component.ai.service.AiMultimodalService;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Spring Cloud Config {@code EnvironmentChangeEvent} 桥接器 —— 多模态自动刷新。
 * <p>
 * 镜像 {@code com.richie.component.web.core.reload.HotReloadCloudBridge} 的设计：
 * ai 组件不引入 spring-cloud 编译期依赖；当业务方引入
 * {@code spring-cloud-context}（典型通过 {@code spring-cloud-starter-config} /
 * Nacos Config / Apollo）时，本桥接器自动激活 —— 监听
 * {@code EnvironmentChangeEvent}，任一变更 key 以 {@link #KEY_PREFIX}
 * 开头时触发 {@link AiMultimodalService#refresh()}，从
 * {@link AiModelProperties} 重新构建 Rerank / Image / TTS / STT 四维模型。
 * <p>
 * 与 Chat 端的"运行时 {@code initializeModels(...)} 触发 reload"不同：多模态侧的
 * 全部状态都从 {@link AiModelProperties} 派生，{@code refresh()} 是幂等的；
 * 因此监听 {@code EnvironmentChangeEvent} 即可实现"配置变更 → 自动重建"。
 *
 * <h2>工作机制</h2>
 * <ol>
 *   <li>{@link #supportsEventType(Class)} 通过类名匹配
 *       {@value #ENVIRONMENT_CHANGE_EVENT_CLASS}（避免编译期依赖 spring-cloud）</li>
 *   <li>匹配后反射调 {@code getKeys()} 获取变更 key 集合</li>
 *   <li>任一 key 以 {@link #KEY_PREFIX} 开头或 keys 为 null/empty →
 *       触发 {@code aiMultimodalService.refresh()}</li>
 * </ol>
 *
 * <h2>未引入 spring-cloud 时的行为</h2>
 * <p>本类由 {@link AiModelAutoConfiguration} 通过
 * {@code @ConditionalOnClass(name = ENVIRONMENT_CHANGE_EVENT_CLASS)} 控制装配；
 * 未引入时 {@link AiMultimodalService#refresh()} 仍可通过业务方手动调用、
 * {@code AiModelAutoConfiguration.aiMultimodalService(...)} 启动期调用、
 * 或未来通过其他事件源（自定义 ApplicationEvent / Nacos 客户端回调）触发。
 *
 * <h2>失败隔离</h2>
 * <p>反射失败仅 warn，不抛异常，避免污染 Spring 事件总线。
 *
 * @author richie696
 * @since 1.0.0
 */
public class AiMultimodalCloudBridge implements SmartApplicationListener {

    private static final Logger log = LoggerFactory.getLogger(AiMultimodalCloudBridge.class);

    /**
     * Spring Cloud {@code EnvironmentChangeEvent} 全限定类名（不引入编译期依赖）。
     * <p>与 {@code HotReloadCloudBridge.ENVIRONMENT_CHANGE_EVENT_CLASS} 同源：
     * spring-cloud-context 启动器（Nacos Config / Apollo / Spring Cloud Config 客户端）
     * 均会发布此事件。
     */
    public static final String ENVIRONMENT_CHANGE_EVENT_CLASS =
            "org.springframework.cloud.context.environment.EnvironmentChangeEvent";

    /**
     * 当变更的任一 key 以此前缀开头时，认为与多模态配置相关。
     * <p>覆盖范围：{@code platform.component.ai.rerank.<key>} /
     * {@code platform.component.ai.image.<key>} /
     * {@code platform.component.ai.tts.<key>} /
     * {@code platform.component.ai.stt.<key>} —— 任一变更即触发多模态四维
     * {@link AiMultimodalService#refresh()} 重建。
     * <p>注意：宽匹配会把 {@code platform.component.ai.models.*} /
     * {@code platform.component.ai.routing.*} 等 Chat 侧变更也一并纳入；
     * 但 {@code refresh()} 幂等无副作用，且多模态 Map 不会被 Chat 模型变更污染，
     * 代价仅是一次额外的轻量重建，故此处不细化到四维独立前缀。
     */
    public static final String KEY_PREFIX = "platform.component.ai.";

    private final AiMultimodalService aiMultimodalService;

    public AiMultimodalCloudBridge(AiMultimodalService aiMultimodalService) {
        this.aiMultimodalService = aiMultimodalService;
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
        // 与 HotReloadCloudBridge 对齐：晚于业务 @EventListener 触发，避免抢占常规事件链路。
        return Integer.MAX_VALUE - 100;
    }

    @Override
    public void onApplicationEvent(@Nullable ApplicationEvent event) {
        if (event == null) {
            log.debug("AiMultimodalCloudBridge received null event, ignoring");
            return;
        }
        Set<String> keys = extractKeys(event);
        if (log.isDebugEnabled()) {
            log.debug("AiMultimodalCloudBridge received EnvironmentChangeEvent: keys={}", keys);
        }
        if (shouldReload(keys)) {
            try {
                aiMultimodalService.refresh();
                log.info("AiMultimodalCloudBridge triggered multimodal refresh (keys={})", keys);
            } catch (Exception ex) {
                // refresh() 已内部 try-catch 单条 vendor，理论上不会抛出；此处仅为双保险。
                log.warn("AiMultimodalCloudBridge refresh failed (keys={}): {}", keys, ex.getMessage());
            }
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
            log.warn("AiMultimodalCloudBridge: cannot reflect getKeys() from {}: {}",
                    event.getClass().getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 决策是否触发多模态 refresh：
     * <ul>
     *   <li>keys 为 null（反射失败）：保守触发 refresh</li>
     *   <li>keys 为空：保守触发 refresh（通常意味着任意 key）</li>
     *   <li>任一 key 以 {@link #KEY_PREFIX} 开头：触发 refresh</li>
     *   <li>其它：忽略（与多模态配置无关）</li>
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