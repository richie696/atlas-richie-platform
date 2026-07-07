package com.richie.component.web.core.protection;

import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Objects;

/**
 * 长连接路径旁路检查（README.md §4.8.2 A 组 + §4.4 HangDetection 衔接）。
 * <p>
 * 命中配置的 Ant 风格路径模式（如 {@code /ws/**} / {@code /sse/**} / {@code /stream/**}）即视为长连接；
 * 后续 {@code HangDetectionInterceptor}（§4.4）会读 ctx 上的 {@link #LONG_LIVED_ATTRIBUTE} 标志跳过 watchdog，
 * 避免误杀 SSE / WebSocket / stream 长连接。
 *
 * <h2>触发方</h2>
 * <p>本类由 {@link PlatformProtectionInterceptor} 调用；不直接进拦截器链。
 *
 * @author richie696
 * @since 2026-07
 */
public class LongLivedPathBypass {

    /** ctx attribute key（{@link PlatformProtectionInterceptor} 写入，{@code HangDetectionInterceptor} 读取）。 */
    public static final String LONG_LIVED_ATTRIBUTE = "platform.web.long-lived";

    private final List<String> pathPatterns;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public LongLivedPathBypass(List<String> pathPatterns) {
        this.pathPatterns = List.copyOf(Objects.requireNonNull(pathPatterns, "pathPatterns must not be null"));
    }

    /**
     * 路径是否在长连接旁路列表内。
     */
    public boolean matches(String path) {
        if (path == null) {
            return false;
        }
        for (String pattern : pathPatterns) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 配置的路径模式列表（只读，用于诊断 / 测试断言）。
     */
    public List<String> patterns() {
        return pathPatterns;
    }
}