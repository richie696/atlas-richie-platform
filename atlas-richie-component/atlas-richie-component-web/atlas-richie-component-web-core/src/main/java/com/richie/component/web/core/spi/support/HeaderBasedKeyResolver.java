package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.spi.KeyResolver;
import com.richie.component.web.core.spi.WebRequestContext;

import java.util.Objects;

/**
 * 默认 {@link KeyResolver}：从指定 header 取 clientKey（README.md §4.1）。
 * <p>
 * 配置项见 {@link com.richie.component.web.core.config.WebFilterProperties#getKeyHeader()}，
 * 默认 {@code X-Client-Id}。
 *
 * <h2>行为</h2>
 * <ul>
 *   <li>header 存在 → 返回 header value（trim）</li>
 *   <li>header 缺失 / 空字符串 → 返回 {@code null}（拦截器按"未识别"处理）</li>
 *   <li>大小写不敏感：依赖 {@link WebRequestContext#header(String)} 内部归一</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
public final class HeaderBasedKeyResolver implements KeyResolver {

    private final String headerName;

    public HeaderBasedKeyResolver(String headerName) {
        this.headerName = Objects.requireNonNull(headerName, "headerName must not be null");
    }

    @Override
    public String resolve(WebRequestContext ctx) {
        String value = ctx.header(headerName);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}