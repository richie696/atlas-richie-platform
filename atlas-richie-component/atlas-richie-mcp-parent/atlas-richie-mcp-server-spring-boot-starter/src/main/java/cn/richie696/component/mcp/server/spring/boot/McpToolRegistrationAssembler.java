package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.server.McpToolHandler;
import cn.richie696.component.mcp.api.server.McpToolHandlerProvider;
import cn.richie696.component.mcp.api.server.McpToolDefinition;
import cn.richie696.component.mcp.api.server.McpToolDefinitionSource;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 合并多源 Tool 注册项：编程式 Bean、{@code @McpTool} 注解、{@code tools.definitions}
 * 配置以及 {@link McpToolDefinitionSource}，并应用 {@code tools.overrides} 补丁。
 *
 * <p>装配顺序经过精心设计以保证确定性：
 * <ol>
 *     <li>编程式 {@link McpToolRegistration} Bean（基线；同名冲突直接 fail-fast）；</li>
 *     <li>{@link McpAnnotatedToolRegistrar} 扫描出的 {@code @McpTool}；</li>
 *     <li>{@code tools.definitions}（按 TreeMap key 排序）；</li>
 *     <li>{@link McpToolDefinitionSource} 列表（按 {@link McpToolDefinitionSource#order()} 升序，
 *         同 order 内按 {@code sourceId} 字典序）；</li>
 *     <li>{@code tools.overrides}（按 TreeMap key 排序，仅覆写非空字段）；</li>
 *     <li>最终过滤：{@code enabled=false} 被丢弃，未在 {@code enabledGroups} 内的 Tool 被丢弃。</li>
 * </ol>
 * 装配结果是稳定的不可变列表，可由 {@link cn.richie696.component.mcp.server.tool.McpToolRegistry}
 * 在幂等刷新时复用。
 *
 * <p>{@code fail-fast} 关闭时，配置错误仅记录 WARN 而不抛异常，便于灰度期间容忍部分配置缺失。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpToolRegistrationAssembler {
    private static final System.Logger LOGGER =
            System.getLogger(McpToolRegistrationAssembler.class.getName());

    private final List<McpToolRegistration> legacyRegistrations;
    private final McpAnnotatedToolRegistrar annotatedRegistrar;
    private final Map<String, McpToolHandler> handlers;
    private final List<McpToolDefinitionSource> definitionSources;
    private volatile McpServerProperties properties;

    /**
     * 构造装配器并解析所有 Handler 引用。
     *
     * <p>构造阶段会校验 {@code handlerRef} 非空、唯一，以及 {@link McpToolHandler}
     * 自身非空；冲突直接抛异常，避免运行期才发现。</p>
     *
     * @param legacyRegistrations 编程式注册的 Tool 列表
     * @param annotatedRegistrar 注解扫描器
     * @param handlerProviders 外部 HandlerProvider 列表
     * @param definitionSources 外部 DefinitionSource 列表
     * @param properties MCP 配置
     * @throws NullPointerException 当关键参数为 {@code null} 时
     * @throws IllegalArgumentException 当 {@code handlerRef} 为空或重复时
     */
    public McpToolRegistrationAssembler(
            Collection<McpToolRegistration> legacyRegistrations,
            McpAnnotatedToolRegistrar annotatedRegistrar,
            Collection<McpToolHandlerProvider> handlerProviders,
            Collection<McpToolDefinitionSource> definitionSources,
            McpServerProperties properties) {
        this.legacyRegistrations = List.copyOf(legacyRegistrations);
        this.annotatedRegistrar = Objects.requireNonNull(annotatedRegistrar, "annotatedRegistrar");
        this.properties = Objects.requireNonNull(properties, "properties");
        TreeMap<String, McpToolHandler> resolvedHandlers = new TreeMap<>();
        for (McpToolHandlerProvider provider : handlerProviders) {
            String reference = required(provider.handlerRef(), "handlerRef");
            McpToolHandler previous = resolvedHandlers.putIfAbsent(
                    reference, Objects.requireNonNull(provider.handler(), "handler"));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate MCP tool handlerRef: " + reference);
            }
        }
        this.handlers = Map.copyOf(resolvedHandlers);
        this.definitionSources = definitionSources.stream()
                .sorted(java.util.Comparator.comparingInt(McpToolDefinitionSource::order)
                        .thenComparing(McpToolDefinitionSource::sourceId))
                .toList();
    }

    /**
     * 执行一次装配：合并所有注册源、应用 overrides、过滤禁用 / 未启用分组。
     *
     * @return 不可变的注册项列表，按 Tool 名字典序
     */
    public List<McpToolRegistration> assemble() {
        McpServerProperties.Tools tools = properties.getTools();
        TreeMap<String, McpToolRegistration> registrations = new TreeMap<>();
        legacyRegistrations.forEach(registration -> add(
                registrations, registration, "McpToolRegistration bean"));
        if (!tools.isEnabled()) return List.copyOf(registrations.values());

        annotatedRegistrar.registrations().forEach(registration -> add(
                registrations, registration, "@McpTool"));
        for (Map.Entry<String, McpServerProperties.ToolDefinition> entry
                : new TreeMap<>(tools.getDefinitions()).entrySet()) {
            try {
                add(registrations, configured(entry.getKey(), entry.getValue()),
                        "configuration definition");
            } catch (RuntimeException exception) {
                handle("Invalid MCP tool definition " + entry.getKey(), exception);
            }
        }
        applyDefinitionSources(registrations);

        for (Map.Entry<String, McpServerProperties.ToolOverride> entry
                : new TreeMap<>(tools.getOverrides()).entrySet()) {
            McpToolRegistration registration = registrations.get(entry.getKey());
            if (registration == null) {
                String overrideGroup = normalize(entry.getValue().getGroup());
                if (overrideGroup != null
                        && !tools.getEnabledGroups().isEmpty()
                        && !tools.getEnabledGroups().contains(overrideGroup)) {
                    continue;
                }
                handle("MCP tool override targets unknown tool " + entry.getKey(),
                        new IllegalArgumentException("Unknown MCP tool override: " + entry.getKey()));
                continue;
            }
            registrations.put(entry.getKey(), patch(registration, entry.getValue()));
        }

        List<McpToolRegistration> result = new ArrayList<>();
        for (McpToolRegistration registration : registrations.values()) {
            McpToolRegistration withDefaults = defaults(registration, tools.getDefaults());
            Map<String, Object> annotations = withDefaults.descriptor().annotations();
            if (Boolean.FALSE.equals(annotations.get("enabled"))) continue;
            if (!tools.getEnabledGroups().isEmpty()) {
                Object group = annotations.get("group");
                if (!(group instanceof String value)
                        || !tools.getEnabledGroups().contains(value)) continue;
            }
            result.add(withDefaults);
        }
        return List.copyOf(result);
    }

    private void applyDefinitionSources(Map<String, McpToolRegistration> registrations) {
        Integer currentOrder = null;
        java.util.Set<String> namesAtOrder = new java.util.HashSet<>();
        for (McpToolDefinitionSource source : definitionSources) {
            if (!Objects.equals(currentOrder, source.order())) {
                currentOrder = source.order();
                namesAtOrder.clear();
            }
            Collection<McpToolDefinition> loaded = Objects.requireNonNull(
                    source.load(), "MCP definition source returned null: " + source.sourceId());
            for (McpToolDefinition definition : loaded) {
                if (!namesAtOrder.add(definition.name())) {
                    handle("Duplicate MCP tool definition at order " + source.order()
                                    + ": " + definition.name(),
                            new IllegalArgumentException(
                                    "Duplicate MCP tool definition at the same order: "
                                            + definition.name()));
                    continue;
                }
                try {
                    if (!definition.enabled()) {
                        registrations.remove(definition.name());
                        continue;
                    }
                    registrations.put(definition.name(), compileDefinition(
                            definition, registrations.get(definition.name())));
                } catch (RuntimeException exception) {
                    handle("Invalid MCP tool definition from " + source.sourceId()
                            + ": " + definition.name(), exception);
                }
            }
        }
    }

    private McpToolRegistration compileDefinition(
            McpToolDefinition definition,
            McpToolRegistration existing) {
        McpToolHandler handler;
        if (existing != null) {
            Object existingHandlerRef = existing.descriptor().annotations().get("handlerRef");
            if (definition.handlerRef() != null
                    && !definition.handlerRef().equals(existingHandlerRef)) {
                throw new IllegalArgumentException(
                        "External configuration cannot replace the handler of existing tool: "
                                + definition.name());
            }
            handler = existing.handler();
        } else {
            handler = handlers.get(definition.handlerRef());
            if (handler == null) {
                throw new IllegalArgumentException(
                        "Unknown MCP tool handlerRef: " + definition.handlerRef());
            }
        }
        McpToolDescriptor baseline = existing == null ? null : existing.descriptor();
        Map<String, Object> annotations = baseline == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(baseline.annotations());
        annotations.putAll(definition.annotations());
        annotations.put("enabled", definition.enabled());
        if (!definition.requiredScopes().isEmpty()) {
            annotations.put("requiredScopes", List.copyOf(definition.requiredScopes()));
        }
        if (definition.timeout() != null) annotations.put("timeoutMs", definition.timeout().toMillis());
        if (definition.group() != null) annotations.put("group", definition.group());
        if (!definition.policies().isEmpty()) annotations.put("policies", definition.policies());
        Map<String, Object> inputSchema = !definition.inputSchema().isEmpty()
                ? definition.inputSchema()
                : baseline == null ? Map.of("type", "object") : baseline.inputSchema();
        Map<String, Object> outputSchema = !definition.outputSchema().isEmpty()
                ? definition.outputSchema()
                : baseline == null ? Map.of() : baseline.outputSchema();
        return new McpToolRegistration(new McpToolDescriptor(
                definition.name(),
                definition.title() != null ? definition.title() : baseline == null ? null : baseline.title(),
                definition.description() != null
                        ? definition.description() : baseline == null ? null : baseline.description(),
                inputSchema,
                outputSchema,
                annotations), handler);
    }

    /**
     * 同步更新 MCP 配置，同时刷新内部缓存的注解扫描器。
     *
     * <p>当扫描包或 Bean 名集合变化时，{@link McpAnnotatedToolRegistrar} 会清空
     * 已缓存的注册项，下一次 {@link #assemble()} 将重新扫描。</p>
     *
     * @param properties 新 MCP 配置
     * @throws NullPointerException 当 {@code properties} 为 {@code null} 时
     */
    public synchronized void updateProperties(McpServerProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        annotatedRegistrar.updateProperties(properties.getTools());
    }

    private McpToolRegistration configured(
            String name,
            McpServerProperties.ToolDefinition definition) {
        String handlerRef = required(definition.getHandlerRef(), "handler-ref");
        McpToolHandler handler = handlers.get(handlerRef);
        if (handler == null) {
            throw new IllegalArgumentException(
                    "Unknown MCP tool handlerRef: " + handlerRef);
        }
        Map<String, Object> annotations = definition.getAnnotations() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(definition.getAnnotations());
        annotations.put("handlerRef", handlerRef);
        applyPatchAnnotations(annotations, definition);
        Map<String, Object> inputSchema = definition.getInputSchema() == null
                ? Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)
                : definition.getInputSchema();
        Map<String, Object> outputSchema = definition.getOutputSchema() == null
                ? Map.of()
                : definition.getOutputSchema();
        return new McpToolRegistration(new McpToolDescriptor(
                name,
                normalize(definition.getTitle()),
                normalize(definition.getDescription()),
                inputSchema,
                outputSchema,
                annotations), handler);
    }

    private McpToolRegistration patch(
            McpToolRegistration registration,
            McpServerProperties.ToolOverride override) {
        McpToolDescriptor descriptor = registration.descriptor();
        Map<String, Object> annotations = new LinkedHashMap<>(descriptor.annotations());
        if (override.getAnnotations() != null) annotations.putAll(override.getAnnotations());
        applyPatchAnnotations(annotations, override);
        Map<String, Object> inputSchema = override.getInputSchema() == null
                ? descriptor.inputSchema() : override.getInputSchema();
        Map<String, Object> outputSchema = override.getOutputSchema() == null
                ? descriptor.outputSchema() : override.getOutputSchema();
        McpToolDescriptor patched = new McpToolDescriptor(
                descriptor.name(),
                override.getTitle() == null ? descriptor.title() : normalize(override.getTitle()),
                override.getDescription() == null
                        ? descriptor.description() : normalize(override.getDescription()),
                inputSchema,
                outputSchema,
                annotations);
        return new McpToolRegistration(patched, registration.handler());
    }

    private McpToolRegistration defaults(
            McpToolRegistration registration,
            McpServerProperties.ToolDefaults defaults) {
        Map<String, Object> annotations = new LinkedHashMap<>(registration.descriptor().annotations());
        annotations.putIfAbsent("audit", defaults.isAuditEnabled());
        if (defaults.getTimeout() != null) {
            annotations.putIfAbsent("timeoutMs", defaults.getTimeout().toMillis());
        }
        if (annotations.equals(registration.descriptor().annotations())) return registration;
        McpToolDescriptor descriptor = registration.descriptor();
        return new McpToolRegistration(new McpToolDescriptor(
                descriptor.name(), descriptor.title(), descriptor.description(),
                descriptor.inputSchema(), descriptor.outputSchema(), annotations),
                registration.handler());
    }

    private void applyPatchAnnotations(
            Map<String, Object> annotations,
            McpServerProperties.ToolOverride override) {
        if (override.getEnabled() != null) annotations.put("enabled", override.getEnabled());
        if (override.getGroup() != null) annotations.put("group", override.getGroup());
        if (override.getTimeout() != null) annotations.put("timeoutMs", override.getTimeout().toMillis());
        if (override.getAuditEnabled() != null) annotations.put("audit", override.getAuditEnabled());
        if (override.getRequiredScopes() != null) {
            annotations.put("requiredScopes", List.copyOf(override.getRequiredScopes()));
        }
        if (override.getPolicies() != null) annotations.put("policies", override.getPolicies());
    }

    private void add(
            Map<String, McpToolRegistration> registrations,
            McpToolRegistration registration,
            String source) {
        McpToolRegistration previous = registrations.putIfAbsent(
                registration.descriptor().name(), registration);
        if (previous != null) {
            handle("Duplicate MCP tool name " + registration.descriptor().name()
                    + " from " + source,
                    new IllegalArgumentException(
                            "Duplicate MCP tool name: " + registration.descriptor().name()));
        }
    }

    private void handle(String message, RuntimeException exception) {
        if (properties.getTools().isFailFast()) throw exception;
        LOGGER.log(System.Logger.Level.WARNING, message, exception);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MCP tool " + field + " must not be blank");
        }
        return value;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
