package cn.richie696.component.mcp.protocol.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpNormalizedResult} 紧凑构造器强制 {@code resultType} 非空；
 * {@code null} payload 归一为不可变空 Map；外部修改入参不影响结果；{@link ResultType}
 * 枚举覆盖 {@code COMPLETE} / {@code INPUT_REQUIRED} 两种状态。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpNormalizedResult 归一化结果契约")
class McpNormalizedResultTest {

    @Test
    @DisplayName("正常构造：resultType 与 payload 可读回，payload 不可变")
    void exposesImmutableFields() {
        Map<String, Object> payload = Map.of("text", "hello");
        McpNormalizedResult result = new McpNormalizedResult(
                McpNormalizedResult.ResultType.COMPLETE, payload);

        assertThat(result.resultType()).isEqualTo(McpNormalizedResult.ResultType.COMPLETE);
        assertThat(result.payload()).containsEntry("text", "hello");
        assertThatThrownBy(() -> result.payload().put("x", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("null payload 归一为不可变空 Map")
    void nullPayloadNormalizesToEmpty() {
        McpNormalizedResult result = new McpNormalizedResult(
                McpNormalizedResult.ResultType.INPUT_REQUIRED, null);

        assertThat(result.payload()).isEmpty();
    }

    @Test
    @DisplayName("payload 入参被拷贝，外部修改不影响归一化结果")
    void payloadInputIsImmutableSnapshot() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("a", 1);
        McpNormalizedResult result = new McpNormalizedResult(
                McpNormalizedResult.ResultType.COMPLETE, payload);

        payload.put("b", 2);

        assertThat(result.payload()).containsOnlyKeys("a");
    }

    @Test
    @DisplayName("null resultType 必须抛 NPE（避免旧协议默认值被无声丢失）")
    void rejectsNullResultType() {
        assertThatNullPointerException()
                .isThrownBy(() -> new McpNormalizedResult(null, Map.of()))
                .withMessageContaining("resultType");
    }

    @Test
    @DisplayName("ResultType 枚举同时包含 COMPLETE 与 INPUT_REQUIRED")
    void resultTypeEnumHasBothStates() {
        assertThat(McpNormalizedResult.ResultType.values())
                .containsExactlyInAnyOrder(
                        McpNormalizedResult.ResultType.COMPLETE,
                        McpNormalizedResult.ResultType.INPUT_REQUIRED);
    }
}
