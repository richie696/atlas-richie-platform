package cn.richie696.component.mcp.transport.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpOriginPolicy} 作为函数式接口的能力：可以用 lambda 创建策略，
 * 默认静态方法 {@link McpOriginPolicy#denyAllPresentOrigins()} 对任何非空
 * origin 拒绝但不拒 null。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpOriginPolicy Origin 校验策略")
class McpOriginPolicyTest {

    @Test
    @DisplayName("lambda 实现：自定义白名单按值判断")
    void lambdaPolicyAcceptsWhitelistAndRejectsOthers() {
        McpOriginPolicy policy = origin -> "https://foundry.example".equals(origin);

        assertThat(policy.isAllowed("https://foundry.example")).isTrue();
        assertThat(policy.isAllowed("https://other.example")).isFalse();
    }

    @Test
    @DisplayName("denyAllPresentOrigins：对任何非 null 输入一律 false")
    void denyAllRejectsEveryNonNullOrigin() {
        McpOriginPolicy policy = McpOriginPolicy.denyAllPresentOrigins();

        assertThat(policy.isAllowed("https://anywhere.example")).isFalse();
        assertThat(policy.isAllowed("https://attacker.example")).isFalse();
        assertThat(policy.isAllowed("")).isFalse();
    }

    @Test
    @DisplayName("denyAllPresentOrigins 两次调用返回行为等价")
    void denyAllInstancesBehaveIdentically() {
        McpOriginPolicy first = McpOriginPolicy.denyAllPresentOrigins();
        McpOriginPolicy second = McpOriginPolicy.denyAllPresentOrigins();

        assertThat(first.isAllowed("https://x"))
                .isEqualTo(second.isAllowed("https://x"))
                .isFalse();
    }
}
