package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.McpCancellationToken;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link McpRequiredScopeVisibilityPolicy} 装饰器的授权叠加语义：
 * 底层策略拒绝则最终拒绝、底层放行但缺 scope 则拒绝、底层放行且持有全部 scope 则放行；
 * scopes 支持字符串 / 字符串集合 / 空白拆分 / requiredScopes 为字符串 / 空集合。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpRequiredScopeVisibilityPolicy 测试")
class McpRequiredScopeVisibilityPolicyTest {

    private McpToolVisibilityPolicy delegate;
    private McpRequiredScopeVisibilityPolicy policy;

    @BeforeEach
    void setUp() {
        delegate = mock(McpToolVisibilityPolicy.class);
        policy = new McpRequiredScopeVisibilityPolicy(delegate);
    }

    @Test
    @DisplayName("构造时 null delegate 抛 NPE")
    void rejectsNullDelegate() {
        assertThatThrownBy(() -> new McpRequiredScopeVisibilityPolicy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("delegate");
    }

    @Test
    @DisplayName("底层策略拒绝时装饰器也拒绝（短路）")
    void delegateRejectionShortCircuits() {
        when(delegate.isVisible(aliceTool(), context())).thenReturn(false);

        assertThat(policy.isVisible(aliceTool(), context())).isFalse();
    }

    @Test
    @DisplayName("底层放行 + 未持有必填 scope → 拒绝")
    void missingRequiredScopeRejects() {
        when(delegate.isVisible(ordersTool(), context())).thenReturn(true);

        assertThat(policy.isVisible(ordersTool(), context())).isFalse();
    }

    @Test
    @DisplayName("底层放行 + 持有全部必填 scope → 放行")
    void holdingAllRequiredScopesPermits() {
        McpCallContext authorized = contextWithScopes("orders:read", "profile");
        when(delegate.isVisible(ordersTool(), authorized)).thenReturn(true);

        assertThat(policy.isVisible(ordersTool(), authorized)).isTrue();
    }

    @Test
    @DisplayName("底层放行 + scopes 为字符串（按空白拆分）→ 放行")
    void stringScopesAreSplitByWhitespace() {
        McpCallContext authorized = contextWithScopeString("orders:read profile");
        when(delegate.isVisible(ordersTool(), authorized)).thenReturn(true);

        assertThat(policy.isVisible(ordersTool(), authorized)).isTrue();
    }

    @Test
    @DisplayName("scopes 为集合（Collection<String>）→ 按字符串识别")
    void collectionScopesAreIterated() {
        McpCallContext authorized = contextWithScopesCollection(List.of("orders:read"));
        when(delegate.isVisible(ordersTool(), authorized)).thenReturn(true);

        assertThat(policy.isVisible(ordersTool(), authorized)).isTrue();
    }

    @Test
    @DisplayName("requiredScopes 为字符串而非集合 → 仍按单一 scope 校验")
    void requiredScopeAsStringIsHonored() {
        McpToolDescriptor tool = new McpToolDescriptor(
                "profile.read", null, null,
                Map.of("type", "object"),
                Map.of(),
                Map.of("requiredScopes", "profile:read"));
        when(delegate.isVisible(tool, context())).thenReturn(true);

        assertThat(policy.isVisible(tool, context())).isFalse();

        McpCallContext granted = contextWithScopes("profile:read");
        when(delegate.isVisible(tool, granted)).thenReturn(true);
        assertThat(policy.isVisible(tool, granted)).isTrue();
    }

    @Test
    @DisplayName("requiredScopes 为 null 或非集合非字符串 → 视为无必填")
    void unknownRequiredScopesFormatMeansOpen() {
        McpToolDescriptor tool = new McpToolDescriptor(
                "open.tool", null, null,
                Map.of("type", "object"),
                Map.of(),
                Map.of("requiredScopes", 42));
        when(delegate.isVisible(tool, context())).thenReturn(true);

        assertThat(policy.isVisible(tool, context())).isTrue();
    }

    @Test
    @DisplayName("scopes 集合中的空白字符串被忽略")
    void blankScopesAreIgnored() {
        McpCallContext authorized = contextWithScopesCollection(List.of("   ", "orders:read"));
        when(delegate.isVisible(ordersTool(), authorized)).thenReturn(true);

        assertThat(policy.isVisible(ordersTool(), authorized)).isTrue();
    }

    private static McpToolDescriptor aliceTool() {
        return new McpToolDescriptor(
                "alice.tool", null, null,
                Map.of("type", "object"),
                Map.of(),
                Map.of());
    }

    private static McpToolDescriptor ordersTool() {
        return new McpToolDescriptor(
                "orders.read", null, null,
                Map.of("type", "object"),
                Map.of(),
                Map.of("requiredScopes", List.of("orders:read")));
    }

    private static McpCallContext context() {
        return new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "alice",
                null,
                Map.of(),
                McpCancellationToken.NONE,
                null);
    }

    private static McpCallContext contextWithScopes(String... scopes) {
        return new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "alice",
                null,
                Map.of("scopes", List.of(scopes)),
                McpCancellationToken.NONE,
                null);
    }

    private static McpCallContext contextWithScopeString(String scopeString) {
        return new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "alice",
                null,
                Map.of("scope", scopeString),
                McpCancellationToken.NONE,
                null);
    }

    private static McpCallContext contextWithScopesCollection(List<String> scopes) {
        return new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "alice",
                null,
                Map.of("scopes", scopes),
                McpCancellationToken.NONE,
                null);
    }
}