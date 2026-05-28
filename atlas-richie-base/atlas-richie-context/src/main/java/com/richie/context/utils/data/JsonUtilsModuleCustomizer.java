package com.richie.context.utils.data;

import tools.jackson.databind.JacksonModule;

import java.util.List;

/**
 * JsonUtils Jackson Module 扩展点。
 * <p>
 * 各组件可通过 Spring Bean 提供实现，由自动配置统一收集并注册到 {@link JsonUtils}。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
@FunctionalInterface
public interface JsonUtilsModuleCustomizer {

    /**
     * 提供需要注册到 JsonUtils 的 Jackson 模块。
     *
     * @return 模块列表
     */
    List<JacksonModule> modules();
}

