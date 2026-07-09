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
package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.spi.KeyDimension;
import com.richie.component.web.core.spi.WebRequestContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@link KeyDimension} 内置实现：从 {@code X-Tenant-Id} header 取租户 ID。
 * <p>
 * 命名/语义遵循 {@code TenantInterceptor}（README.md §9 多租户解析）保持一致。
 *
 * @author richie696
 * @since 2026-07
 */
@Component
@Order(20)
public class TenantIdDimension implements KeyDimension {

    /** 默认 tenant header 名；与 {@code TenantInterceptor} 默认配置一致。 */
    public static final String DEFAULT_HEADER = "X-Tenant-Id";

    @Override
    public String name() {
        return "tenant";
    }

    @Override
    public String extract(WebRequestContext ctx) {
        String value = ctx.header(DEFAULT_HEADER);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}