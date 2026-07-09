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
package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.spi.KeyDimension;
import com.richie.component.web.core.spi.KeyResolver;
import com.richie.component.web.core.spi.WebRequestContext;

import java.util.List;
import java.util.Objects;

/**
 * 多维 key 组合 {@link KeyResolver}（README.md §4.1 RateLimit 多维 key 组合）。
 * <p>
 * 接收有序 {@link KeyDimension} bean 列表，按序遍历每个维度：
 * <ul>
 *   <li>维度 {@link KeyDimension#extract(WebRequestContext)} 返回 {@code null} 或空 → 跳过</li>
 *   <li>返回非空 → 以 {@code <name>:<value>} 形式拼接</li>
 * </ul>
 * 多个非空维度之间用 {@link #separator} 连接（默认 {@code "|"}）。
 *
 * <h2>最终 key 格式</h2>
 * <pre>
 *   维度顺序：client(10) → tenant(20) → ip(30) → path(40)
 *   输入：clientId=user-1, tenantId=acme, ip=1.2.3.4, path=/api/v1/orders
 *   输出：client:user-1|tenant:acme|ip:1.2.3.4|path:/api/v1/orders
 * </pre>
 *
 * <h2>全部维度为 null</h2>
 * <p>返回 {@code null} → 由拦截器按"未识别客户端"处理（markShortCircuit 401）。
 *
 * @author richie696
 * @since 2026-07
 */
public class CompositeKeyResolver implements KeyResolver {

    private final List<KeyDimension> dimensions;
    private final String separator;

    public CompositeKeyResolver(List<KeyDimension> dimensions, String separator) {
        this.dimensions = List.copyOf(Objects.requireNonNull(dimensions, "dimensions must not be null"));
        this.separator = Objects.requireNonNull(separator, "separator must not be null");
        if (separator.isEmpty()) {
            throw new IllegalArgumentException("separator must not be empty");
        }
    }

    @Override
    public String resolve(WebRequestContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (KeyDimension dim : dimensions) {
            String value = dim.extract(ctx);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(separator);
            }
            sb.append(dim.name()).append(':').append(value);
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    public List<KeyDimension> dimensions() {
        return dimensions;
    }

    public String separator() {
        return separator;
    }
}