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
package com.richie.component.web.core.spi;

/**
 * 客户端 key 维度 SPI（README.md §4.1 RateLimit 多维 key 组合）。
 * <p>
 * 单个 {@link KeyResolver} 一次只能返回一个字符串，但实际场景常需多维组合：
 * <ul>
 *   <li>{@code client}: 客户端 ID（来自 {@code X-Client-Id} 或 JWT sub）</li>
 *   <li>{@code tenant}: 租户（来自 {@code X-Tenant-Id}）</li>
 *   <li>{@code ip}: 客户端 IP（来自 {@code X-Forwarded-For} / {@code X-Real-IP}）</li>
 *   <li>{@code path}: 请求路径（精确或 Ant 模式）</li>
 * </ul>
 * 本接口把"提取单个维度"能力抽象为可插拔 SPI，
 * 由 {@link com.richie.component.web.core.spi.support.CompositeKeyResolver} 组合成最终 key。
 *
 * <h2>返回值语义</h2>
 * <ul>
 *   <li>{@link #extract(WebRequestContext)} 返回 {@code null} 或空字符串 →
 *       {@code CompositeKeyResolver} 跳过该维度（不进入组合 key）</li>
 *   <li>返回非空字符串 → 进入组合 key，格式为 {@code <name>:<value>}</li>
 * </ul>
 *
 * <h2>顺序</h2>
 * <p>多个 {@code KeyDimension} bean 按 {@link org.springframework.core.annotation.Order @Order} 升序串接；
 * 默认顺序由各内置维度的 {@code @Order} 注解决定（{@code client(10) < tenant(20) < ip(30) < path(40)}），
 * 用户可自定义覆盖。
 *
 * @author richie696
 * @since 2026-07
 */
public interface KeyDimension {

    /**
     * 维度名，用于 {@code CompositeKeyResolver} 输出格式 {@code <name>:<value>} 的前缀。
     * <p>建议短小全小写，不含特殊字符（如 {@code ":"}）。
     */
    String name();

    /**
     * 从请求上下文提取当前维度的值。无法识别时返回 {@code null}（如缺失 header / 路径为空）。
     *
     * @param ctx 当前请求上下文
     * @return 维度值；无法识别返回 {@code null}
     */
    String extract(WebRequestContext ctx);
}