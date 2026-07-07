package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.config.ratelimit.WebFilterProperties;
import com.richie.component.web.core.spi.KeyDimension;
import com.richie.component.web.core.spi.WebRequestContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@link KeyDimension} 内置实现：从指定 header 取客户端 ID（README.md §4.1）。
 * <p>
 * 默认从 {@link WebFilterProperties#getKeyHeader()}（{@code X-Client-Id}）读取，
 * 与 {@code HeaderBasedKeyResolver} 默认行为一致，确保组合与单维模式在同一配置下行为对齐。
 *
 * <h2>行为</h2>
 * <ul>
 *   <li>header 存在 → 返回 trim 后的值</li>
 *   <li>header 缺失 / 空字符串 / 全空白 → 返回 {@code null}（维度跳过）</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-07
 */
@Component
@Order(10)
public class ClientIdDimension implements KeyDimension {

    private final String headerName;

    public ClientIdDimension(WebFilterProperties properties) {
        this.headerName = properties.getKeyHeader();
    }

    @Override
    public String name() {
        return "client";
    }

    @Override
    public String extract(WebRequestContext ctx) {
        String value = ctx.header(headerName);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}