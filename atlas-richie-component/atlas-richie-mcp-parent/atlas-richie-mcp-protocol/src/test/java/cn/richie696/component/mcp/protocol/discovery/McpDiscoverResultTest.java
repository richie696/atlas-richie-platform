package cn.richie696.component.mcp.protocol.discovery;

import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.protocol.model.McpImplementationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpDiscoverResult} 紧凑构造器的归一化契约：
 * 版本列表去重保序、空白版本拒绝；{@code ttlMs} 强制非负；所有 Map/ServerInfo 字段
 * 缺省归一为不可变空视图，外部修改入参不影响结果。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpDiscoverResult discover 结果归一化")
class McpDiscoverResultTest {

    @Test
    @DisplayName("正常构造：去重保序；ServerInfo / instructions / extensions 缺省归一")
    void acceptsValidInputs() {
        List<String> versions = new ArrayList<>();
        versions.add(McpProtocolVersions.V_2026_07_28);
        versions.add(McpProtocolVersions.V_2025_11_25);
        versions.add(McpProtocolVersions.V_2026_07_28);

        McpDiscoverResult result = new McpDiscoverResult(
                versions,
                Map.of("tools", Map.of()),
                new McpImplementationInfo("atlas", "1.0"),
                "Enterprise MCP",
                1_000L,
                McpCacheScope.PUBLIC,
                Map.of("com.example/build", "42"));

        assertThat(result.supportedVersions())
                .containsExactly(
                        McpProtocolVersions.V_2026_07_28,
                        McpProtocolVersions.V_2025_11_25);
        assertThat(result.capabilities()).containsKey("tools");
        assertThat(result.serverInfo().name()).isEqualTo("atlas");
        assertThat(result.instructions()).isEqualTo("Enterprise MCP");
        assertThat(result.ttlMs()).isEqualTo(1_000L);
        assertThat(result.cacheScope()).isEqualTo(McpCacheScope.PUBLIC);
        assertThat(result.extensions()).containsEntry("com.example/build", "42");
    }

    @Test
    @DisplayName("null supportedVersions 必须拒绝（业务必填）")
    void rejectsNullVersions() {
        assertThatThrownBy(() -> new McpDiscoverResult(
                null, Map.of(), null, null, 0L, McpCacheScope.PUBLIC, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("supportedVersions");
    }

    @Test
    @DisplayName("空 / 全空白版本列表必须拒绝")
    void rejectsEmptyOrBlankVersions() {
        assertThatThrownBy(() -> new McpDiscoverResult(
                List.of(), Map.of(), null, null, 0L, McpCacheScope.PUBLIC, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supportedVersions must contain at least one non-blank version");
        assertThatThrownBy(() -> new McpDiscoverResult(
                List.of(""), Map.of(), null, null, 0L, McpCacheScope.PUBLIC, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new McpDiscoverResult(
                List.of(McpProtocolVersions.V_2026_07_28, "   "),
                Map.of(), null, null, 0L, McpCacheScope.PUBLIC, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null capabilities / extensions 归一为不可变空 Map")
    void nullCollectionsNormalizeToEmpty() {
        McpDiscoverResult result = new McpDiscoverResult(
                List.of(McpProtocolVersions.V_2026_07_28),
                null, null, null, 0L, McpCacheScope.PRIVATE, null);

        assertThat(result.capabilities()).isEmpty();
        assertThat(result.extensions()).isEmpty();
    }

    @Test
    @DisplayName("Map 入参被冻结，外部修改不影响 discover 结果")
    void mapInputsAreImmutableSnapshot() {
        Map<String, Object> caps = new HashMap<>();
        caps.put("a", 1);
        Map<String, Object> ext = new HashMap<>();
        ext.put("b", 2);

        McpDiscoverResult result = new McpDiscoverResult(
                List.of(McpProtocolVersions.V_2026_07_28),
                caps, null, null, 0L, McpCacheScope.PUBLIC, ext);
        caps.put("intruder", true);
        ext.put("intruder", true);

        assertThat(result.capabilities()).containsOnlyKeys("a");
        assertThat(result.extensions()).containsOnlyKeys("b");
    }

    @Test
    @DisplayName("ttlMs 为负必须拒绝")
    void rejectsNegativeTtl() {
        assertThatThrownBy(() -> new McpDiscoverResult(
                List.of(McpProtocolVersions.V_2026_07_28),
                Map.of(), null, null, -1L, McpCacheScope.PUBLIC, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttlMs");
    }

    @Test
    @DisplayName("null cacheScope 必须拒绝（业务必填）")
    void rejectsNullCacheScope() {
        assertThatThrownBy(() -> new McpDiscoverResult(
                List.of(McpProtocolVersions.V_2026_07_28),
                Map.of(), null, null, 0L, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cacheScope");
    }
}
