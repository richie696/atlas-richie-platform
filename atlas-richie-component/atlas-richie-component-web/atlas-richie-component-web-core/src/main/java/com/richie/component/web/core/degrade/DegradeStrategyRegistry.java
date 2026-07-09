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
package com.richie.component.web.core.degrade;

import java.util.List;
import java.util.Optional;

/**
 * 降级策略注册表（README.md §4.7）。
 * <p>
 * 注册表负责：
 * <ol>
 *   <li>{@link #register(String, DegradeStrategy) register}：业务方注入策略</li>
 *   <li>{@link #select(Trigger) select}：按 {@code order} 升序返回首个匹配策略</li>
 *   <li>{@link #all() all}：调试 / 诊断 / 配置 dump</li>
 * </ol>
 *
 * <p><strong>线程安全</strong>：注册表实现必须支持并发注册与查询（典型用 {@code CopyOnWriteArrayList}）。
 *
 * @author richie696
 * @since 2026-07
 */
public interface DegradeStrategyRegistry {

    /**
     * 注册一个策略。同名覆盖。
     */
    void register(String name, DegradeStrategy strategy);

    /**
     * 取消注册。
     */
    void unregister(String name);

    /**
     * 按触发条件选择首个匹配策略。
     *
     * @return 命中策略；无命中返回 {@link Optional#empty()}
     */
    Optional<DegradeStrategy> select(Trigger trigger);

    /**
     * 获取所有已注册策略（按 {@code order} 升序；同名按注册先后），用于诊断。
     */
    List<DegradeStrategy> all();
}