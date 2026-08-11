package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpHttpRequest} 在紧凑构造器里执行的防御性拷贝：构造后外部对入参
 * {@code headers} 的修改不会污染本对象；外部嵌套的 value 列表也无法
 * 二次 add；以及对 {@code null} 头 / {@code null} value 容错——把
 * 头集合替换为 {@link java.util.Collections#unmodifiableMap} 包装。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpHttpRequest 请求载体")
class McpHttpRequestTest {

    @Test
    @DisplayName("外部头被修改不影响快照")
    void outerHeaderMutationDoesNotAffectSnapshot() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Accept", new ArrayList<>(List.of("application/json")));
        McpHttpRequest request = new McpHttpRequest(
                "POST",
                headers,
                new McpJsonRpcRequest("2.0", 1, "tools/list", Map.of()));

        headers.put("Accept", List.of("text/plain"));
        headers.put("Origin", List.of("https://attacker.example"));

        assertThat(request.headers()).containsOnlyKeys("Accept");
        assertThat(request.headers().get("Accept")).containsExactly("application/json");
    }

    @Test
    @DisplayName("嵌套 value 列表的二次修改不影响快照")
    void nestedValueListMutationDoesNotAffectSnapshot() {
        List<String> values = new ArrayList<>(List.of("application/json"));
        Map<String, List<String>> headers = Map.of("Accept", values);
        McpHttpRequest request = new McpHttpRequest(
                "POST",
                headers,
                new McpJsonRpcRequest("2.0", 1, "tools/list", Map.of()));

        values.add("text/event-stream");

        assertThat(request.headers().get("Accept")).containsExactly("application/json");
    }

    @Test
    @DisplayName("不可变：尝试修改返回的 headers 会抛异常")
    void headersViewIsUnmodifiable() {
        McpHttpRequest request = new McpHttpRequest(
                "POST",
                Map.of("Accept", List.of("application/json")),
                new McpJsonRpcRequest("2.0", 1, "tools/list", Map.of()));

        assertThatThrownBy(() -> request.headers().put("Origin", List.of("https://malicious")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.headers().get("Accept").add("text/plain"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("null headers 不会崩溃，结果为不可变空 Map")
    void nullHeadersProducesEmptyUnmodifiable() {
        McpHttpRequest request = new McpHttpRequest(
                "POST",
                null,
                new McpJsonRpcRequest("2.0", 1, "tools/list", Map.of()));

        assertThat(request.headers()).isEmpty();
        assertThatThrownBy(() -> request.headers().put("Origin", List.of("https://x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("null value 列表归一为不可变空列表")
    void nullValueListIsNormalizedToEmpty() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Accept", null);
        McpHttpRequest request = new McpHttpRequest(
                "POST",
                headers,
                new McpJsonRpcRequest("2.0", 1, "tools/list", Map.of()));

        assertThat(request.headers().get("Accept")).isEmpty();
        assertThatThrownBy(() -> request.headers().get("Accept").add("text/plain"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
