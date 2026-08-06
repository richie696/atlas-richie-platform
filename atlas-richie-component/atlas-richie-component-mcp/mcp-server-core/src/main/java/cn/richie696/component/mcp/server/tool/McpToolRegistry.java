package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.schema.McpCompiledSchema;
import cn.richie696.component.mcp.schema.McpJsonSchemaValidator;
import cn.richie696.component.mcp.schema.McpJsonSchemaValidators;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 线程安全、确定性排序、请求授权感知的 Tool 注册表。
 */
public final class McpToolRegistry {
    private static final Pattern RECOMMENDED_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,128}");

    private final ConcurrentNavigableMap<String, McpResolvedTool> registrations =
            new ConcurrentSkipListMap<>();
    private final AtomicLong revision = new AtomicLong();
    private final McpToolVisibilityPolicy visibilityPolicy;
    private final McpJsonSchemaValidator schemaValidator;

    public McpToolRegistry() {
        this(McpToolVisibilityPolicy.ALLOW_ALL, McpJsonSchemaValidators.secureDefaults());
    }

    public McpToolRegistry(McpToolVisibilityPolicy visibilityPolicy) {
        this(visibilityPolicy, McpJsonSchemaValidators.secureDefaults());
    }

    public McpToolRegistry(
            McpToolVisibilityPolicy visibilityPolicy,
            McpJsonSchemaValidator schemaValidator) {
        this.visibilityPolicy = Objects.requireNonNull(visibilityPolicy, "visibilityPolicy");
        this.schemaValidator = Objects.requireNonNull(schemaValidator, "schemaValidator");
    }

    public long register(McpToolRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        validateDescriptor(registration.descriptor());
        McpCompiledSchema inputSchema = schemaValidator.compile(registration.descriptor().inputSchema());
        McpCompiledSchema outputSchema = registration.descriptor().outputSchema().isEmpty()
                ? null
                : schemaValidator.compile(registration.descriptor().outputSchema());
        McpResolvedTool resolved = new McpResolvedTool(registration, inputSchema, outputSchema);
        McpResolvedTool existing =
                registrations.putIfAbsent(registration.descriptor().name(), resolved);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Duplicate MCP tool name: " + registration.descriptor().name());
        }
        return revision.incrementAndGet();
    }

    public long unregister(String name) {
        Objects.requireNonNull(name, "name");
        if (registrations.remove(name) != null) {
            return revision.incrementAndGet();
        }
        return revision.get();
    }

    public McpToolRegistrySnapshot snapshot(McpCallContext context) {
        Objects.requireNonNull(context, "context");
        var visible = registrations.values().stream()
                .map(tool -> tool.registration().descriptor())
                .filter(descriptor -> visibilityPolicy.isVisible(descriptor, context))
                .toList();
        return new McpToolRegistrySnapshot(revision.get(), visible);
    }

    public McpToolRegistration requireAuthorized(String name, McpCallContext context) {
        return resolveAuthorized(name, context).registration();
    }

    public McpResolvedTool resolveAuthorized(String name, McpCallContext context) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(context, "context");
        McpResolvedTool resolved = registrations.get(name);
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

    public long revision() {
        return revision.get();
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
