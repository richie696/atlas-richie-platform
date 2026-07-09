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

import java.util.Set;

/**
 * HotReload 注册表（README.md §4.6）。
 * <p>
 * 业务拦截器实现 {@link Reloadable} 后注册到本表，配置变更触发 reload：
 * <ol>
 *   <li>业务方接 Nacos / Spring Cloud Config 时，调 {@link #reload(String)} 或
 *       {@link #reloadAll()}</li>
 *   <li>Registry 调 {@link Reloadable#accept(Object)} 替换状态</li>
 *   <li>（可选）通过 HookBus 发布 {@link ReloadEvent}</li>
 * </ol>
 *
 * <h2>不依赖 Spring Cloud</h2>
 * <p>web-core 不引入 spring-cloud 依赖；本表只提供"被调"接口，业务方自行接配置中心。
 */
public interface HotReloadRegistry {

    /**
     * 注册一个可热替换对象。
     */
    void register(String name, Reloadable<?> reloadable);

    /**
     * 取消注册。
     */
    void unregister(String name);

    /**
     * 获取当前注册的 Reloadable。
     */
    Reloadable<?> get(String name);

    /**
     * 全部已注册名（用于诊断 / reloadAll）。
     */
    Set<String> names();

    /**
     * 重载单个对象。返回 true 表示成功。
     */
    boolean reload(String name);

    /**
     * 重载全部（用于配置中心全量刷新场景）。
     *
     * @return 成功重载的数量
     */
    int reloadAll();
}