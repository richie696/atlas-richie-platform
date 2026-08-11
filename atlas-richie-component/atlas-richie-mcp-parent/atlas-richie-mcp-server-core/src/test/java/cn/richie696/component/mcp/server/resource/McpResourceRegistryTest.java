package cn.richie696.component.mcp.server.resource;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.McpCancellationToken;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.api.server.McpResourceHandler;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpResourceRegistry} 的完整语义：
 * 默认/自定义策略构造、精确 URI 注册与拒绝（重复/空白/换行）、模板注册与拒绝、
 * 列表过滤、URI 模板匹配（占位符 + 字面段转义 + 不跨斜杠）、
 * 解析路径（精确命中、模板命中、未命中）、版本号自增。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpResourceRegistry 测试")
class McpResourceRegistryTest {

    @Test
    @DisplayName("默认构造使用 ALLOW_ALL 并允许注册精确 URI")
    void defaultConstructorRegistersExactUri() {
        McpResourceRegistry registry = new McpResourceRegistry();
        McpResourceHandler handler = (u, c) -> null;

        long revision = registry.register(new McpResourceRegistration(
                new McpResourceDescriptor("file://a.txt", "a", null, null, "text/plain", null),
                handler));

        assertThat(revision).isEqualTo(1L);
        assertThat(registry.revision()).isEqualTo(1L);
    }

