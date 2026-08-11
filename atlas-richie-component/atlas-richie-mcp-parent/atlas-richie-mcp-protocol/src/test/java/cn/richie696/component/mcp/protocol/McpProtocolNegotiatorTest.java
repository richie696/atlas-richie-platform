package cn.richie696.component.mcp.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpProtocolNegotiator} 在客户端 / 服务端版本集合上的选择策略：
 * 双方共同支持 modern 版本时优先选择该版本；否则降级到 legacy；交集为空时
 * 必须抛出 {@link McpProtocolException}（错误码 {@code -32022}），并在
 * {@code data.supported} 中附带本端支持的版本集合，便于上游日志与协商 UI 提示；
 * 自定义构造器在去重、必填、不可变等约束上保持稳定。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpProtocolNegotiator 协议版本协商器")
class McpProtocolNegotiatorTest {

    private final McpProtocolNegotiator negotiator = new McpProtocolNegotiator();

    @Test
    @DisplayName("默认协商器按 SUPPORTED 顺序返回 modern 优先")
    void prefersModernVersionWhenBothSidesSupportIt() {
        assertThat(negotiator.negotiate(List.of(
                McpProtocolVersions.V_2025_11_25,
                McpProtocolVersions.V_2026_07_28)))
                .isEqualTo(McpProtocolVersions.V_2026_07_28);
    }

    @Test
    @DisplayName("对端只支持 legacy 时降级到 legacy")
    void fallsBackToLegacyVersion() {
        assertThat(negotiator.negotiate(List.of(McpProtocolVersions.V_2025_11_25)))
                .isEqualTo(McpProtocolVersions.V_2025_11_25);
    }

    @Test
    @DisplayName("交集为空时抛 -32022 McpProtocolException 并附带 supported 列表")
    void reportsSupportedVersionsWhenNegotiationFails() {
        assertThatThrownBy(() -> negotiator.negotiate(List.of("2024-11-05")))
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32022);
                    assertThat(exception.data().get("supported"))
                            .isEqualTo(McpProtocolVersions.SUPPORTED);
                });
    }

    @Test
    @DisplayName("null supportedVersions 必须抛 NPE")
    void rejectsNullSupportedVersions() {
        assertThatNullPointerException()
                .isThrownBy(() -> new McpProtocolNegotiator(null));
    }

    @Test
    @DisplayName("空 supportedVersions 必须抛 IllegalArgumentException")
    void rejectsEmptySupportedVersions() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new McpProtocolNegotiator(List.of()))
                .withMessageContaining("At least one protocol version");
    }

    @Test
    @DisplayName("supportedVersions 自动去重并保序")
    void supportedVersionsAreDeduplicatedInOrder() {
        McpProtocolNegotiator local = new McpProtocolNegotiator(List.of(
                McpProtocolVersions.V_2026_07_28,
                McpProtocolVersions.V_2025_11_25,
                McpProtocolVersions.V_2026_07_28));

        assertThat(local.supportedVersions())
                .containsExactly(
                        McpProtocolVersions.V_2026_07_28,
                        McpProtocolVersions.V_2025_11_25);
    }

    @Test
    @DisplayName("supportedVersions() 返回不可变 List")
    void supportedVersionsIsImmutable() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> negotiator.supportedVersions().add("2099-01-01"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("requireSupported：未在支持列表中的版本抛 -32022")
    void requireSupportedRejectsUnknown() {
        assertThatThrownBy(() -> negotiator.requireSupported("2099-01-01"))
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32022);
                    assertThat(exception.data()).containsEntry("requested", "2099-01-01");
                });
    }

    @Test
    @DisplayName("requireSupported：支持的版本直接放行")
    void requireSupportedAcceptsKnown() {
        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> negotiator.requireSupported(McpProtocolVersions.V_2026_07_28));
    }
}
