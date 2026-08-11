package cn.richie696.component.mcp.protocol.model;

import cn.richie696.component.mcp.protocol.McpProtocolEra;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpNormalizedRequest} 紧凑构造器对必填字段（{@code method}、
 * {@code protocolVersion}、{@code era}）做强校验；所有 Map 类型 null → 不可变空 Map；
 * 外部修改入参不影响归一化结果；{@link #notification()} 跟随 {@code id} 是否为 null。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpNormalizedRequest 归一化请求契约")
class McpNormalizedRequestTest {

    @Test
    @DisplayName("正常构造：所有字段可读回，Map 不可变")
    void exposesImmutableFields() {
        Map<String, Object> args = Map.of("customerId", "C-1");
        McpNormalizedRequest request = new McpNormalizedRequest(
                "r-1", "tools/call", args,
                "2026-07-28", McpProtocolEra.STATELESS_2026,
                null, Map.of("tools", Map.of()), Map.of());

        assertThat(request.method()).isEqualTo("tools/call");
        assertThat(request.protocolVersion()).isEqualTo("2026-07-28");
        assertThat(request.era()).isEqualTo(McpProtocolEra.STATELESS_2026);
        assertThat(request.peer()).isNull();
        assertThat(request.arguments()).containsEntry("customerId", "C-1");
        assertThatThrownBy(() -> request.arguments().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("null arguments / capabilities / metadata 归一为不可变空 Map")
    void nullCollectionsNormalizeToEmpty() {
        McpNormalizedRequest request = new McpNormalizedRequest(
                null, "ping", null, "2026-07-28",
                McpProtocolEra.STATELESS_2026, null, null, null);

        assertThat(request.arguments()).isEmpty();
        assertThat(request.capabilities()).isEmpty();
        assertThat(request.metadata()).isEmpty();
    }

    @Test
    @DisplayName("Map 入参被拷贝，外部修改不影响归一化结果")
    void mapInputsAreImmutableSnapshot() {
        Map<String, Object> args = new HashMap<>();
        args.put("a", 1);
        McpNormalizedRequest request = new McpNormalizedRequest(
                1, "tools/call", args, "2026-07-28",
                McpProtocolEra.STATELESS_2026, null, null, null);

        args.put("b", 2);

        assertThat(request.arguments()).containsOnlyKeys("a");
    }

    @Test
    @DisplayName("null method / protocolVersion / era 必须抛 NPE")
    void requiresNonNullMethodVersionAndEra() {
        assertThatNullPointerException()
                .isThrownBy(() -> new McpNormalizedRequest(
                        1, null, Map.of(), "2026-07-28",
                        McpProtocolEra.STATELESS_2026, null, null, null))
                .withMessageContaining("method");
        assertThatNullPointerException()
                .isThrownBy(() -> new McpNormalizedRequest(
                        1, "ping", Map.of(), null,
                        McpProtocolEra.STATELESS_2026, null, null, null))
                .withMessageContaining("protocolVersion");
        assertThatNullPointerException()
                .isThrownBy(() -> new McpNormalizedRequest(
                        1, "ping", Map.of(), "2026-07-28",
                        null, null, null, null))
                .withMessageContaining("era");
    }

    @Test
    @DisplayName("notification() 在 id 为 null 时返回 true")
    void notificationReflectsIdPresence() {
        McpNormalizedRequest notification = new McpNormalizedRequest(
                null, "events/ping", Map.of(), "2026-07-28",
                McpProtocolEra.STATELESS_2026, null, null, null);
        McpNormalizedRequest call = new McpNormalizedRequest(
                1, "tools/list", Map.of(), "2026-07-28",
                McpProtocolEra.STATELESS_2026, null, null, null);

        assertThat(notification.notification()).isTrue();
        assertThat(call.notification()).isFalse();
    }
}
