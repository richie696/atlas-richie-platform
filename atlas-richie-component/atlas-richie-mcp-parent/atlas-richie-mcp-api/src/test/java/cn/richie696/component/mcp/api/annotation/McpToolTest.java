package cn.richie696.component.mcp.api.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过反射读取 {@code @McpTool} 注解属性，覆盖默认与覆盖值两条路径，
 * 并校验 Target=METHOD / Retention=RUNTIME。
 */
@DisplayName("@McpTool 工具方法注解")
class McpToolTest {

    @Test
    @DisplayName("默认值：未显式覆盖时回落到注解默认值")
    void shouldExposeDefaultValues() throws NoSuchMethodException {
        Method method = Fixture.class.getMethod("defaultTool");
        McpTool annotation = method.getAnnotation(McpTool.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEmpty();
        assertThat(annotation.title()).isEmpty();
        assertThat(annotation.description()).isEmpty();
        assertThat(annotation.idempotent()).isFalse();
        assertThat(annotation.readOnly()).isFalse();
        assertThat(annotation.destructive()).isFalse();
        assertThat(annotation.openWorld()).isFalse();
        assertThat(annotation.requiredScopes()).isEmpty();
        assertThat(annotation.enabled()).isTrue();
        assertThat(annotation.group()).isEmpty();
        assertThat(annotation.timeoutMs()).isEqualTo(-1L);
        assertThat(annotation.audit()).isFalse();
    }

    @Test
    @DisplayName("覆盖值：所有显式属性都被正确读取")
    void shouldExposeOverriddenValues() throws NoSuchMethodException {
        Method method = Fixture.class.getMethod("configured");
        McpTool annotation = method.getAnnotation(McpTool.class);

        assertThat(annotation.name()).isEqualTo("customer.lookup");
        assertThat(annotation.title()).isEqualTo("Customer Lookup");
        assertThat(annotation.description()).isEqualTo("Look up a customer by id");
        assertThat(annotation.idempotent()).isTrue();
        assertThat(annotation.readOnly()).isTrue();
        assertThat(annotation.destructive()).isFalse();
        assertThat(annotation.openWorld()).isTrue();
        assertThat(annotation.requiredScopes()).containsExactly("customer:read", "tenant:any");
        assertThat(annotation.enabled()).isFalse();
        assertThat(annotation.group()).isEqualTo("customer");
        assertThat(annotation.timeoutMs()).isEqualTo(5000L);
        assertThat(annotation.audit()).isTrue();
    }

    @Test
    @DisplayName("Target/Retention：仅方法可见且运行时可见")
    void shouldHaveMethodTargetAndRuntimeRetention() {
        assertThat(McpTool.class.getAnnotation(Target.class))
                .isNotNull()
                .satisfies(target -> assertThat(target.value()).containsExactly(ElementType.METHOD));
        assertThat(McpTool.class.getAnnotation(Retention.class))
                .isNotNull()
                .extracting(Retention::value)
                .isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    @DisplayName("@McpTool 不会污染参数上的 @McpArgument 注解")
    void shouldNotLeakOntoParameter() throws NoSuchMethodException {
        Method method = Fixture.class.getMethod("withArgument", String.class);
        Parameter parameter = method.getParameters()[0];
        McpArgument argument = parameter.getAnnotation(McpArgument.class);

        assertThat(argument).isNotNull();
        assertThat(argument.name()).isEqualTo("customerId");
    }

    @SuppressWarnings("unused")
    private static final class Fixture {
        @McpTool
        public void defaultTool() { }

        @McpTool(name = "customer.lookup", title = "Customer Lookup",
                description = "Look up a customer by id",
                idempotent = true, readOnly = true, openWorld = true,
                requiredScopes = {"customer:read", "tenant:any"},
                enabled = false, group = "customer", timeoutMs = 5000L, audit = true)
        public void configured() { }

        public void withArgument(@McpArgument(name = "customerId") String customerId) { }
    }
}
