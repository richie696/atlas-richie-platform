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
 * 通过反射读取 {@code @McpHeader} 注解，并校验其只能标注在方法参数上。
 */
@DisplayName("@McpHeader header 透传注解")
class McpHeaderTest {

    @Test
    @DisplayName("value() 应返回构造时传入的 header 名")
    void shouldExposeValue() throws NoSuchMethodException {
        Method method = Fixture.class.getMethod("invoke", String.class);
        Parameter parameter = method.getParameters()[0];
        McpHeader annotation = parameter.getAnnotation(McpHeader.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("X-Tenant-Id");
    }

    @Test
    @DisplayName("Target/Retention：仅允许参数，运行时可见")
    void shouldHaveParameterTargetAndRuntimeRetention() {
        assertThat(McpHeader.class.getAnnotation(Target.class))
                .isNotNull()
                .satisfies(target -> assertThat(target.value()).containsExactly(ElementType.PARAMETER));
        assertThat(McpHeader.class.getAnnotation(Retention.class))
                .isNotNull()
                .extracting(Retention::value)
                .isEqualTo(RetentionPolicy.RUNTIME);
    }

    @SuppressWarnings("unused")
    private static final class Fixture {
        public void invoke(@McpHeader("X-Tenant-Id") String tenantId) { }
    }
}
