package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.McpCancellationToken;
import cn.richie696.component.mcp.api.McpProgressReporter;
import cn.richie696.component.mcp.api.model.McpResourceContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpServerCallContextRequest} 紧凑构造器对 header 多值列表与 attributes 的不可变拷贝，
 * 以及 cancellationToken/progressReporter 的缺省回落。
 */
@DisplayName("McpServerCallContextRequest 协议层请求 record")
class McpServerCallContextRequestTest {

    @Test
    @DisplayName("完整参数：暴露全部字段并对 header/attributes 做不可变拷贝")
    void shouldExposeAllFields() {
        Map<String, List<String>> headers = Map.of("X-Tenant", List.of("a", "b"));
        Map<String, Object> attributes = Map.of("traceId", "abc");
        McpCancellationToken token = () -> true;
        McpProgressReporter reporter = (p, t, m) -> { };
        McpServerCallContextRequest request = new McpServerCallContextRequest(
                "req-1", "2025-11-25", headers, attributes, Instant.parse("2030-01-01T00:00:00Z"),
                token, reporter);

        assertThat(request.requestId()).isEqualTo("req-1");
        assertThat(request.protocolVersion()).isEqualTo("2025-11-25");
        assertThat(request.headers()).containsKey("X-Tenant");
        assertThat(request.attributes()).containsEntry("traceId", "abc");
        assertThat(request.defaultDeadline()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
        assertThat(request.cancellationToken()).isSameAs(token);
        assertThat(request.progressReporter()).isSameAs(reporter);
    }

    @Test
    @DisplayName("缺省回落：cancellationToken/progressReporter 为 null 时回落到 NONE/NOOP")
    void shouldFallbackToNoopInstances() {
        McpServerCallContextRequest request = new McpServerCallContextRequest(
                "r", "v", null, null, null, null, null);

        assertThat(request.cancellationToken().isCancellationRequested()).isFalse();
        assertThat(request.progressReporter()).isSameAs(McpProgressReporter.NOOP);
        assertThat(request.headers()).isEmpty();
        assertThat(request.attributes()).isEmpty();
    }

    @Test
    @DisplayName("header values 为 null 时回落到空 List")
    void shouldHandleNullHeaderValues() {
        Map<String, List<String>> headers = new java.util.LinkedHashMap<>();
        headers.put("X-Empty", null);

        McpServerCallContextRequest request = new McpServerCallContextRequest(
                "r", "v", headers, null, null, null, null);

        assertThat(request.headers().get("X-Empty")).isEmpty();
        assertThat(request.headers().get("X-Empty")).isUnmodifiable();
    }

    @Nested
    @DisplayName("header 不可变拷贝")
    class HeaderDefensiveCopy {

        @Test
        @DisplayName("修改外部 List 不影响内部状态")
        void shouldNotLeakThroughSourceList() {
            List<String> mutable = new ArrayList<>(List.of("a"));
            Map<String, List<String>> headers = new java.util.LinkedHashMap<>();
            headers.put("X-A", mutable);

            McpServerCallContextRequest request = new McpServerCallContextRequest(
                    "r", "v", headers, null, null, null, null);

            mutable.add("b");

            assertThat(request.headers().get("X-A")).containsExactly("a");
        }

        @Test
        @DisplayName("构造后修改 header 集合抛异常")
        void shouldBeImmutableAfterConstruction() {
            McpServerCallContextRequest request = new McpServerCallContextRequest(
                    "r", "v", Map.of("X-A", List.of("a")), null, null, null, null);

            assertThat(request.headers()).isUnmodifiable();
            assertThat(request.headers().get("X-A")).isUnmodifiable();
        }
    }

    @Test
    @DisplayName("attributes 不可变：构造后外部 Map 突变不影响内部")
    void shouldDefensivelyCopyAttributes() {
        Map<String, Object> mutable = new java.util.LinkedHashMap<>(Map.of("k", 1));
        McpServerCallContextRequest request = new McpServerCallContextRequest(
                "r", "v", null, mutable, null, null, null);

        mutable.put("sneaky", "v");

        assertThat(request.attributes()).containsOnlyKeys("k");
    }
}
