package cn.richie696.component.mcp.schema;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link JacksonMcpTypeSchemaGenerator} 把 Java 类型映射为 JSON Schema 2020-12
 * 描述时对 record / enum / 集合 / 嵌套对象 / 原始 {@code Map<String,Object>} 等
 * 常见形态的输出：record 字段映射为 {@code properties}；枚举映射为 {@code enum}；
 * 嵌套数组类型的 {@code items} 必须仍为对象；{@code Map<String,Object>} 视为
 * 完全无约束的 JSON 值（{@code additionalProperties: {}}）。
 *
 * @author richie696
 * @since 2026-08-11
 */
class JacksonMcpTypeSchemaGeneratorTest {
    private final JacksonMcpTypeSchemaGenerator generator =
            new JacksonMcpTypeSchemaGenerator();

    @Test
    void generatesRecordEnumCollectionAndNestedSchemas() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("accept", Request.class);

        Map<String, Object> schema = generator.generate(method.getGenericParameterTypes()[0]);

        assertThat(schema).containsEntry("type", "object");
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKeys("storeId", "status", "lines");
        assertThat(properties.get("status")).isEqualTo(
                Map.of("type", "string", "enum", List.of("ACTIVE", "INACTIVE")));
        @SuppressWarnings("unchecked")
        Map<String, Object> lines = (Map<String, Object>) properties.get("lines");
        assertThat(lines).containsEntry("type", "array");
        assertThat(lines.get("items")).isInstanceOf(Map.class);
    }

    @Test
    void treatsObjectMapValuesAsUnconstrainedJsonValues() throws Exception {
        Method method = Fixture.class.getDeclaredMethod("response");

        Map<String, Object> schema = generator.generate(method.getGenericReturnType());

        assertThat(schema).containsEntry("type", "object");
        assertThat(schema.get("additionalProperties")).isEqualTo(Map.of());
    }

    private static final class Fixture {
        void accept(Request request) {
        }

        Map<String, Object> response() {
            return Map.of();
        }
    }

    private record Request(String storeId, Status status, List<Line> lines) {
    }

    private record Line(String itemCode, int quantity) {
    }

    private enum Status {
        ACTIVE,
        INACTIVE
    }
}
