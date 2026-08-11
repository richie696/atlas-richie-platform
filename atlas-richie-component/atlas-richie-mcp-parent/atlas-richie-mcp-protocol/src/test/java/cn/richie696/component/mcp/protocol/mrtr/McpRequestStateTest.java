package cn.richie696.component.mcp.protocol.mrtr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpRequestState} 在构造期对所有字符串字段（payload / principalFingerprint /
 * method / nonce）做强校验，非空非空白；{@code expiresAt} 非 null。任意字段非法都会在
 * MRTR 验证之前就被拒绝。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpRequestState MRTR 状态令牌契约")
class McpRequestStateTest {

    private final Instant future = Instant.parse("2026-12-31T00:00:00Z");

    @Test
    @DisplayName("合法五字段构造可读回")
    void acceptsValidFields() {
        McpRequestState state = new McpRequestState(
                "opaque-payload", "principal-a", "tools/call", future, "nonce-1");

        assertThat(state.payload()).isEqualTo("opaque-payload");
        assertThat(state.principalFingerprint()).isEqualTo("principal-a");
        assertThat(state.method()).isEqualTo("tools/call");
        assertThat(state.expiresAt()).isEqualTo(future);
        assertThat(state.nonce()).isEqualTo("nonce-1");
    }

    @Test
    @DisplayName("payload / principalFingerprint / method / nonce 为 null 或空白必须拒绝")
    void rejectsBlankOrNullStrings() {
        assertThatThrownBy(() -> new McpRequestState(null, "p", "m", future, "n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");
        assertThatThrownBy(() -> new McpRequestState(" ", "p", "m", future, "n"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new McpRequestState("p", null, "m", future, "n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("principalFingerprint");
        assertThatThrownBy(() -> new McpRequestState("p", "  ", "m", future, "n"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new McpRequestState("p", "p", null, future, "n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("method");
        assertThatThrownBy(() -> new McpRequestState("p", "p", " ", future, "n"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new McpRequestState("p", "p", "m", future, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonce");
        assertThatThrownBy(() -> new McpRequestState("p", "p", "m", future, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null expiresAt 必须拒绝")
    void rejectsNullExpiry() {
        assertThatThrownBy(() -> new McpRequestState("p", "p", "m", null, "n"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("expiresAt");
    }
}
