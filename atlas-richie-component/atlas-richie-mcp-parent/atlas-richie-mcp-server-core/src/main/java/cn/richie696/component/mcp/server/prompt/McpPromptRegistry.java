package cn.richie696.component.mcp.server.prompt;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.protocol.McpProtocolException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Pattern;

/** Deterministic prompt registry with required argument validation. */
public final class McpPromptRegistry {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]{1,128}");
    private final ConcurrentNavigableMap<String, McpPromptRegistration> registrations =
            new ConcurrentSkipListMap<>();

    public long register(McpPromptRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        if (!NAME.matcher(registration.descriptor().name()).matches()) {
            throw new IllegalArgumentException("Invalid MCP prompt name: " + registration.descriptor().name());
        }
        if (registrations.putIfAbsent(registration.descriptor().name(), registration) != null) {
            throw new IllegalArgumentException("Duplicate MCP prompt name: " + registration.descriptor().name());
        }
        return registrations.size();
    }

    public List<McpPromptDescriptor> list() {
        return registrations.values().stream().map(McpPromptRegistration::descriptor).toList();
    }

    public McpPromptRegistration resolve(String name, Map<String, Object> arguments) {
        McpPromptRegistration registration = registrations.get(name);
        if (registration == null) {
            throw new McpProtocolException(
                    "MCP_PROMPT_NOT_FOUND", -32602, "Prompt not found: " + name, Map.of("name", name));
        }
        Map<String, Object> supplied = arguments == null ? Map.of() : arguments;
        for (Map<String, Object> argument : registration.descriptor().arguments()) {
            if (Boolean.TRUE.equals(argument.get("required"))) {
                Object argumentName = argument.get("name");
                if (argumentName instanceof String text && !supplied.containsKey(text)) {
                    throw new McpProtocolException(
                            "MCP_INVALID_PARAMS", -32602,
                            "Missing required prompt argument: " + text,
                            Map.of("name", name, "argument", text));
                }
            }
        }
        return registration;
    }
}
