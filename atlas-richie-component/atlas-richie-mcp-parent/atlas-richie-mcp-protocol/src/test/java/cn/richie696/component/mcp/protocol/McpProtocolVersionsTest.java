package cn.richie696.component.mcp.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpProtocolVersions} 暴露的版本号常量与默认支持顺序的契约。
 * 顺序是协议协商时的偏好依据，必须稳定、不可变。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpProtocolVersions 协议版本常量与默认支持列表")
class McpProtocolVersionsTest {

    @Test
    @DisplayName("版本号常量字面量与官方发布日期一致")
    void versionLiteralsAreStable() {
        assertThat(McpProtocolVersions.V_2026_07_28).isEqualTo("2026-07-28");
        assertThat(McpProtocolVersions.V_2025_11_25).isEqualTo("2025-11-25");
    }

    @Test
    @DisplayName("SUPPORTED 列表以现代协议版本优先")
    void supportedListPrefersModernFirst() {
        assertThat(McpProtocolVersions.SUPPORTED)
                .containsExactly(
                        McpProtocolVersions.V_2026_07_28,
                        McpProtocolVersions.V_2025_11_25);
    }

    @Test
    @DisplayName("SUPPORTED 列表是不可变 List，外部修改会被拒")
    void supportedListIsImmutable() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> McpProtocolVersions.SUPPORTED.add("2099-01-01"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
