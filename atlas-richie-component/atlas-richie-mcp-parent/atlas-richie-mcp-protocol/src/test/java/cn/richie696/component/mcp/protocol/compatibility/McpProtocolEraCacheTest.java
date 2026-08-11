package cn.richie696.component.mcp.protocol.compatibility;

import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * 验证 {@link McpProtocolEraCache} 对已协商协议版本 / 时代的 TTL 缓存行为：
 * 写入合法版本号与正 TTL 后能正确读取；未知版本号或零 / 负 TTL 必须被拒绝，
 * 防止缓存层退化成长久脏数据；过期条目按 lazy 策略自动失效；{@code invalidate}
 * 接受 {@code null} key 而不抛错。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpProtocolEraCache 协议版本 TTL 缓存")
class McpProtocolEraCacheTest {

    private static final String KEY = "https://server.example";
    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");
    private static final String SUPPORTED = McpProtocolVersions.V_2026_07_28;

    @Test
    @DisplayName("固定时钟：写入后能命中，TTL 内一直可读")
    void getReturnsEntryBeforeTtlElapses() {
        McpProtocolEraCache cache = new McpProtocolEraCache(Clock.fixed(NOW, ZoneOffset.UTC));

        cache.put(KEY, SUPPORTED, Duration.ofMinutes(1));

        Optional<McpNegotiatedProtocol> entry = cache.get(KEY);
        assertThat(entry).isPresent();
        assertThat(entry.get().version()).isEqualTo(SUPPORTED);
        assertThat(entry.get().expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("key 不存在时返回 Optional.empty()")
    void missingKeyReturnsEmpty() {
        McpProtocolEraCache cache = new McpProtocolEraCache();

        assertThat(cache.get("missing")).isEmpty();
    }

    @Test
    @DisplayName("null 或空白 key 在 get 中视为未命中，不抛错")
    void getAcceptsNullAndBlankKey() {
        McpProtocolEraCache cache = new McpProtocolEraCache();

        assertThat(cache.get(null)).isEmpty();
        assertThat(cache.get("")).isEmpty();
        assertThat(cache.get("   ")).isEmpty();
    }

    @Test
    @DisplayName("写入 null / 空白 key 必须抛 IllegalArgumentException")
    void putRejectsNullOrBlankKey() {
        McpProtocolEraCache cache = new McpProtocolEraCache();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> cache.put(null, SUPPORTED, Duration.ofMinutes(1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> cache.put("", SUPPORTED, Duration.ofMinutes(1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> cache.put("   ", SUPPORTED, Duration.ofMinutes(1)));
    }

    @Test
    @DisplayName("未在 SUPPORTED 白名单的版本必须拒绝（防止脏数据）")
    void putRejectsUnknownVersion() {
        McpProtocolEraCache cache = new McpProtocolEraCache();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> cache.put(KEY, "2099-01-01", Duration.ofMinutes(1)))
                .withMessageContaining("Unsupported MCP protocol version");
    }

    @Test
    @DisplayName("TTL 为 null / 零 / 负值必须拒绝（避免立即过期）")
    void putRejectsInvalidTtl() {
        McpProtocolEraCache cache = new McpProtocolEraCache();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> cache.put(KEY, SUPPORTED, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> cache.put(KEY, SUPPORTED, Duration.ZERO));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> cache.put(KEY, SUPPORTED, Duration.ofSeconds(-1)));
    }

    @Test
    @DisplayName("过期条目在 get 时被 lazy 删除并返回 empty")
    void expiredEntryIsRemovedOnAccess() {
        MutableClock clock = new MutableClock(NOW);
        McpProtocolEraCache cache = new McpProtocolEraCache(clock);

        cache.put(KEY, SUPPORTED, Duration.ofMinutes(1));
        clock.advance(Duration.ofMinutes(2));

        assertThat(cache.get(KEY)).isEmpty();
    }

    @Test
    @DisplayName("invalidate(null) 静默忽略，非 null key 实际删除")
    void invalidateHandlesNullAndExistingKey() {
        McpProtocolEraCache cache = new McpProtocolEraCache();

        cache.put(KEY, SUPPORTED, Duration.ofMinutes(1));
        cache.invalidate(null);
        assertThat(cache.get(KEY)).isPresent();

        cache.invalidate(KEY);
        assertThat(cache.get(KEY)).isEmpty();
    }

    @Test
    @DisplayName("clear 清空所有条目")
    void clearRemovesAllEntries() {
        McpProtocolEraCache cache = new McpProtocolEraCache();

        cache.put("a", SUPPORTED, Duration.ofMinutes(1));
        cache.put("b", McpProtocolVersions.V_2025_11_25, Duration.ofMinutes(1));

        cache.clear();

        assertThat(cache.get("a")).isEmpty();
        assertThat(cache.get("b")).isEmpty();
    }

    @Test
    @DisplayName("重复 put 同一 key 覆盖前值（写入新 expiresAt）")
    void putOverwritesExistingEntry() {
        McpProtocolEraCache cache = new McpProtocolEraCache(Clock.fixed(NOW, ZoneOffset.UTC));

        cache.put(KEY, SUPPORTED, Duration.ofMinutes(1));
        cache.put(KEY, McpProtocolVersions.V_2025_11_25, Duration.ofHours(1));

        assertThat(cache.get(KEY))
                .get()
                .extracting(McpNegotiatedProtocol::version)
                .isEqualTo(McpProtocolVersions.V_2025_11_25);
    }

    @Test
    @DisplayName("默认构造器使用系统 UTC 时钟（不抛错）")
    void defaultConstructorUsesSystemClock() {
        McpProtocolEraCache cache = new McpProtocolEraCache();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> cache.put(null, SUPPORTED, Duration.ofMinutes(1)));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant initial) {
            this.instant = initial;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
