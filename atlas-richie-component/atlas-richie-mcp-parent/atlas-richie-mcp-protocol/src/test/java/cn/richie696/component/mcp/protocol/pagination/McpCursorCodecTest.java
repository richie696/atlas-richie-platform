package cn.richie696.component.mcp.protocol.pagination;

import cn.richie696.component.mcp.protocol.McpProtocolException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpCursorCodec} 的不透明游标语义：encode 出的游标不得泄露底层整数值；
 * 同密钥下解码可还原原值；任何对游标字符串的篡改必须被拒绝，防止攻击者通过翻页游标
 * 越权访问他人分页结果。secret 至少 16 字节；负 offset 拒绝；空 / null 游标视为首页 0。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpCursorCodec 分页游标编解码")
class McpCursorCodecTest {

    private static final byte[] SECRET = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private final McpCursorCodec codec = new McpCursorCodec(SECRET);

    @Test
    @DisplayName("encode 不暴露底层整数值；decode 还原原值")
    void roundTripsOpaqueCursor() {
        String cursor = codec.encode(42);

        assertThat(cursor).doesNotContain("42");
        assertThat(codec.decode(cursor)).isEqualTo(42);
    }

    @Test
    @DisplayName("负 offset 必须拒绝")
    void rejectsNegativeOffset() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> codec.encode(-1))
                .withMessageContaining("offset");
    }

    @Test
    @DisplayName("空 / null 游标视为 offset = 0（首页便捷约定）")
    void emptyCursorDecodesToZero() {
        assertThat(codec.decode(null)).isZero();
        assertThat(codec.decode("")).isZero();
        assertThat(codec.decode("   ")).isZero();
    }

    @Test
    @DisplayName("secret 长度 < 16 字节必须拒绝")
    void rejectsShortSecret() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new McpCursorCodec(new byte[8]))
                .withMessageContaining("at least 16 bytes");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new McpCursorCodec(null))
                .withMessageContaining("at least 16 bytes");
    }

    @Test
    @DisplayName("secret 在构造期被克隆，外部修改不影响内部")
    void secretIsCloned() {
        byte[] secret = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        McpCursorCodec local = new McpCursorCodec(secret);
        String cursor = local.encode(0);

        secret[0] = (byte) 0xFF;
        assertThat(local.decode(cursor)).isZero();
    }

    @Test
    @DisplayName("任意篡改（末位替换）必须拒绝")
    void rejectsTamperedCursor() {
        String cursor = codec.encode(42);

        assertThatThrownBy(() -> codec.decode(cursor.substring(0, cursor.length() - 1) + "x"))
                .isInstanceOf(McpProtocolException.class)
                .hasMessageContaining("Invalid pagination cursor");
    }

    @Test
    @DisplayName("非 Base64 内容必须拒绝")
    void rejectsNonBase64Content() {
        assertThatThrownBy(() -> codec.decode("@@@@@"))
                .isInstanceOf(McpProtocolException.class);
        assertThatThrownBy(() -> codec.decode("..."))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    @DisplayName("offset = 0 是合法值")
    void zeroOffsetIsAllowed() {
        String cursor = codec.encode(0);

        assertThat(codec.decode(cursor)).isZero();
    }

    @Test
    @DisplayName("大整数 offset 也能 round-trip")
    void largeOffsetRoundTrips() {
        String cursor = codec.encode(Integer.MAX_VALUE);

        assertThat(codec.decode(cursor)).isEqualTo(Integer.MAX_VALUE);
    }
}
