package cn.richie696.component.mcp.client.spring.boot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * 进程内 list/discovery 结果的 TTL 缓存。
 *
 * <p>本类刻意采用"简单 + 进程内"实现，原因：MCP list/discovery 调用的频度远高于 callTool，且对实时性要求宽松，
 * 一个进程内 ConcurrentMap 即可节省 95% 以上的远端请求，而无需引入外部缓存（Redis/Caffeine）。</p>
 *
 * <p>支持按 key、按 serverId 前缀两种粒度的失效，满足 MCP 协议 {@code list_changed} 通知触发"按 server 全量清理"的场景；
 * 通过 {@link Clock} 抽象注入时间源，便于测试。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpClientResultCache {
    private final Clock clock;
    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * 默认构造，使用 {@link Clock#systemUTC()}。
     */
    public McpClientResultCache() {
        this(Clock.systemUTC());
    }

    /**
     * 注入自定义时间源，便于测试或按租户对齐时间基准。
     *
     * @param clock 时间源，不可为 {@code null}
     */
    public McpClientResultCache(Clock clock) {
        this.clock = clock;
    }

    /**
     * 获取指定 key 的缓存值（带 TTL 校验）。
     *
     * <p>当 entry 已过期时，惰性删除（{@link ConcurrentMap#remove(Object, Object)} 保证只删除自己刚读到的实例，
     * 避免误删并发更新写入的新 entry）并返回 {@link Optional#empty()}。</p>
     *
     * @param <T>   期望的目标类型
     * @param key   缓存 key
     * @return 命中且未过期时返回包装值；未命中或已过期返回 {@link Optional#empty()}
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.expiresAt().isAfter(clock.instant())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of((T) entry.value());
    }

    /**
     * 写入缓存，自动计算到期时间。
     *
     * @param key   缓存 key，不可为 {@code null} 或空白
     * @param value 缓存值，不可为 {@code null}
     * @param ttl   过期时长，必须为正数
     * @throws IllegalArgumentException 当 key 空白、value 为 {@code null} 或 ttl 非正时抛出
     */
    public void put(String key, Object value, Duration ttl) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        entries.put(key, new Entry(value, clock.instant().plus(ttl)));
    }

    /**
     * 缓存未命中时通过 loader 加载并写入；命中时直接返回。
     *
     * <p>典型用法是 double-check 风格：{@code getOrLoad(key, ttl, () -> remoteCall())}，
     * 避免业务方手写"先 get 后 put"的样板代码。</p>
     *
     * @param <T>    值的类型
     * @param key    缓存 key
     * @param ttl    加载成功后的过期时长
     * @param loader 加载函数（仅在未命中时调用）
     * @return 已缓存或刚加载的值
     */
    public <T> T getOrLoad(String key, Duration ttl, Supplier<T> loader) {
        return this.<T>get(key).orElseGet(() -> {
            T value = loader.get();
            put(key, value, ttl);
            return value;
        });
    }

    /**
     * 失效指定 key。
     *
     * @param key 待失效的 key；{@code null} 时为 no-op
     */
    public void invalidate(String key) {
        if (key != null) {
            entries.remove(key);
        }
    }

    /**
     * 失效指定 server 的全部缓存家族。
     *
     * <p>约定 key 形如 {@code "<serverId>|<operation>"}，因此通过 {@code serverId + "|"} 前缀匹配即可一次清理。</p>
     *
     * @param serverId 配置中的 server ID；{@code null} 或空白时为 no-op
     */
    public void invalidateServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        String prefix = serverId + "|";
        entries.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * 清空全部缓存。
     */
    public void clear() {
        entries.clear();
    }

    /**
     * 缓存条目值对象，记录值与到期时间。
     */
    private record Entry(Object value, Instant expiresAt) {
    }
}
