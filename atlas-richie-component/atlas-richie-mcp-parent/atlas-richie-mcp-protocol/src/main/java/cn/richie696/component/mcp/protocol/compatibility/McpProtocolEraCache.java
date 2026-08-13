package cn.richie696.component.mcp.protocol.compatibility;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 进程内 TTL 缓存：保存协议协商结果以避免重复探测。
 *
 * 为什么做成无 Redis 依赖：本类只服务于探测状态机
 * {@link McpEraProbeStateMachine}，其作用域是"避免同一个远端在 TTL 窗口内被反复探测"，
 * 这一诉求天然适合进程内缓存。如果业务需要跨实例共享结果，可以在本类之上包装一层
 * {@code platform.cache}（Redis）实现，而不需要让本类承担 Redis 依赖。
 *
 * 关键设计：
 * <ul>
 *   <li>{@link Clock} 注入便于测试用 {@link Clock#fixed} 模拟时间推进。</li>
 *   <li>{@link #get(String)} 是 lazy expiry：访问时发现过期即删除，避免后台线程扫描。</li>
 *   <li>使用 {@link ConcurrentHashMap} 保证线程安全。</li>
 *   <li>{@link #put} 强制 TTL 为正，避免出现"立即过期"的死条目。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpProtocolEraCache {
    private final Clock clock;
    private final ConcurrentMap<String, McpNegotiatedProtocol> entries = new ConcurrentHashMap<>();

    /**
     * 使用系统 UTC 时钟构造缓存。
     */
    public McpProtocolEraCache() {
        this(Clock.systemUTC());
    }

    /**
     * 使用自定义时钟构造缓存（便于测试）。
     *
     * @param clock 时钟实例
     */
    public McpProtocolEraCache(Clock clock) {
        this.clock = clock;
    }

    /**
     * 按 key 获取未过期的协商结果。
     *
     * @param key 缓存键
     * @return 命中且未过期时返回对应条目；否则返回 {@link Optional#empty()}
     */
    public Optional<McpNegotiatedProtocol> get(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        McpNegotiatedProtocol entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expired(clock.instant())) {
            // Lazy expiry：访问时发现过期即删除，避免后台扫描线程
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    /**
     * 写入一个协商结果，TTL 由参数决定。
     *
     * @param key     缓存键
     * @param version 已选定的协议版本
     * @param ttl     过期时长，必须为正
     * @throws IllegalArgumentException 当 key 空白或 TTL 非正时
     */
    public void put(String key, String version, Duration ttl) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        entries.put(key, new McpNegotiatedProtocol(version, clock.instant().plus(ttl)));
    }

    /**
     * 手动失效指定 key。
     *
     * @param key 缓存键；为 {@code null} 时静默忽略
     */
    public void invalidate(String key) {
        if (key != null) {
            entries.remove(key);
        }
    }

    /**
     * 清空所有缓存条目（用于测试或全局重置场景）。
     */
    public void clear() {
        entries.clear();
    }
}
