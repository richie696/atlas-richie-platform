package cn.richie696.component.mcp.protocol.compatibility;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Small process-local TTL cache for protocol negotiation results.
 *
 * <p>The cache intentionally has no Redis dependency. Applications that need
 * cross-instance persistence can wrap this API with their platform cache.</p>
 */
public final class McpProtocolEraCache {
    private final Clock clock;
    private final ConcurrentMap<String, McpNegotiatedProtocol> entries = new ConcurrentHashMap<>();

    public McpProtocolEraCache() {
        this(Clock.systemUTC());
    }

    public McpProtocolEraCache(Clock clock) {
        this.clock = clock;
    }

    public Optional<McpNegotiatedProtocol> get(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        McpNegotiatedProtocol entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expired(clock.instant())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public void put(String key, String version, Duration ttl) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        entries.put(key, new McpNegotiatedProtocol(version, clock.instant().plus(ttl)));
    }

    public void invalidate(String key) {
        if (key != null) {
            entries.remove(key);
        }
    }

    public void clear() {
        entries.clear();
    }
}
