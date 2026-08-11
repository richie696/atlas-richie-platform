package cn.richie696.component.mcp.api.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpCompletionRequest} 紧凑构造器对 argumentName 必填、reference/value/contextArguments 的不可变拷贝。
 */
@DisplayName("McpCompletionRequest 补全请求 record")
class McpCompletionRequestTest {

    @Test
    @DisplayName("完整参数：暴露全部字段")
    void shouldExposeAllFields() {
        Map<String, Object> reference = new LinkedHashMap<>(Map.of("type", "tool", "name", "lookup"));
        Map<String, String> contextArgs = new LinkedHashMap<>(Map.of("country", "CN"));
        McpCompletionRequest request = new McpCompletionRequest(reference, "city", "Shang", contextArgs);

        assertThat(request.reference()).containsEntry("name", "lookup");
        assertThat(request.argumentName()).isEqualTo("city");
        assertThat(request.value()).isEqualTo("Shang");
        assertThat(request.contextArguments()).containsEntry("country", "CN");
    }

    @Test
    @DisplayName("null reference 回落为不可变空 Map")
    void shouldFallbackNullReference() {
        McpCompletionRequest request = new McpCompletionRequest(null, "arg", "v", null);

        assertThat(request.reference()).isEmpty();
        assertThat(request.contextArguments()).isEmpty();
        assertThat(request.value()).isEqualTo("v");
    }

    @Test
    @DisplayName("null value 回落为 \"\"")
    void shouldFallbackNullValue() {
        McpCompletionRequest request = new McpCompletionRequest(null, "arg", null, null);
        assertThat(request.value()).isEmpty();
    }

    @Test
    @DisplayName("null/空白 argumentName 抛 IllegalArgumentException")
    void shouldRejectBlankArgumentName() {
        assertThatThrownBy(() -> new McpCompletionRequest(null, null, "v", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("argumentName");
        assertThatThrownBy(() -> new McpCompletionRequest(null, "  ", "v", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("argumentName");
    }

    @Test
    @DisplayName("reference/contextArguments 不可变：外部突变不影响内部")
    void shouldDefensivelyCopyReferenceAndContextArguments() {
        Map<String, Object> mutableRef = new LinkedHashMap<>(Map.of("k", "v"));
        Map<String, String> mutableCtx = new LinkedHashMap<>(Map.of("k", "v"));
        McpCompletionRequest request = new McpCompletionRequest(mutableRef, "arg", null, mutableCtx);

        mutableRef.put("sneaky", "v");
        mutableCtx.put("sneaky", "v");

        assertThat(request.reference()).containsOnlyKeys("k");
        assertThat(request.contextArguments()).containsOnlyKeys("k");
    }
}
