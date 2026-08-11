package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.schema.McpCompiledSchema;
import cn.richie696.component.mcp.schema.McpJsonSchemaValidator;
import cn.richie696.component.mcp.schema.McpJsonSchemaValidators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Thread-safe, atomically replaceable and authorization-aware Tool registry. */
/**
 * 线程安全、原子可替换的 Tool 注册表：维护工具集快照并强制访问控制。
 *
 * <p>设计要点：
 * <ul>
 *   <li>使用 {@link AtomicReference} 持有 {@link McpToolRegistryState}，
 *       所有写操作通过 CAS 重试直到成功，保证"读侧看到的永远是某一完整时刻的快照"。</li>
 *   <li>注册时即调用 {@link McpJsonSchemaValidator} 预编译入参与出参 Schema，
 *       避免请求热路径上重复解析 JSON Schema。</li>
 *   <li>所有写操作完成后通过 {@link McpToolRegistryChangeListener} 通知变更，
 *       监听器异常被捕获并记录，绝不影响主调用流程。</li>
 *   <li>对外暴露的可见性受 {@link McpToolVisibilityPolicy} 控制，未授权 Tool
 *       对调用方不可见（同时 {@link #resolveAuthorized} 也会拒绝调用）。</li>
 * </ul>
 * </p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpToolRegistry {
    private static final Pattern RECOMMENDED_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,128}");
    private static final System.Logger LOGGER = System.getLogger(McpToolRegistry.class.getName());

    private final AtomicReference<McpToolRegistryState> state =
            new AtomicReference<>(new McpToolRegistryState(0, new TreeMap<>()));
    private final CopyOnWriteArrayList<McpToolRegistryChangeListener> listeners =
            new CopyOnWriteArrayList<>();
    private final McpToolVisibilityPolicy visibilityPolicy;
    private final McpJsonSchemaValidator schemaValidator;

    /**
     * 使用默认可见性策略（全部放行）与安全默认 JSON Schema 校验器构造注册表。
     */
    public McpToolRegistry() {
        this(McpToolVisibilityPolicy.ALLOW_ALL, McpJsonSchemaValidators.secureDefaults());
    }

    /**
     * 使用自定义可见性策略与安全默认 JSON Schema 校验器构造注册表。
     *
     * @param visibilityPolicy 可见性策略，不能为 null
     */
    public McpToolRegistry(McpToolVisibilityPolicy visibilityPolicy) {
        this(visibilityPolicy, McpJsonSchemaValidators.secureDefaults());
    }

    /**
     * 完整构造：自定义可见性策略与 JSON Schema 校验器。
     *
     * @param visibilityPolicy 可见性策略，不能为 null
     * @param schemaValidator  JSON Schema 预编译/校验器，不能为 null
     */
    public McpToolRegistry(
            McpToolVisibilityPolicy visibilityPolicy,
            McpJsonSchemaValidator schemaValidator) {
        this.visibilityPolicy = Objects.requireNonNull(visibilityPolicy, "visibilityPolicy");
        this.schemaValidator = Objects.requireNonNull(schemaValidator, "schemaValidator");
    }

    /**
     * 注册单个 Tool；若同名 Tool 已存在则抛 {@link IllegalArgumentException}。
     *
     * @param registration 工具注册项，不能为 null
     * @return 操作完成后的注册表版本号
     * @throws IllegalArgumentException 当 Tool 名重复或描述符非法时
     */
    public long register(McpToolRegistration registration) {
        McpResolvedTool resolved = resolve(registration);
        String name = registration.descriptor().name();
        while (true) {
            McpToolRegistryState current = state.get();
            if (current.tools().containsKey(name)) {
                throw new IllegalArgumentException("Duplicate MCP tool name: " + name);
            }
            TreeMap<String, McpResolvedTool> nextTools = new TreeMap<>(current.tools());
            nextTools.put(name, resolved);
            McpToolRegistryState next = new McpToolRegistryState(current.revision() + 1, nextTools);
            if (state.compareAndSet(current, next)) {
                notifyChanged(new McpToolRefreshResult(
                        current.revision(), next.revision(), Set.of(name), Set.of(), Set.of()));
                return next.revision();
            }
        }
    }

    /**
     * 注销指定名称的 Tool；若不存在则视为幂等无操作，返回当前版本号。
     *
     * @param name 工具名，不能为 null
     * @return 操作完成后的注册表版本号
     */
    public long unregister(String name) {
        Objects.requireNonNull(name, "name");
        while (true) {
            McpToolRegistryState current = state.get();
            if (!current.tools().containsKey(name)) {
                return current.revision();
            }
            TreeMap<String, McpResolvedTool> nextTools = new TreeMap<>(current.tools());
            nextTools.remove(name);
            McpToolRegistryState next = new McpToolRegistryState(current.revision() + 1, nextTools);
            if (state.compareAndSet(current, next)) {
                notifyChanged(new McpToolRefreshResult(
                        current.revision(), next.revision(), Set.of(), Set.of(name), Set.of()));
                return next.revision();
            }
        }
    }

    /**
     * 原子地添加或替换单个 Tool；新旧注册等价时返回 {@link McpToolRefreshResult#unchanged}。
     *
     * @param registration 工具注册项
     * @return 描述本次变更差异的 {@link McpToolRefreshResult}
     */
    public McpToolRefreshResult replace(McpToolRegistration registration) {
        McpResolvedTool resolved = resolve(registration);
        String name = registration.descriptor().name();
        while (true) {
            McpToolRegistryState current = state.get();
            McpResolvedTool previous = current.tools().get(name);
            if (previous != null && previous.registration().equals(registration)) {
                return McpToolRefreshResult.unchanged(current.revision());
            }
            TreeMap<String, McpResolvedTool> nextTools = new TreeMap<>(current.tools());
            nextTools.put(name, resolved);
            McpToolRegistryState next = new McpToolRegistryState(current.revision() + 1, nextTools);
            if (state.compareAndSet(current, next)) {
                McpToolRefreshResult result = previous == null
                        ? new McpToolRefreshResult(current.revision(), next.revision(),
                                Set.of(name), Set.of(), Set.of())
                        : new McpToolRefreshResult(current.revision(), next.revision(),
                                Set.of(), Set.of(), Set.of(name));
                notifyChanged(result);
                return result;
            }
        }
    }

    /**
     * 原子地替换整个 Tool 集合：先完整预编译候选集再 CAS，避免"半替换"导致中间态对外暴露。
     *
     * @param registrations 新一轮完整 Tool 注册项集合
     * @return 描述本次变更差异的 {@link McpToolRefreshResult}
     * @throws IllegalArgumentException 当候选集合内部存在重名 Tool 时
     */
    public McpToolRefreshResult replaceAll(Collection<McpToolRegistration> registrations) {
        Objects.requireNonNull(registrations, "registrations");
        TreeMap<String, McpResolvedTool> candidates = new TreeMap<>();
        for (McpToolRegistration registration : registrations) {
            McpResolvedTool resolved = resolve(registration);
            String name = registration.descriptor().name();
            if (candidates.putIfAbsent(name, resolved) != null) {
                throw new IllegalArgumentException("Duplicate MCP tool name: " + name);
            }
        }

        while (true) {
            McpToolRegistryState current = state.get();
            if (sameRegistrations(current.tools(), candidates)) {
                return McpToolRefreshResult.unchanged(current.revision());
            }
            McpToolRegistryState next = new McpToolRegistryState(current.revision() + 1, candidates);
            McpToolRefreshResult result = difference(current, next);
            if (state.compareAndSet(current, next)) {
                notifyChanged(result);
                return result;
            }
        }
    }

    /**
     * 生成当前调用上下文"可见"的工具快照，列表已按可见性策略过滤。
     *
     * @param context 当前调用上下文
     * @return 不可变快照
     */
    public McpToolRegistrySnapshot snapshot(McpCallContext context) {
        Objects.requireNonNull(context, "context");
        McpToolRegistryState current = state.get();
        var visible = current.tools().values().stream()
                .map(tool -> tool.registration().descriptor())
                .filter(descriptor -> visibilityPolicy.isVisible(descriptor, context))
                .toList();
        return new McpToolRegistrySnapshot(current.revision(), visible);
    }

    /**
     * 解析并授权指定 Tool 的注册项；不可见或不存在时抛协议异常。
     *
     * @param name    工具名
     * @param context 当前调用上下文
     * @return 已通过授权校验的 {@link McpToolRegistration}
     * @throws McpProtocolException 当 Tool 不存在或对当前上下文不可见时
     */
    public McpToolRegistration requireAuthorized(String name, McpCallContext context) {
        return resolveAuthorized(name, context).registration();
    }

    /**
     * 解析并授权指定 Tool 的完整解析态（含预编译 Schema），供调用方复用校验器。
     *
     * @param name    工具名
     * @param context 当前调用上下文
     * @return 已通过授权校验的 {@link McpResolvedTool}
     * @throws McpProtocolException 当 Tool 不存在或对当前上下文不可见时
     */
    public McpResolvedTool resolveAuthorized(String name, McpCallContext context) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(context, "context");
        McpToolRegistryState current = state.get();
        McpResolvedTool resolved = current.tools().get(name);
        if (resolved == null
                || !visibilityPolicy.isVisible(resolved.registration().descriptor(), context)) {
            throw new McpProtocolException(
                    "MCP_UNKNOWN_TOOL",
                    -32602,
                    "Unknown tool: " + name,
                    Map.of("name", name));
        }
        return resolved;
    }

    /**
     * 返回当前注册表版本号。
     *
     * @return 自增版本号
     */
    public long revision() {
        return state.get().revision();
    }

    /**
     * 返回当前不可变快照（包含全部 Tool，不做可见性过滤）。
     *
     * @return 当前 {@link McpToolRegistryState}
     */
    public McpToolRegistryState state() {
        return state.get();
    }

    /**
     * 注册一个变更监听器，在原子变更成功后被调用。
     *
     * @param listener 监听器实例，不能为 null
     */
    public void addChangeListener(McpToolRegistryChangeListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * 移除一个变更监听器。
     *
     * @param listener 待移除的监听器实例
     */
    public void removeChangeListener(McpToolRegistryChangeListener listener) {
        listeners.remove(listener);
    }

    private McpResolvedTool resolve(McpToolRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        validateDescriptor(registration.descriptor());
        McpCompiledSchema inputSchema = schemaValidator.compile(registration.descriptor().inputSchema());
        McpCompiledSchema outputSchema = registration.descriptor().outputSchema().isEmpty()
                ? null
                : schemaValidator.compile(registration.descriptor().outputSchema());
        return new McpResolvedTool(registration, inputSchema, outputSchema);
    }

    private boolean sameRegistrations(
            NavigableMap<String, McpResolvedTool> current,
            NavigableMap<String, McpResolvedTool> candidates) {
        if (!current.keySet().equals(candidates.keySet())) {
            return false;
        }
        for (String name : current.keySet()) {
            if (!current.get(name).registration().equals(candidates.get(name).registration())) {
                return false;
            }
        }
        return true;
    }

    private McpToolRefreshResult difference(
            McpToolRegistryState current,
            McpToolRegistryState next) {
        Set<String> added = new TreeSet<>(next.tools().keySet());
        added.removeAll(current.tools().keySet());
        Set<String> removed = new TreeSet<>(current.tools().keySet());
        removed.removeAll(next.tools().keySet());
        Set<String> updated = new TreeSet<>();
        for (String name : current.tools().keySet()) {
            McpResolvedTool candidate = next.tools().get(name);
            if (candidate != null && !current.tools().get(name).registration()
                    .equals(candidate.registration())) {
                updated.add(name);
            }
        }
        return new McpToolRefreshResult(
                current.revision(), next.revision(), added, removed, updated);
    }

    private void notifyChanged(McpToolRefreshResult result) {
        // 中文说明：使用 ArrayList 拷贝是为了避免监听器在回调中通过 add/remove 修改列表造成 ConcurrentModificationException
        for (McpToolRegistryChangeListener listener : new ArrayList<>(listeners)) {
            try {
                listener.onChanged(result);
            } catch (RuntimeException exception) {
                // 中文说明：监听器是观察性组件，必须将其异常吞掉并日志记录，绝不能影响注册表主流程
                LOGGER.log(System.Logger.Level.WARNING,
                        "MCP tool registry change listener failed", exception);
            }
        }
    }

    private void validateDescriptor(McpToolDescriptor descriptor) {
        if (!RECOMMENDED_NAME.matcher(descriptor.name()).matches()) {
            throw new IllegalArgumentException(
                    "MCP tool name must match [A-Za-z0-9_.-]{1,128}: " + descriptor.name());
        }
        Object inputType = descriptor.inputSchema().get("type");
        if (!"object".equals(inputType)) {
            throw new IllegalArgumentException(
                    "MCP tool inputSchema root type must be object: " + descriptor.name());
        }
    }
}
