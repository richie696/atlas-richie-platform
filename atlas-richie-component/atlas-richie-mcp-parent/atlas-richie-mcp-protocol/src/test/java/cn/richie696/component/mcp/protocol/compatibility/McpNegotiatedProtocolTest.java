package cn.richie696.component.mcp.protocol.compatibility;

import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpNegotiatedProtocol} 在构造期强校验版本号 + 过期时间，
 * {@link #expired(Instant)} 严格按 {@code now >= expiresAt} 判定，避免脏数据进入缓存。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpNegotiatedProtocol 缓存条目契约")
class McpNegotiatedProtocolTest {

    private final Instant future = Instant.parse("2026-12-31T00:00:00Z");

    @Test
    @DisplayName("合法版本号 + 过期时间可成功构造")
    void acceptsValidInputs() {
        McpNegotiatedProtocol protocol = new McpNegotiatedProtocol(
                McpProtocolVersions.V_2026_07_28, future);

        assertThat(protocol.version()).isEqualTo(McpProtocolVersions.V_2026_07_28);
        assertThat(protocol.expiresAt()).isEqualTo(future);
    }

    @Test
    @DisplayName("空白 / null 版本号必须拒绝")
    void rejectsBlankOrNullVersion() {
        assertThatThrownBy(() -> new McpNegotiatedProtocol(null, future))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version must not be blank");
        assertThatThrownBy(() -> new McpNegotiatedProtocol("", future))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new McpNegotiatedProtocol("   ", future))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("不在 SUPPORTED 白名单的版本号必须拒绝")
    void rejectsUnsupportedVersion() {
        assertThatThrownBy(() -> new McpNegotiatedProtocol("2099-01-01", future))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported MCP protocol version");
    }

    @Test
    @DisplayName("null 过期时间必须拒绝")
    void rejectsNullExpiry() {
        assertThatNullPointerException()
                .isThrownBy(() -> new McpNegotiatedProtocol(
                        McpProtocolVersions.V_2026_07_28, null))
                .withMessageContaining("expiresAt");
    }

    @Test
    @DisplayName("expired(now) 在 now < expiresAt 时返回 false")
    void notYetExpired() {
        McpNegotiatedProtocol protocol = new McpNegotiatedProtocol(
                McpProtocolVersions.V_2026_07_28, future);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(protocol.expired(now)).isFalse();
    }

    @Test
    @DisplayName("expired(now) 在 now >= expiresAt 时返回 true（边界等于也算过期）")
    void expiredAtBoundary() {
        McpNegotiatedProtocol protocol = new McpNegotiatedProtocol(
                McpProtocolVersions.V_2026_07_28, future);

        assertThat(protocol.expired(future)).isTrue();
        assertThat(protocol.expired(Instant.parse("2027-01-01T00:00:00Z"))).isTrue();
    }

    @Test
    @DisplayName("expired(null) 必须抛 NPE")
    void expiredRejectsNullNow() {
        McpNegotiatedProtocol protocol = new McpNegotiatedProtocol(
                McpProtocolVersions.V_2026_07_28, future);

        assertThatNullPointerException()
                .isThrownBy(() -> protocol.expired(null))
                .withMessageContaining("now");
    }
}
