package cn.richie696.component.mcp.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 与具体协议版本无关的 Tool 描述。
 *
 * <p>该 record 是 Tool 在业务层的统一描述载体：业务名/标题/描述是稳定字段，Schema
 * 与 annotations 则以 {@code Map<String, Object>} 表达，这样既保持协议无关（不直接
 * 暴露 JSON Schema 类型），又允许底层协议自由扩展（v2024 → v2025 增加字段时不会破坏
 * 该 record）。</p>
 *
 * <p>关键设计：紧凑构造器递归地把 Schema 内部的所有 {@code Map/List/Set} 做防御性不可变
 * 包装，防止外部突变污染 Tool 元数据，进而影响后续协议序列化与权限判定。</p>
 *
 * @param name Tool 名（必填）
 * @param title 标题
 * @param description 用途描述
 * @param inputSchema 输入 JSON Schema
 * @param outputSchema 输出 JSON Schema
 * @param annotations 协议无关的扩展注解
 * @author richie696
 * @since 2026-08-11
 */
public record McpToolDescriptor(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Map<String, Object> annotations) {

    /**
     * 紧凑构造器：必填校验 + 递归不可变包装。
     *
     * @param name Tool 名
     * @param title 标题
     * @param description 描述
     * @param inputSchema 输入 Schema
     * @param outputSchema 输出 Schema
     * @param annotations 扩展注解
     * @throws NullPointerException 当 {@code name} 为 {@code null} 时
     */
    public McpToolDescriptor {
        name = Objects.requireNonNull(name, "name");
        inputSchema = immutableMap(inputSchema);
        outputSchema = immutableMap(outputSchema);
        annotations = immutableMap(annotations);
    }

    /**
     * 把任意 Map 递归转换为不可变结构（Map/List/Set 都会被不可变包装）。
     *
     * @param source 源 Map
     * @return 不可变 Map；源为 {@code null} 或空时返回 {@link Map#of()}
     */
    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(result);
    }

    /**
     * 递归包装任意值：Map/List/Set 都会被转换为不可变结构，其他值原样保留。
     *
     * @param value 任意值
     * @return 不可变包装后的值
     */
    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> result = new LinkedHashMap<>();
            map.forEach((key, entry) -> result.put(key, immutableValue(entry)));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(McpToolDescriptor::immutableValue).toList();
        }
        if (value instanceof Set<?> set) return set.stream()
                .map(McpToolDescriptor::immutableValue)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return value;
    }
}
