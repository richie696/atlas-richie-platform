package cn.richie696.component.mcp.client.spring.boot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** Process-local TTL cache for list/discovery results. */
public final class McpClientResultCache {
    private final Clock clock;
    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    public McpClientResultCache() {
        this(Clock.systemUTC());
    }

    public McpClientResultCache(Clock clock) {
        this.clock = clock;
    }

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

    public <T> T getOrLoad(String key, Duration ttl, Supplier<T> loader) {
        return this.<T>get(key).orElseGet(() -> {
            T value = loader.get();
            put(key, value, ttl);
            return value;
        });
    }

    public void invalidate(String key) {
        if (key != null) {
            entries.remove(key);
        }
    }

    /** Invalidates all cached result families for one configured MCP server. */
    public void invalidateServer(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        String prefix = serverId + "|";
        entries.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void clear() {
        entries.clear();
    }

    private record Entry(Object value, Instant expiresAt) {
    }
}
