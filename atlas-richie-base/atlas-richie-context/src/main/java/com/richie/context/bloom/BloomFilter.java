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
package com.richie.context.bloom;

import java.util.Set;

/**
 * 布隆过滤器 SPI。
 * <p>
 * 用于"前置预校验"：HTTP 拦截器在请求早期读取业务写入的 attribute，调用
 * {@link #mightContain(String)} 判断目标资源是否存在——不存在的请求直接短路拒绝，
 * 避免下游 DB / 缓存 / 业务逻辑被无效请求打穿。
 *
 * <h2>默认实现</h2>
 * <p>本包提供基于 Guava 的 {@link GuavaBloomFilter}（单体部署，开箱即用）。
 * 分布式部署需用户实现本接口（如基于 Redis bitmap / RedissonBloomFilter）并注册为 Spring bean，
 * web-core 的默认实现会自动让位（{@code @ConditionalOnMissingBean}）。
 *
 * <h2>使用约束</h2>
 * <ul>
 *   <li>本接口只定义"读/写"语义；底层初始化（预热、扩容）由实现方负责</li>
 *   <li>{@link #mightContain(String)} 允许误判（false positive），但不允许漏报（false negative 概率为 0）</li>
 *   <li>拦截器装配时会先调 {@link #isExists()}：未初始化的实现应返回 {@code false} 以跳过预校验，
 *       避免误杀线上请求</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
public interface BloomFilter {

    /**
     * 判断 key 是否可能存在。
     * <p>返回 {@code true} 表示"可能存在"（拦截器放行）；
     * 返回 {@code false} 表示"一定不存在"（拦截器短路拒绝）。
     */
    boolean mightContain(String key);

    /**
     * 将 key 加入过滤器。在业务数据写入路径调用（如新增用户、创建订单）。
     */
    void put(String key);

    /**
     * 批量 put，用于预热场景（如全量同步历史数据）。
     */
    void putAll(Set<String> keys);

    /**
     * 底层过滤器是否已初始化完成。
     * <p>未初始化时调用方应跳过预校验，避免因"空 bloom"导致所有请求被拒。
     */
    boolean isExists();
}