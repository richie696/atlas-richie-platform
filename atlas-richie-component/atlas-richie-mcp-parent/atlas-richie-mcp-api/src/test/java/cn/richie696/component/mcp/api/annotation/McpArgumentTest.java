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
 * 通过反射读取 {@code @McpArgument} 注解属性，覆盖默认值与覆盖值两种路径。
 */
@DisplayName("@McpArgument 参数元数据注解")
class McpArgumentTest {

    @Test
    @DisplayName("默认值：未显式覆盖时所有属性回落到注解默认值")
    void shouldExposeDefaultValues() throws NoSuchMethodException {
        Method method = Fixture.class.getMethod("defaultParam", String.class);
        Parameter parameter = method.getParameters()[0];
        McpArgument annotation = parameter.getAnnotation(McpArgument.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEmpty();
        assertThat(annotation.description()).isEmpty();
        assertThat(annotation.required()).isTrue();
        assertThat(annotation.defaultValue()).isEqualTo(McpArgument.NO_DEFAULT);
        assertThat(annotation.format()).isEmpty();
        assertThat(annotation.example()).isEmpty();
        assertThat(annotation.enumValues()).isEmpty();
        assertThat(annotation.minimum()).isEmpty();
        assertThat(annotation.maximum()).isEmpty();
        assertThat(annotation.minLength()).isEqualTo(-1);
        assertThat(annotation.maxLength()).isEqualTo(-1);
        assertThat(annotation.sensitive()).isFalse();
    }

    @Test
    @DisplayName("未标注时 getAnnotation 返回 null：业务应当自己处理 null")
    void shouldReturnNullWhenAnnotationMissing() throws NoSuchMethodException {
        Method method = Fixture.class.getMethod("noAnnotation", String.class);
        Parameter parameter = method.getParameters()[0];

        assertThat(parameter.getAnnotation(McpArgument.class)).isNull();
    }

    @Test
    @DisplayName("覆盖值：所有显式属性都被正确读取")
    void shouldExposeOverriddenValues() throws NoSuchMethodException {
        Method method = Fixture.class.getMethod("configured", String.class, int.class);
        Parameter idParam = method.getParameters()[0];
        Parameter countParam = method.getParameters()[1];
        McpArgument idAnnotation = idParam.getAnnotation(McpArgument.class);
        McpArgument countAnnotation = countParam.getAnnotation(McpArgument.class);

        assertThat(idAnnotation.name()).isEqualTo("id");
        assertThat(idAnnotation.description()).isEqualTo("primary id");
        assertThat(idAnnotation.required()).isTrue();
        assertThat(idAnnotation.format()).isEqualTo("uuid");
        assertThat(idAnnotation.example()).isEqualTo("00000000-0000-0000-0000-000000000000");
        assertThat(idAnnotation.enumValues()).containsExactly("A", "B", "C");
        assertThat(idAnnotation.sensitive()).isTrue();

        assertThat(countAnnotation.name()).isEqualTo("count");
        assertThat(countAnnotation.required()).isFalse();
        assertThat(countAnnotation.defaultValue()).isEqualTo("10");
        assertThat(countAnnotation.minimum()).isEqualTo("0");
        assertThat(countAnnotation.maximum()).isEqualTo("100");
        assertThat(countAnnotation.minLength()).isEqualTo(0);
        assertThat(countAnnotation.maxLength()).isEqualTo(8);
        assertThat(countAnnotation.sensitive()).isFalse();
    }

    @Test
    @DisplayName("NO_DEFAULT 哨兵：使用 \\u0000 字符可被可靠区分")
    void noDefaultSentinelShouldBeUnicodeNull() {
        assertThat(McpArgument.NO_DEFAULT).isEqualTo("\u0000");
        assertThat(McpArgument.NO_DEFAULT).hasSize(1);
    }

    @Test
    @DisplayName("Target/Retention：仅允许参数，且运行时可见")
    void shouldHaveParameterTargetAndRuntimeRetention() {
        assertThat(McpArgument.class.getAnnotation(Target.class))
                .isNotNull()
                .satisfies(target -> {
                    ElementType[] types = target.value();
                    assertThat(types).containsExactly(ElementType.PARAMETER);
                });
        assertThat(McpArgument.class.getAnnotation(Retention.class))
                .isNotNull()
                .extracting(Retention::value)
                .isEqualTo(RetentionPolicy.RUNTIME);
    }

    @SuppressWarnings("unused")
    private static final class Fixture {
        public void defaultParam(@McpArgument String value) { }

        public void noAnnotation(String value) { }

        public void configured(
                @McpArgument(name = "id", description = "primary id", format = "uuid",
                        example = "00000000-0000-0000-0000-000000000000",
                        enumValues = {"A", "B", "C"}, sensitive = true) String id,
                @McpArgument(name = "count", required = false, defaultValue = "10",
                        minimum = "0", maximum = "100", minLength = 0, maxLength = 8) int count) { }
    }
}
