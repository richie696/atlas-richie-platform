package cn.richie696.component.mcp.api.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpArgumentMetadata} 紧凑构造器对 enumValues 列表的不可变拷贝。
 */
@DisplayName("McpArgumentMetadata 参数元数据 record")
class McpArgumentMetadataTest {

    @Test
    @DisplayName("完整参数：暴露全部字段")
    void shouldExposeAllFields() {
        McpArgumentMetadata metadata = new McpArgumentMetadata(
                "city", "city name", true, "Beijing", "string", "Beijing", List.of("Beijing", "Shanghai"), true);

        assertThat(metadata.name()).isEqualTo("city");
        assertThat(metadata.description()).isEqualTo("city name");
        assertThat(metadata.required()).isTrue();
        assertThat(metadata.defaultValue()).isEqualTo("Beijing");
        assertThat(metadata.format()).isEqualTo("string");
        assertThat(metadata.example()).isEqualTo("Beijing");
        assertThat(metadata.enumValues()).containsExactly("Beijing", "Shanghai");
        assertThat(metadata.sensitive()).isTrue();
    }

    @Test
    @DisplayName("null enumValues 回落为不可变空列表")
    void shouldFallbackNullEnumValues() {
        McpArgumentMetadata metadata = new McpArgumentMetadata(
                "n", "d", false, "", "", "", null, false);

        assertThat(metadata.enumValues()).isEmpty();
        assertThat(metadata.enumValues()).isUnmodifiable();
    }

    @Test
    @DisplayName("enumValues 不可变：外部突变不影响内部")
    void shouldDefensivelyCopyEnumValues() {
        List<String> mutable = new ArrayList<>(List.of("a"));
        McpArgumentMetadata metadata = new McpArgumentMetadata(
                "n", "d", false, "", "", "", mutable, false);

        mutable.add("b");

        assertThat(metadata.enumValues()).containsExactly("a");
    }
}
