package cn.richie696.component.mcp.server.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpToolRefreshResult} 的不可变 record 语义：
 * null 集合归一为空集合、集合被不可变拷贝、{@link #unchanged(long)} 工厂构造
 * 无变更占位、{@link #changed()} 反映版本号差异。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpToolRefreshResult 测试")
class McpToolRefreshResultTest {

    @Test
    @DisplayName("构造时三个集合为 null 自动归一为空集合")
    void nullSetsAreNormalizedToEmpty() {
        McpToolRefreshResult result = new McpToolRefreshResult(1L, 2L, null, null, null);

        assertThat(result.addedTools()).isEmpty();
        assertThat(result.removedTools()).isEmpty();
        assertThat(result.updatedTools()).isEmpty();
    }

    @Test
    @DisplayName("构造时集合被不可变拷贝（外部突变不可见）")
    void setsAreDefensivelyCopied() {
        Set<String> mutable = new java.util.LinkedHashSet<>(List.of("alpha"));

        McpToolRefreshResult result = new McpToolRefreshResult(0L, 1L, mutable, null, null);

        mutable.clear();
        mutable.add("beta");

        assertThat(result.addedTools()).containsExactly("alpha");
    }

    @Test
    @DisplayName("unchanged 工厂返回空集合且新旧版本号相同")
    void unchangedFactoryBuildsEmptyResult() {
        McpToolRefreshResult result = McpToolRefreshResult.unchanged(42L);

        assertThat(result.oldRevision()).isEqualTo(42L);
        assertThat(result.newRevision()).isEqualTo(42L);
        assertThat(result.addedTools()).isEmpty();
        assertThat(result.removedTools()).isEmpty();
        assertThat(result.updatedTools()).isEmpty();
    }

    @Test
    @DisplayName("changed() 在版本号相同时返回 false")
    void changedFalseWhenRevisionsEqual() {
        McpToolRefreshResult result = new McpToolRefreshResult(5L, 5L,
                Set.of("a"), Set.of(), Set.of());

        assertThat(result.changed()).isFalse();
    }

    @Test
    @DisplayName("changed() 在版本号不同时返回 true")
    void changedTrueWhenRevisionsDiffer() {
        McpToolRefreshResult result = new McpToolRefreshResult(1L, 2L,
                Set.of("alpha"), Set.of(), Set.of());

        assertThat(result.changed()).isTrue();
    }

    @Test
    @DisplayName("完整构造后字段被原样暴露")
    void fieldsAreReturnedAsIs() {
        Set<String> added = Set.of("alpha");
        Set<String> removed = Set.of("beta");
        Set<String> updated = Set.of("gamma");

        McpToolRefreshResult result = new McpToolRefreshResult(1L, 2L, added, removed, updated);

        assertThat(result.oldRevision()).isEqualTo(1L);
        assertThat(result.newRevision()).isEqualTo(2L);
        assertThat(result.addedTools()).containsExactly("alpha");
        assertThat(result.removedTools()).containsExactly("beta");
        assertThat(result.updatedTools()).containsExactly("gamma");
    }
}