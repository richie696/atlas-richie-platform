package cn.richie696.component.mcp.server.resource;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.protocol.McpProtocolException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/** Deterministic resource registry with exact URI and URI-template entries. */
public final class McpResourceRegistry {
    private final ConcurrentNavigableMap<String, McpResourceRegistration> resources =
            new ConcurrentSkipListMap<>();
    private final ConcurrentNavigableMap<String, McpResourceTemplateRegistration> templates =
            new ConcurrentSkipListMap<>();
    private final AtomicLong revision = new AtomicLong();
    private final McpResourceVisibilityPolicy visibilityPolicy;

    public McpResourceRegistry() {
        this(McpResourceVisibilityPolicy.ALLOW_ALL);
    }

    public McpResourceRegistry(McpResourceVisibilityPolicy visibilityPolicy) {
        this.visibilityPolicy = Objects.requireNonNull(visibilityPolicy, "visibilityPolicy");
    }

    public long register(McpResourceRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        requireUri(registration.descriptor().uri(), "resource uri");
        if (resources.putIfAbsent(registration.descriptor().uri(), registration) != null) {
            throw new IllegalArgumentException("Duplicate MCP resource URI: " + registration.descriptor().uri());
        }
        return revision.incrementAndGet();
    }

    public long registerTemplate(McpResourceTemplateRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        requireUri(registration.descriptor().uriTemplate(), "resource uriTemplate");
        if (templates.putIfAbsent(registration.descriptor().uriTemplate(), registration) != null) {
            throw new IllegalArgumentException(
                    "Duplicate MCP resource template: " + registration.descriptor().uriTemplate());
        }
        return revision.incrementAndGet();
    }

    public List<McpResourceDescriptor> list(McpCallContext context) {
        return resources.values().stream()
                .map(McpResourceRegistration::descriptor)
                .filter(descriptor -> visibilityPolicy.isVisible(descriptor, context))
                .toList();
    }

    public List<McpResourceTemplateDescriptor> listTemplates() {
        return templates.values().stream().map(McpResourceTemplateRegistration::descriptor).toList();
    }

    public McpResourceRegistration resolve(String uri, McpCallContext context) {
        Objects.requireNonNull(uri, "uri");
        McpResourceRegistration exact = resources.get(uri);
        if (exact != null && visibilityPolicy.isVisible(exact.descriptor(), context)) {
            return exact;
        }
        for (McpResourceTemplateRegistration template : templates.values()) {
            if (matches(template.descriptor().uriTemplate(), uri)) {
                McpResourceDescriptor descriptor = new McpResourceDescriptor(
                        uri,
                        template.descriptor().name(),
                        template.descriptor().title(),
                        template.descriptor().description(),
                        template.descriptor().mimeType(),
                        null,
                        template.descriptor().icons(),
                        template.descriptor().annotations());
                if (visibilityPolicy.isVisible(descriptor, context)) {
                    return new McpResourceRegistration(descriptor, template.handler());
                }
            }
        }
        throw new McpProtocolException(
                "MCP_RESOURCE_NOT_FOUND", -32602, "Resource not found: " + uri, Map.of("uri", uri));
    }

    public long revision() {
        return revision.get();
    }

    private boolean matches(String template, String uri) {
        StringBuilder regex = new StringBuilder("^");
        int cursor = 0;
        java.util.regex.Matcher matcher = Pattern.compile("\\{[^/{}]+}").matcher(template);
        while (matcher.find()) {
            regex.append(Pattern.quote(template.substring(cursor, matcher.start()))).append("[^/]+");
            cursor = matcher.end();
        }
        regex.append(Pattern.quote(template.substring(cursor))).append("$");
        return uri.matches(regex.toString());
    }

    private void requireUri(String value, String field) {
        if (value == null || value.isBlank() || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException(field + " must be a non-blank URI value");
        }
    }
}
