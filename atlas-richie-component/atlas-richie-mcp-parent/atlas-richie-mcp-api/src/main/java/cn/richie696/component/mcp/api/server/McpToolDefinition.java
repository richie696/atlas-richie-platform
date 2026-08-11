package cn.richie696.component.mcp.api.server;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Framework-neutral, fully merged definition of one server tool.
 *
 * <p>该 record 是 Tool 在运行期的"完全合并定义"：把注解扫描结果（{@code @McpTool} +
 * {@code @McpArgument}）与部署期配置覆盖（YAML 中的策略、超时、Scope 列表）合并后的
 * 不可变快照。运行时所有拦截器、协议适配层、调用链都面向该 record 工作，而不是直接
 * 处理注解反射或 YAML——这样既保证运行时的高效访问，也使"配置覆盖注解"成为单向流程。</p>
 *
 * <p>关键校验：</p>
 * <ul>
 *   <li>Tool 名必须匹配 {@code [A-Za-z0-9_.-]{1,128}}，避免特殊字符破坏协议序列化；</li>
 *   <li>{@code timeout} 必须为正；零或负值在 MCP 语义下没有意义；</li>
 *   <li>所有 Map/Set 字段都会被不可变包装，防止外部突变污染全局 Tool 表。</li>
 * </ul>
 *
 * @param name Tool 名
 * @param title 标题
 * @param description 描述
 * @param enabled 是否启用
 * @param handlerRef 处理器引用（指向 {@link McpToolHandlerProvider}）
 * @param inputSchema 输入 JSON Schema
 * @param outputSchema 输出 JSON Schema
 * @param annotations 协议无关注解
 * @param requiredScopes 所需 Scope
 * @param timeout 超时时间
 * @param group 逻辑分组
 * @param policies 部署期策略
 * @author richie696
 * @since 2026-08-11
 */
public record McpToolDefinition(
        String name,
        String title,
        String description,
        boolean enabled,
        String handlerRef,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Map<String, Object> annotations,
        Set<String> requiredScopes,
        Duration timeout,
        String group,
        Map<String, Object> policies) {

    /**
     * Tool 名称合法性校验正则：仅允许字母数字与 {@code _.-}，长度 1-128。
     */
    private static final Pattern TOOL_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,128}");

    /**
     * 紧凑构造器：执行 Tool 名正则校验、超时正数校验与所有 Map/Set 字段的不可变包装。
     *
     * @param name Tool 名
     * @param title 标题
     * @param description 描述
     * @param enabled 是否启用
     * @param handlerRef 处理器引用
     * @param inputSchema 输入 Schema
     * @param outputSchema 输出 Schema
     * @param annotations 注解
     * @param requiredScopes 所需 Scope
     * @param timeout 超时
     * @param group 分组
     * @param policies 策略
     * @throws NullPointerException 当 {@code name} 为 {@code null} 时
     * @throws IllegalArgumentException 当 {@code name} 非法或 {@code timeout} 非正时
     */
    public McpToolDefinition {
        name = Objects.requireNonNull(name, "name");
        if (!TOOL_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "MCP tool name must match [A-Za-z0-9_.-]{1,128}: " + name);
        }
        inputSchema = immutableMap(inputSchema);
        outputSchema = immutableMap(outputSchema);
        annotations = immutableMap(annotations);
        requiredScopes = requiredScopes == null ? Set.of() : Set.copyOf(requiredScopes);
        policies = immutableMap(policies);
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("MCP tool timeout must be positive: " + name);
        }
        group = group == null || group.isBlank() ? null : group;
        handlerRef = handlerRef == null || handlerRef.isBlank() ? null : handlerRef;
    }

    /**
     * 把任意 Map 递归转换为不可变结构（与 {@link cn.richie696.component.mcp.api.model.McpToolDescriptor}
     * 中的同名方法行为一致，确保 Tool 元数据跨类型不可变）。
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
            return list.stream().map(McpToolDefinition::immutableValue).toList();
        }
        if (value instanceof Set<?> set) return set.stream()
                .map(McpToolDefinition::immutableValue)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return value;
    }
}
