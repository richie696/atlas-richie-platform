package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.spi.KeyDimension;
import com.richie.component.web.core.spi.WebRequestContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@link KeyDimension} 内置实现：取请求路径作为 key 维度。
 * <p>
 * 适用场景：同一客户端对不同接口需独立限流（路径级隔离）。路径为原始 URI 路径，
 * 不含 query string（见 {@link WebRequestContext#path()} 契约）。
 *
 * @author richie696
 * @since 2026-07
 */
@Component
@Order(40)
public class PathDimension implements KeyDimension {

    @Override
    public String name() {
        return "path";
    }

    @Override
    public String extract(WebRequestContext ctx) {
        String path = ctx.path();
        return (path == null || path.isBlank()) ? null : path;
    }
}