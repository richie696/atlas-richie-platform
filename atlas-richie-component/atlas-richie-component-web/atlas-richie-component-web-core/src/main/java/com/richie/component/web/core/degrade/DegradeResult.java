package com.richie.component.web.core.degrade;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 降级响应结果（README.md §4.7）。
 * <p>
 * 不可变 record；承载降级时回写给客户端的：
 * <ul>
 *   <li>HTTP 状态码</li>
 *   <li>响应 body（已序列化为字符串）</li>
 *   <li>附加响应 header（保留插入顺序）</li>
 *   <li>策略名（用于日志 / HookBus）</li>
 * </ul>
 *
 * <p><strong>body 序列化约定</strong>：业务方在策略里直接生成 JSON 字符串；
 * 本组件不强制使用 Jackson，避免强依赖。
 *
 * @param status HTTP 状态码（200 ~ 599）
 * @param body   响应 body 字符串
 * @param headers 附加响应 header；null 视为空
 * @param strategyName 命中的策略名（用于日志 / 排查）
 *
 * @author richie696
 * @since 2026-07
 */
public record DegradeResult(int status,
                            String body,
                            Map<String, String> headers,
                            String strategyName) {

    public DegradeResult {
        Objects.requireNonNull(body, "body must not be null");
        Objects.requireNonNull(strategyName, "strategyName must not be null");
        if (status < 100 || status > 599) {
            throw new IllegalArgumentException("status out of HTTP range: " + status);
        }
        headers = headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /**
     * 紧凑构造器（最常用）：无附加 header。
     */
    public static DegradeResult of(int status, String body, String strategyName) {
        return new DegradeResult(status, body, null, strategyName);
    }

    /**
     * 带 header 的构造器。
     */
    public static DegradeResult of(int status, String body, Map<String, String> headers, String strategyName) {
        return new DegradeResult(status, body, headers, strategyName);
    }
}