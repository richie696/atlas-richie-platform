package cn.richie696.component.mcp.protocol.discovery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpCacheHints} 注入缓存元数据的契约：
 * {@code ttlMs} 强制非负（防止缓存层把负 TTL 解释为"永远不过期"）；{@code ttlMs}
 * 序列化为 {@link BigDecimal} 以规避 Long → String 的渲染差异；
 * 返回的 Map 不可写回原引用。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpCacheHints 缓存元数据注入")
class McpCacheHintsTest {

    @Test
    @DisplayName("ttlMs = 0 允许（视为立刻过期边界）")
    void zeroTtlIsAllowed() {
        Map<String, Object> result = McpCacheHints.add(Map.of(), 0L, McpCacheScope.PRIVATE);

        assertThat(result).containsEntry(McpCacheHints.TTL_MS, BigDecimal.valueOf(0L));
        assertThat(result).containsEntry(McpCacheHints.CACHE_SCOPE, "private");
    }

    @Test
    @DisplayName("正 ttlMs 正常注入；scope 映射到线格式字面量")
    void positiveTtlAndScope() {
        Map<String, Object> result = McpCacheHints.add(
                Map.of("existing", "v"), 3_600_000L, McpCacheScope.PUBLIC);

        assertThat(result)
                .containsEntry("existing", "v")
                .containsEntry(McpCacheHints.TTL_MS, BigDecimal.valueOf(3_600_000L))
                .containsEntry(McpCacheHints.CACHE_SCOPE, "public");
    }

    @Test
    @DisplayName("负 ttlMs 必须拒绝")
    void negativeTtlIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> McpCacheHints.add(Map.of(), -1L, McpCacheScope.PUBLIC))
                .withMessageContaining("ttlMs");
    }

    @Test
    @DisplayName("null result Map 视为空 Map，缓存字段仍正常注入")
    void nullResultNormalizesToEmpty() {
        Map<String, Object> result = McpCacheHints.add(null, 1L, McpCacheScope.PRIVATE);

        assertThat(result).hasSize(2)
                .containsKeys(McpCacheHints.TTL_MS, McpCacheHints.CACHE_SCOPE);
    }

    @Test
    @DisplayName("返回新 Map：修改入参不影响返回值；返回值与入参不是同一引用")
    void returnsNewMapDetachedFromInput() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("a", 1);

        Map<String, Object> result = McpCacheHints.add(mutable, 10L, McpCacheScope.PUBLIC);
        mutable.put("intruder", true);

        assertThat(result).containsOnlyKeys("a", McpCacheHints.TTL_MS, McpCacheHints.CACHE_SCOPE);
        org.assertj.core.api.Assertions.assertThat(result).isNotSameAs(mutable);
    }
}
