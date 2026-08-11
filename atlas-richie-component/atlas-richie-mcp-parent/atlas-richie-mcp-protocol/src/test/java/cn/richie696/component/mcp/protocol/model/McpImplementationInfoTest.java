package cn.richie696.component.mcp.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpImplementationInfo} 作为对端身份描述的防腐层契约：
 * {@code fromWire} 容错（缺失可选字段 / 缺省 icons 列表 / 异常类型直接抛
 * {@link IllegalArgumentException}）；{@code toWire} 字段顺序固定为
 * {@code name, version, title, description, websiteUrl, icons}；{@code icons} 元素不可被外部修改。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpImplementationInfo 对端身份防腐层")
class McpImplementationInfoTest {

    @Test
    @DisplayName("便捷构造器只填必填字段，可选字段留空")
    void compactConstructorHandlesRequiredOnly() {
        McpImplementationInfo info = new McpImplementationInfo("atlas", "1.0.0");

        assertThat(info.name()).isEqualTo("atlas");
        assertThat(info.version()).isEqualTo("1.0.0");
        assertThat(info.title()).isNull();
        assertThat(info.description()).isNull();
        assertThat(info.websiteUrl()).isNull();
        assertThat(info.icons()).isEmpty();
    }

    @Test
    @DisplayName("fromWire/toWire roundtrip：字段顺序稳定、可选字段按需写入")
    void fromWireAndToWireRoundtrip() {
        Map<String, Object> wire = new java.util.LinkedHashMap<>();
        wire.put("name", "atlas-server");
        wire.put("version", "2.0.0");
        wire.put("title", "Atlas MCP");
        wire.put("websiteUrl", "https://example.com/mcp");
        wire.put("icons", List.of(Map.of("src", "https://example.com/icon.png")));

        McpImplementationInfo info = McpImplementationInfo.fromWire(wire);
        Map<String, Object> result = info.toWire();

        assertThat(result).containsExactly(
                Map.entry("name", "atlas-server"),
                Map.entry("version", "2.0.0"),
                Map.entry("title", "Atlas MCP"),
                Map.entry("websiteUrl", "https://example.com/mcp"),
                Map.entry("icons", List.of(Map.of("src", "https://example.com/icon.png"))));
    }

    @Test
    @DisplayName("fromWire 缺失可选字段时返回 null，icons 默认为空")
    void fromWireAcceptsPartialInput() {
        McpImplementationInfo info = McpImplementationInfo.fromWire(
                Map.of("name", "atlas", "version", "1.0.0"));

        assertThat(info.title()).isNull();
        assertThat(info.description()).isNull();
        assertThat(info.websiteUrl()).isNull();
        assertThat(info.icons()).isEmpty();
    }

    @Test
    @DisplayName("fromWire 缺 name 或 version 抛 IllegalArgumentException")
    void fromWireRejectsMissingRequiredFields() {
        assertThatThrownBy(() -> McpImplementationInfo.fromWire(Map.of("version", "1.0.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> McpImplementationInfo.fromWire(Map.of("name", "atlas")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    @DisplayName("name / version 非字符串或空白时 fromWire 拒绝")
    void fromWireRejectsBlankOrNonStringRequiredFields() {
        assertThatThrownBy(() -> McpImplementationInfo.fromWire(
                Map.of("name", " ", "version", "1.0.0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> McpImplementationInfo.fromWire(
                Map.of("name", "atlas", "version", 42)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    @DisplayName("icons 必须为数组，元素必须为对象，含非字符串键时报错")
    void fromWireValidatesIconsShape() {
        assertThatThrownBy(() -> McpImplementationInfo.fromWire(Map.of(
                "name", "atlas", "version", "1.0.0", "icons", "not-array")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("icons must be an array");

        assertThatThrownBy(() -> McpImplementationInfo.fromWire(Map.of(
                "name", "atlas", "version", "1.0.0", "icons", List.of("not-object"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("icons[] must be an object");

        assertThatThrownBy(() -> McpImplementationInfo.fromWire(Map.of(
                "name", "atlas", "version", "1.0.0",
                "icons", List.of(Map.<Object, Object>of(42, "x")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("icons[] contains a non-string key");
    }

    @Test
    @DisplayName("icons 元素在构造后被冻结为不可变 Map，外部修改不影响内部")
    void iconsAreImmutable() {
        java.util.Map<String, Object> icon = new java.util.HashMap<>();
        icon.put("src", "x");

        McpImplementationInfo info = new McpImplementationInfo(
                "atlas", "1.0.0", null, null, null, List.of(icon));

        icon.put("intruder", true);

        assertThat(info.icons()).hasSize(1);
        assertThat(info.icons().get(0)).containsOnlyKeys("src");
        assertThatThrownBy(() -> info.icons().get(0).put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("toWire 缺省可选字段时不写入；icons 为空也不写入")
    void toWireOmitsAbsentOptionalFields() {
        Map<String, Object> wire = McpImplementationInfo.fromWire(
                Map.of("name", "atlas", "version", "1.0.0"))
                .toWire();

        assertThat(wire).containsOnlyKeys("name", "version");
    }
}
