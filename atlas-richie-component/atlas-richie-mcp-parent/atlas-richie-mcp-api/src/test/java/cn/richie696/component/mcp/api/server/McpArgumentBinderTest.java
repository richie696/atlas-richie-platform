package cn.richie696.component.mcp.api.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpArgumentBinder} 作为函数式接口可被 lambda 直接实现并返回转换结果。
 */
@DisplayName("McpArgumentBinder 参数绑定扩展点")
class McpArgumentBinderTest {

    @Test
    @DisplayName("lambda 实现：返回 rawValue 转换后的目标类型值")
    void shouldReturnConvertedValue() {
        McpArgumentBinder binder = (rawValue, targetType, metadata) -> {
            if (targetType == Integer.class) {
                return Integer.parseInt((String) rawValue);
            }
            return rawValue;
        };
        McpArgumentMetadata metadata = new McpArgumentMetadata("age", "age", true, "0", "int32", "0", List.of(), false);

        Object result = binder.bind("42", Integer.class, metadata);

        assertThat(result).isInstanceOf(Integer.class).isEqualTo(42);
    }

    @Test
    @DisplayName("自定义实现：可抛 McpArgumentBindingException 表达绑定失败")
    void shouldPropagateBindingException() {
        McpArgumentBinder binder = (rawValue, targetType, metadata) -> {
            throw new McpArgumentBindingException(metadata.name(), "unsupported type: " + targetType);
        };
        McpArgumentMetadata metadata = new McpArgumentMetadata("x", "x", true, "", "", "", List.of(), false);

        assertThatThrownBy(() -> binder.bind("v", (Type) Object.class, metadata))
                .isInstanceOf(McpArgumentBindingException.class)
                .hasMessageContaining("unsupported type")
                .extracting("argumentName")
                .isEqualTo("x");
    }

    @Test
    @DisplayName("lambda 实现：metadata 为 null 时仍可被调用（业务需自行判空）")
    void shouldAcceptNullMetadata() {
        McpArgumentBinder binder = (rawValue, targetType, metadata) -> Map.of("raw", rawValue);

        Object result = binder.bind("v", Object.class, null);

        assertThat(result).isInstanceOf(Map.class);
    }
}
