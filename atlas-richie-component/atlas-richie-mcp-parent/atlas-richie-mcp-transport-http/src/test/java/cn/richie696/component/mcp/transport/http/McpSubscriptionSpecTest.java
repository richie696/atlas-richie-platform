package cn.richie696.component.mcp.transport.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpSubscriptionSpec} 紧凑构造器把 {@code resourceSubscriptions} 归一为
 * 不可变 Set：null 替换为 {@link Set#of()}；非 null 时做 {@link Set#copyOf} 拷贝以防御
 * 外部突变，并使其适合在并发场景下安全共享给 {@link McpSubscriptionManager}。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpSubscriptionSpec 订阅规则")
class McpSubscriptionSpecTest {

    @Test
    @DisplayName("null resourceSubscriptions 归一为不可变空 Set")
    void nullSetBecomesEmptyImmutable() {
        McpSubscriptionSpec spec = new McpSubscriptionSpec(true, false, false, null);

        assertThat(spec.resourceSubscriptions()).isEmpty();
        assertThatThrownBy(() -> spec.resourceSubscriptions().add("file://x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("外部 Set 修改不影响 spec 快照")
    void outerSetMutationDoesNotAffectSnapshot() {
        Set<String> resources = new LinkedHashSet<>();
        resources.add("file://docs/a.md");
        McpSubscriptionSpec spec = new McpSubscriptionSpec(false, false, false, resources);

        resources.add("file://docs/b.md");

        assertThat(spec.resourceSubscriptions()).containsExactly("file://docs/a.md");
    }

    @Test
    @DisplayName("布尔字段原样回传")
    void booleansAreRetained() {
        McpSubscriptionSpec spec = new McpSubscriptionSpec(
                true, true, true, Set.of("file://a", "file://b"));

        assertThat(spec.toolsListChanged()).isTrue();
        assertThat(spec.promptsListChanged()).isTrue();
        assertThat(spec.resourcesListChanged()).isTrue();
        assertThat(spec.resourceSubscriptions())
                .containsExactlyInAnyOrder("file://a", "file://b");
    }

    @Test
    @DisplayName("入参 Set 重复 URI 被去重（Set 语义）")
    void duplicateUrisAreDeduplicated() {
        Set<String> source = new LinkedHashSet<>();
        source.add("file://a");
        source.add("file://a");
        source.add("file://b");
        McpSubscriptionSpec spec = new McpSubscriptionSpec(false, false, false, source);

        assertThat(spec.resourceSubscriptions())
                .containsExactlyInAnyOrder("file://a", "file://b");
    }
}
