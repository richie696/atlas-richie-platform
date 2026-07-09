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
/**
 * Redis 各类型 API 的 Spring 管理器实现，经 {@link com.richie.component.cache.GlobalCache} 对外暴露。
 *
 * <p><b>复杂度总序</b>（与 {@link com.richie.component.cache.GlobalCache} 一致）：
 * {@code O(1) > O(log n) > O(n) > O(n log n) > O(n²) > O(2ⁿ) > …}
 *
 * <p><b>ToC 分级策略</b>：
 * <ul>
 *   <li>{@code O(1)}：核心链路推荐；本包中对应封装优先标为 {@link com.richie.component.cache.redis.perf.RedisComplexityTier#O1}</li>
 *   <li>{@code O(log n)}：有序结构等，可控范围内可用，配合 {@link com.richie.component.cache.redis.perf.RedisPerfGuard} 与阈值</li>
 *   <li>{@code O(n)}：与元素数/扫描相关，ToC 高并发默认禁用；仅离线、管理端、低频任务等需文档与配置双重约束</li>
 *   <li>更差：严禁在 ToC 核心路径暴露；封装层应禁止或降级为批处理/异步</li>
 * </ul>
 *
 * <p>各 Manager 对外方法的时间复杂度以 Redis 命令主导项为准；多 key、pipeline、循环调用会叠加业务侧代价。
 * 运行时策略见 {@code spring.data.redis.perf.*}（含可选白名单 {@code toc-allowed-complexities}）。
 * String 类型写入时另见 {@link com.richie.component.cache.redis.perf.RedisStringPayloadInspector}（集合/JavaBean 整包、超大 value 等）。
 *
 * @see com.richie.component.cache.redis.perf.RedisOperationCatalog
 * @see com.richie.component.cache.redis.perf.RedisPerfGuard
 */
package com.richie.component.cache.redis.manage;
