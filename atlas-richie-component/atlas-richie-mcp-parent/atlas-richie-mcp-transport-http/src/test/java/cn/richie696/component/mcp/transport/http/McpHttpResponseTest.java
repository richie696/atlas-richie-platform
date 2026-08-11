package cn.richie696.component.mcp.transport.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpHttpResponse} 在两种构造路径（普通构造器 + {@code json/accepted/sse} 静态工厂）
 * 下的不可变契约：空/null {@code notifications} 归一为 {@link List#of()}；{@link #hasBody()} 与
 * {@code body} 一致；{@code notifications} 列表在 {@link McpHttpResponse} 实例间不可共享以避免
 * alias bug；{@code body} 传 {@code Subscription}、{@code Map} 等不同类型仍保持原引用（适配器自行解释）。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpHttpResponse 响应记录")
class McpHttpResponseTest {

    @Test
    @DisplayName("json 工厂：固定 application/json Content-Type 与空通知列表")
    void jsonFactoryProducesJsonContentType() {
        Map<String, Object> body = Map.of("ok", true);
        McpHttpResponse response = McpHttpResponse.json(200, body);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.contentType()).isEqualTo("application/json");
        assertThat(response.body()).isSameAs(body);
        assertThat(response.notifications()).isEmpty();
        assertThat(response.hasBody()).isTrue();
    }

    @Test
    @DisplayName("accepted 工厂：202 + null body + null contentType")
    void acceptedFactoryProducesNoContent() {
        McpHttpResponse response = McpHttpResponse.accepted();

        assertThat(response.status()).isEqualTo(202);
        assertThat(response.contentType()).isNull();
        assertThat(response.body()).isNull();
        assertThat(response.notifications()).isEmpty();
        assertThat(response.hasBody()).isFalse();
    }

    @Test
    @DisplayName("sse 工厂：固定 text/event-stream 并保留通知列表")
    void sseFactoryProducesEventStream() {
        Map<String, Object> payload = Map.of("streamId", "1");
        List<Map<String, Object>> notifications = List.of(Map.of("method", "ping"));
        McpHttpResponse response = McpHttpResponse.sse(200, payload, notifications);

        assertThat(response.contentType()).isEqualTo("text/event-stream");
        assertThat(response.body()).isSameAs(payload);
        assertThat(response.notifications()).containsExactly(Map.of("method", "ping"));
    }

    @Test
    @DisplayName("直接构造：null notifications 归一为不可变空列表")
    void directConstructorNormalizesNullNotifications() {
        McpHttpResponse response = new McpHttpResponse(204, "text/plain", null, null);

        assertThat(response.notifications()).isEmpty();
        assertThat(response.notifications()).isInstanceOf(List.class);
        assertThatThrownBy(() -> response.notifications().add(Map.of("x", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("直接构造：mutating 外部列表不会影响响应快照")
    void externalListMutationDoesNotAffectResponse() {
        List<Map<String, Object>> mutable = new ArrayList<>();
        mutable.add(Map.of("kind", "initial"));
        McpHttpResponse response = new McpHttpResponse(200, "text/event-stream", null, mutable);

        mutable.add(Map.of("kind", "late"));

        assertThat(response.notifications()).hasSize(1);
    }

    @Test
    @DisplayName("便捷三参构造：缺省 notifications 等价于空列表")
    void threeArgConstructorDefaultsToEmptyNotifications() {
        Map<String, Object> body = Map.of("err", "boom");
        McpHttpResponse response = new McpHttpResponse(500, "application/json", body);

        assertThat(response.notifications()).isEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.body();
        assertThat(responseBody).containsEntry("err", "boom");
    }

    @Test
    @DisplayName("hasBody：body 为 null 时返回 false，否则为 true")
    void hasBodyReflectsBodyPresence() {
        assertThat(McpHttpResponse.accepted().hasBody()).isFalse();
        assertThat(new McpHttpResponse(200, "application/json", "text").hasBody()).isTrue();
    }
}
