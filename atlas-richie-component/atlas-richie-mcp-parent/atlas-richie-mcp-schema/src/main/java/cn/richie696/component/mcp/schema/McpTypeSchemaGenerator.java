package cn.richie696.component.mcp.schema;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * 将 Java/Kotlin 类型转换为 MCP 协议兼容的 JSON Schema 片段的策略接口。
 *
 * <p>该接口抽象了"反射类型 → JSON Schema"的过程，使 server 端可以在不同时期注入不同的实现：
 * 既有默认的 {@link JacksonMcpTypeSchemaGenerator}（基于 Jackson 反射），也允许业务方自定义
 * （如支持自有注解、忽略某些字段、定制描述等）。产物必须是可序列化为 JSON 的 Map 结构。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpTypeSchemaGenerator {
    /**
     * 生成指定类型的 JSON Schema 片段。
     *
     * @param type 任意 Java/Kotlin 反射类型（{@link Class}、{@link java.lang.reflect.ParameterizedType} 等）
     * @return JSON Schema 片段，键为关键字（{@code type}/{@code properties}/{@code items} 等），值为嵌套 Map 或标量
     * @throws McpSchemaDefinitionException 当类型层级过深、循环引用或类型不支持时抛出
     */
    Map<String, Object> generate(Type type);
}
