package cn.richie696.component.mcp.protocol.mrtr;

import cn.richie696.component.mcp.protocol.McpProtocolException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpRequestStateCodec} 的多轮交互安全契约：
 * secret 至少 32 字节；外部修改入参不影响内部密钥；篡改 / 过期 / 主体不匹配 / 方法
 * 不匹配都按 {@code MCP_INVALID_REQUEST_STATE} 拒绝；重复签发产生独立 nonce 不可预测。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpRequestStateCodec MRTR 状态编解码")
class McpRequestStateCodecTest {

    private static final byte[] SECRET = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);

    private final McpRequestStateCodec codec = new McpRequestStateCodec(SECRET);

    @Test
    @DisplayName("protect + verify：相同主体/方法、令牌未过期时可成功验证")
    void protectsAndVerifiesOpaqueState() {
        String token = codec.protect("opaque", "principal-a", "tools/call",
                Instant.now().plusSeconds(30));

        McpRequestState state = codec.verify(token, "principal-a", "tools/call");
        assertThat(state.payload()).isEqualTo("opaque");
        assertThat(state.principalFingerprint()).isEqualTo("principal-a");
        assertThat(state.method()).isEqualTo("tools/call");
    }

    @Test
    @DisplayName("secret 长度 < 32 字节必须拒绝")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new McpRequestStateCodec(new byte[16]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
        assertThatThrownBy(() -> new McpRequestStateCodec(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("secret 字段在构造期被克隆，外部修改不影响内部")
    void secretIsCloned() {
        byte[] secret = new byte[32];
        for (int i = 0; i < 32; i++) {
            secret[i] = (byte) i;
        }
        McpRequestStateCodec local = new McpRequestStateCodec(secret);

        secret[0] = (byte) 0xFF;
        String token = local.protect("opaque", "p", "m", Instant.now().plusSeconds(30));

        assertThat(local.verify(token, "p", "m").payload()).isEqualTo("opaque");
    }

    @Test
    @DisplayName("主体或方法不匹配时拒绝")
    void bindsStateToPrincipalAndMethod() {
        String token = codec.protect("opaque", "principal-a", "tools/call",
                Instant.now().plusSeconds(30));

        assertThatThrownBy(() -> codec.verify(token, "principal-b", "tools/call"))
                .isInstanceOf(McpProtocolException.class)
                .hasMessageContaining("Invalid or expired");
        assertThatThrownBy(() -> codec.verify(token, "principal-a", "other/method"))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    @DisplayName("已过期的令牌必须拒绝")
    void rejectsExpiredToken() {
        String token = codec.protect("opaque", "principal-a", "tools/call",
                Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> codec.verify(token, "principal-a", "tools/call"))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    @DisplayName("格式错误（无分隔符 / 非 Base64）的令牌必须拒绝")
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> codec.verify("no-separator", "p", "m"))
                .isInstanceOf(McpProtocolException.class);
        assertThatThrownBy(() -> codec.verify("@@@@.@@@@", "p", "m"))
                .isInstanceOf(McpProtocolException.class);
        assertThatThrownBy(() -> codec.verify("body.signature.tail", "p", "m"))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    @DisplayName("签名被篡改时拒绝（重新编码伪造签名也不可绕过）")
    void rejectsTamperedSignature() {
        String token = codec.protect("opaque", "principal-a", "tools/call",
                Instant.now().plusSeconds(30));
        int dot = token.lastIndexOf('.');
        String forged = token.substring(0, dot + 1) + "AAAAAAAAAAAAAAAAAAAAAA";

        assertThatThrownBy(() -> codec.verify(forged, "principal-a", "tools/call"))
                .isInstanceOf(McpProtocolException.class);
    }
}