    @Test
    @DisplayName("构造时 null visibilityPolicy 抛 NPE")
    void rejectsNullPolicy() {
        assertThatThrownBy(() -> new McpResourceRegistry(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("visibilityPolicy");
    }

    @Test
    @DisplayName("register 时 null registration 抛 NPE")
    void registerRejectsNull() {
        McpResourceRegistry registry = new McpResourceRegistry();
        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("registration");
    }

    @Test
    @DisplayName("register 重复 URI 抛 IllegalArgumentException")
    void rejectsDuplicateExactUri() {
        McpResourceRegistry registry = new McpResourceRegistry();
        McpResourceHandler handler = (u, c) -> null;
        registry.register(new McpResourceRegistration(
                new McpResourceDescriptor("file://a.txt", "a", null, null, "text/plain", null),
                handler));

        assertThatThrownBy(() -> registry.register(new McpResourceRegistration(
                new McpResourceDescriptor("file://a.txt", "a-dup", null, null, "text/plain", null),
                handler)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate MCP resource URI");
    }

    @Test
    @DisplayName("register 拒绝空白 / 换行 URI")
    void rejectsBlankAndNewlineUri() {
        McpResourceRegistry registry = new McpResourceRegistry();
        McpResourceHandler handler = (u, c) -> null;

        assertThatThrownBy(() -> registry.register(new McpResourceRegistration(
                new McpResourceDescriptor("", "a", null, null, "text/plain", null), handler)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-blank");
        assertThatThrownBy(() -> registry.register(new McpResourceRegistration(
                new McpResourceDescriptor("file://a\nINJECT", "a", null, null, "text/plain", null),
                handler)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-blank");
    }

    @Test
    @DisplayName("registerTemplate 时 null registration 抛 NPE")
    void registerTemplateRejectsNull() {
        McpResourceRegistry registry = new McpResourceRegistry();
        assertThatThrownBy(() -> registry.registerTemplate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("registration");
    }

    @Test
    @DisplayName("registerTemplate 成功自增版本号")
    void registerTemplateSuccessIncrementsRevision() {
        McpResourceRegistry registry = new McpResourceRegistry();
        McpResourceHandler handler = (u, c) -> null;

        long revision = registry.registerTemplate(new McpResourceTemplateRegistration(
                new McpResourceTemplateDescriptor(
                        "file:///{path}", "file", null, null, "text/plain", null, null),
                handler));

        assertThat(revision).isEqualTo(1L);
        assertThat(registry.revision()).isEqualTo(1L);
    }

    @Test
    @DisplayName("registerTemplate 重复 URI 抛 IllegalArgumentException")
    void rejectsDuplicateTemplateUri() {
        McpResourceRegistry registry = new McpResourceRegistry();
        McpResourceHandler handler = (u, c) -> null;
        registry.registerTemplate(new McpResourceTemplateRegistration(
                new McpResourceTemplateDescriptor(
                        "file:///{path}", "file", null, null, "text/plain", null, null),
                handler));

        assertThatThrownBy(() -> registry.registerTemplate(new McpResourceTemplateRegistration(
                new McpResourceTemplateDescriptor(
                        "file:///{path}", "file-dup", null, null, "text/plain", null, null),
                handler)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate MCP resource template");
    }

    @Test
    @DisplayName("registerTemplate 拒绝换行 URI")
    void registerTemplateRejectsNewlineUri() {
        McpResourceRegistry registry = new McpResourceRegistry();
        McpResourceHandler handler = (u, c) -> null;
        assertThatThrownBy(() -> registry.registerTemplate(new McpResourceTemplateRegistration(
                new McpResourceTemplateDescriptor(
                        "file:///\nINJECT", "file", null, null, "text/plain", null, null),
                handler)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-blank");
    }

    @Test
    @DisplayName("list 按 URI 字典序返回并按可见性策略过滤")
    void listFiltersByVisibilityInLexicalOrder() {
        McpResourceRegistry registry = new McpResourceRegistry(
                (descriptor, context) -> descriptor.uri().contains("public"));
        McpResourceHandler handler = (u, c) -> null;
        registry.register(new McpResourceRegistration(
                new McpResourceDescriptor("file://public/a.txt", "a", null, null, "text/plain", null),
                handler));
        registry.register(new McpResourceRegistration(
                new McpResourceDescriptor("file://private/b.txt", "b", null, null, "text/plain", null),
                handler));

        List<McpResourceDescriptor> visible = registry.list(context());

        assertThat(visible)
                .extracting(McpResourceDescriptor::uri)
                .containsExactly("file://public/a.txt");
    }

    @Test
    @DisplayName("listTemplates 按字典序返回所有模板（不做可见性过滤）")
    void listTemplatesReturnsAll() {
        McpResourceRegistry registry = new McpResourceRegistry();
        McpResourceHandler handler = (u, c) -> null;
        registry.registerTemplate(new McpResourceTemplateRegistration(
                new McpResourceTemplateDescriptor(
                        "file:///{path}", "file", null, null, "text/plain", null, null),
                handler));
        registry.registerTemplate(new McpResourceTemplateRegistration(
                new McpResourceTemplateDescriptor(
                        "db://{table}/{id}", "db", null, null, "application/json", null, null),
                handler));

        List<McpResourceTemplateDescriptor> templates = registry.listTemplates();

        assertThat(templates)
                .extracting(McpResourceTemplateDescriptor::uriTemplate)
                .containsExactly("db://{table}/{id}", "file:///{path}");
    }

    @Test
    @DisplayName("resolve 精确命中（按 URI 完全匹配）")
    void resolveExactUri() {
        McpResourceRegistry registry = new McpResourceRegistry();
        McpResourceHandler handler = (u, c) -> null;
        registry.register(new McpResourceRegistration(
                new McpResourceDescriptor("file://a.txt", "a", null, null, "text/plain", null),
                handler));

        McpResourceRegistration resolved = registry.resolve("file://a.txt", context());

        assertThat(resolved.descriptor().uri()).isEqualTo("file://a.txt");
    }

    @Test
    @DisplayName("resolve 未命中任何精确项/模板抛 McpProtocolException")
    void resolveMissingUriFails() {
        McpResourceRegistry registry = new McpResourceRegistry();

        assertThatThrownBy(() -> registry.resolve("file://missing.txt", context()))
                .isInstanceOf(McpProtocolException.class)
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32602);
                    assertThat(exception.getMessage()).contains("Resource not found");
                    assertThat(exception.data()).containsEntry("uri", "file://missing.txt");
                });
    }

    @Test
    @DisplayName("resolve 时 null uri 抛 NPE")
    void resolveRejectsNullUri() {
        McpResourceRegistry registry = new McpResourceRegistry();
        assertThatThrownBy(() -> registry.resolve(null, context()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("uri");
    }

    @Test
    @DisplayName("resolve 模板命中：占位符被替换为实际 URI 并复制模板元数据")
    void resolveTemplateMatchesPlaceholder() {
        McpResourceRegistry registry = new McpResourceRegistry();
        McpResourceHandler handler = (u, c) -> null;
        registry.registerTemplate(new McpResourceTemplateRegistration(
                new McpResourceTemplateDescriptor(
                        "file:///{path}",
                        "file",
                        "Files",
                        "file content",
                        "text/plain",
                        List.of(Map.of("url", "icon://file")),
                        Map.of("audience", "user")),
                handler));

        McpResourceRegistration resolved = registry.resolve("file:///docs.txt", context());

        assertThat(resolved.descriptor().uri()).isEqualTo("file:///docs.txt");
        assertThat(resolved.descriptor().name()).isEqualTo("file");
        assertThat(resolved.descriptor().title()).isEqualTo("Files");
        assertThat(resolved.descriptor().description()).isEqualTo("file content");
        assertThat(resolved.descriptor().mimeType()).isEqualTo("text/plain");
        assertThat(resolved.descriptor().icons()).containsExactly(Map.of("url", "icon://file"));
        assertThat(resolved.descriptor().annotations()).containsEntry("audience", "user");
        assertThat(resolved.descriptor().size()).isNull();
        assertThat(resolved.handler()).isSameAs(handler);
    }

    @Test
    @DisplayName("resolve 模板占位符不跨斜杠（{path} 仅匹配单层）")
    void resolveTemplateDoesNotCrossSlash() {
        McpResourceRegistry registry = new McpResourceRegistry();
        McpResourceHandler handler = (u, c) -> null;
        registry.registerTemplate(new McpResourceTemplateRegistration(
                new McpResourceTemplateDescriptor(
                        "file:///{path}", "file", null, null, "text/plain", null, null),
                handler));

        assertThatThrownBy(() -> registry.resolve("file:///a/b/c.txt", context()))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    @DisplayName("resolve 模板中含正则元字符的字面段被正确转义")
    void resolveTemplateEscapesRegexMeta() {
        McpResourceRegistry registry = new McpResourceRegistry();
        McpResourceHandler handler = (u, c) -> null;
        registry.registerTemplate(new McpResourceTemplateRegistration(
                new McpResourceTemplateDescriptor(
                        "api://v1.tenants/{tenantId}", "tenant", null, null, "application/json", null, null),
                handler));

        McpResourceRegistration resolved = registry.resolve("api://v1.tenants/acme", context());

        assertThat(resolved.descriptor().uri()).isEqualTo("api://v1.tenants/acme");
    }

    @Test
    @DisplayName("resolve 精确命中但不可见时跳过精确项，继续走模板匹配")
    void resolveFallsThroughToTemplateWhenExactIsInvisible() {
        McpResourceRegistry registry = new McpResourceRegistry(
                (descriptor, context) -> !"exact-blocked".equals(descriptor.name()));
        McpResourceHandler handler = (u, c) -> null;
        registry.register(new McpResourceRegistration(
                new McpResourceDescriptor("file://blocked/foo.txt", "exact-blocked",
                        null, null, "text/plain", null),
                handler));
        registry.registerTemplate(new McpResourceTemplateRegistration(
                new McpResourceTemplateDescriptor(
                        "file://{tenant}/{name}", "file-template",
                        null, null, "text/plain", null, null),
                handler));

        McpResourceRegistration resolved = registry.resolve("file://blocked/foo.txt", context());

        assertThat(resolved.descriptor().uri()).isEqualTo("file://blocked/foo.txt");
        assertThat(resolved.descriptor().name()).isEqualTo("file-template");
    }

    @Test
    @DisplayName("resolve 模板命中后由模板 visibilityPolicy 再过滤一次")
    void resolveTemplateFiltersByVisibility() {
        McpResourceRegistry registry = new McpResourceRegistry(
                (descriptor, context) -> descriptor.uri().startsWith("file://public/"));
        McpResourceHandler handler = (u, c) -> null;
        registry.registerTemplate(new McpResourceTemplateRegistration(
                new McpResourceTemplateDescriptor(
                        "file:///{path}", "file", null, null, "text/plain", null, null),
                handler));

        assertThatThrownBy(() -> registry.resolve("file://private/a.txt", context()))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    @DisplayName("多次注册 / 模板注册均按字典序存储")
    void entriesAreStoredInLexicalOrder() {
        McpResourceRegistry registry = new McpResourceRegistry();
        McpResourceHandler handler = (u, c) -> null;
        registry.register(new McpResourceRegistration(
                new McpResourceDescriptor("file://z.txt", "z", null, null, "text/plain", null),
                handler));
        registry.register(new McpResourceRegistration(
                new McpResourceDescriptor("file://a.txt", "a", null, null, "text/plain", null),
                handler));

        assertThat(registry.list(context()))
                .extracting(McpResourceDescriptor::uri)
                .containsExactly("file://a.txt", "file://z.txt");
    }

    private static McpCallContext context() {
        return new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "alice",
                null,
                Map.of(),
                McpCancellationToken.NONE,
                null);
    }
}