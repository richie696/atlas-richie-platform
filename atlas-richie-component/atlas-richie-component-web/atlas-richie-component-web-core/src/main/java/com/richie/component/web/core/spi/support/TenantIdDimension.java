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