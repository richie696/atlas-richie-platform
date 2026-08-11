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
/**
 * 资源注册表：同时维护精确 URI 资源与 URI 模板资源，支持模板动态展开。
 *
 * <p>使用 {@link ConcurrentSkipListMap} 保证遍历顺序与 URI 字典序一致，
 * 便于客户端按确定顺序枚举资源；版本号自增用于向 MCP 客户端推送"资源集变更"通知。
 * 模板匹配阶段通过 {@link #matches(String, String)} 把 {@code {name}} 形式占位符
 * 转换为 {@code [^/]+}，单层路径段约束保证不会出现跨越斜杠的越权匹配。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpResourceRegistry {
    private final ConcurrentNavigableMap<String, McpResourceRegistration> resources =
            new ConcurrentSkipListMap<>();
    private final ConcurrentNavigableMap<String, McpResourceTemplateRegistration> templates =
            new ConcurrentSkipListMap<>();
    private final AtomicLong revision = new AtomicLong();
    private final McpResourceVisibilityPolicy visibilityPolicy;

    /**
     * 使用默认 {@link McpResourceVisibilityPolicy#ALLOW_ALL} 构造。
     */
    public McpResourceRegistry() {
        this(McpResourceVisibilityPolicy.ALLOW_ALL);
    }

    /**
     * 使用自定义可见性策略构造。
     *
     * @param visibilityPolicy 可见性策略，不能为 null
     */
    public McpResourceRegistry(McpResourceVisibilityPolicy visibilityPolicy) {
        this.visibilityPolicy = Objects.requireNonNull(visibilityPolicy, "visibilityPolicy");
    }

    /**
     * 注册一个精确 URI 资源；URI 已存在或非法时抛异常。
     *
     * @param registration 资源注册项
     * @return 操作完成后的注册表版本号
     * @throws IllegalArgumentException 当 URI 重复、为空或包含换行符时
     */
    public long register(McpResourceRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        requireUri(registration.descriptor().uri(), "resource uri");
        if (resources.putIfAbsent(registration.descriptor().uri(), registration) != null) {
            throw new IllegalArgumentException("Duplicate MCP resource URI: " + registration.descriptor().uri());
        }
        return revision.incrementAndGet();
    }

    /**
     * 注册一个 URI 模板资源；模板已存在或非法时抛异常。
     *
     * @param registration 模板注册项
     * @return 操作完成后的注册表版本号
     * @throws IllegalArgumentException 当模板重复、为空或包含换行符时
     */
    public long registerTemplate(McpResourceTemplateRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        requireUri(registration.descriptor().uriTemplate(), "resource uriTemplate");
        if (templates.putIfAbsent(registration.descriptor().uriTemplate(), registration) != null) {
            throw new IllegalArgumentException(
                    "Duplicate MCP resource template: " + registration.descriptor().uriTemplate());
        }
        return revision.incrementAndGet();
    }

    /**
     * 列出对当前调用上下文可见的精确 URI 资源描述符。
     *
     * @param context 当前调用上下文
     * @return 不可变资源描述符列表
     */
    public List<McpResourceDescriptor> list(McpCallContext context) {
        return resources.values().stream()
                .map(McpResourceRegistration::descriptor)
                .filter(descriptor -> visibilityPolicy.isVisible(descriptor, context))
                .toList();
    }

    /**
     * 列出全部 URI 模板描述符（不做可见性过滤，模板列表本身是元数据）。
     *
     * @return 不可变模板描述符列表
     */
    public List<McpResourceTemplateDescriptor> listTemplates() {
        return templates.values().stream().map(McpResourceTemplateRegistration::descriptor).toList();
    }

    /**
     * 解析指定 URI：先查精确表，未命中再尝试 URI 模板匹配。
     *
     * @param uri     资源 URI
     * @param context 当前调用上下文
     * @return 已通过可见性校验的资源注册项
     * @throws McpProtocolException 当 URI 不存在任何匹配项时
     */
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

    /**
     * 返回注册表当前版本号（自增用于变更通知）。
     *
     * @return 当前版本号
     */
    public long revision() {
        return revision.get();
    }

    private boolean matches(String template, String uri) {
        // 中文说明：把 {name} 占位符转换为 [^/]+，避免单层模板匹配到跨越斜杠的 URI 段
        // 中文说明：literal 段使用 Pattern.quote 转义，防御性处理 URI 中可能出现的 . * ? 等正则元字符
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
        // 中文说明：拒绝包含 \r / \n 的 URI，防止通过换行注入伪造协议头/日志行
        if (value == null || value.isBlank() || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException(field + " must be a non-blank URI value");
        }
    }
}
