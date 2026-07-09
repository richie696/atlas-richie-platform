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
package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.spi.KeyDimension;
import com.richie.component.web.core.spi.WebRequestContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@link KeyDimension} 内置实现：从 {@code X-Forwarded-For} 或 {@code X-Real-IP} header 取客户端 IP。
 * <p>
 * 解析逻辑与 {@code AnomalyDetectionInterceptor}（README.md §4.8.2）IP 黑名单检测一致：
 * <ol>
 *   <li>优先 {@code X-Forwarded-For}，取逗号分隔的首个 IP（最左是原始客户端）</li>
 *   <li>否则 {@code X-Real-IP}</li>
 *   <li>均无 → 维度跳过（{@code null}）</li>
 * </ol>
 *
 * @author richie696
 * @since 2026-07
 */
@Component
@Order(30)
public class IpDimension implements KeyDimension {

    public static final String HEADER_XFF = "X-Forwarded-For";
    public static final String HEADER_X_REAL_IP = "X-Real-IP";

    @Override
    public String name() {
        return "ip";
    }

    @Override
    public String extract(WebRequestContext ctx) {
        String xff = ctx.header(HEADER_XFF);
        if (xff == null || xff.isBlank()) {
            xff = ctx.header(HEADER_X_REAL_IP);
        }
        if (xff == null || xff.isBlank()) {
            return null;
        }
        int comma = xff.indexOf(',');
        String first = (comma > 0 ? xff.substring(0, comma) : xff).trim();
        return first.isEmpty() ? null : first;
    }
}