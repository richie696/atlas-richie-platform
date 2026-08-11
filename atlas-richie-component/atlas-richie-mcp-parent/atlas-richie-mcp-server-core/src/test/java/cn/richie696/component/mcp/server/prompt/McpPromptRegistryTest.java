package cn.richie696.component.mcp.server.prompt;

import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.server.McpPromptHandler;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpPromptRegistry} 的注册、列表、解析与必填参数校验语义：
 * 非法名 / 重名拒绝、列表按字典序返回、缺失必填参数抛 {@link McpProtocolException}、
 * 参数名非字符串视为非必填、null 入参视作空映射。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpPromptRegistry 测试")
class McpPromptRegistryTest {

    @Test
    @DisplayName("register 时 null registration 抛 NPE")
    void rejectsNullRegistration() {
        McpPromptRegistry registry = new McpPromptRegistry();
        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("registration");
    }

    @Test
    @DisplayName("register 拒绝非法字符名称")
    void rejectsInvalidName() {
        McpPromptRegistry registry = new McpPromptRegistry();
        McpPromptRegistration registration = new McpPromptRegistration(
                new McpPromptDescriptor("contains space", null, null, List.of()),
                (a, c) -> CompletableFuture.completedFuture(null));

        assertThatThrownBy(() -> registry.register(registration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid MCP prompt name");
    }

    @Test
    @DisplayName("register 拒绝重名注册")
    void rejectsDuplicateName() {
        McpPromptRegistry registry = new McpPromptRegistry();
        registry.register(new McpPromptRegistration(
                new McpPromptDescriptor("alpha", null, null, List.of()),
                (a, c) -> CompletableFuture.completedFuture(null)));

        assertThatThrownBy(() -> registry.register(new McpPromptRegistration(
                new McpPromptDescriptor("alpha", "dup", null, List.of()),
                (a, c) -> CompletableFuture.completedFuture(null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate MCP prompt name");
    }

    @Test
    @DisplayName("register 成功返回当前总条目数")
    void registerReturnsTotalCount() {
        McpPromptRegistry registry = new McpPromptRegistry();

        long countA = registry.register(new McpPromptRegistration(
                new McpPromptDescriptor("alpha", null, null, List.of()),
                (a, c) -> CompletableFuture.completedFuture(null)));
        long countB = registry.register(new McpPromptRegistration(
                new McpPromptDescriptor("beta", null, null, List.of()),
                (a, c) -> CompletableFuture.completedFuture(null)));

        assertThat(countA).isEqualTo(1L);
        assertThat(countB).isEqualTo(2L);
    }

    @Test
    @DisplayName("list 按名称字典序返回描述符")
    void listReturnsLexicallyOrderedDescriptors() {
        McpPromptRegistry registry = new McpPromptRegistry();
        registry.register(new McpPromptRegistration(
                new McpPromptDescriptor("zeta", null, null, List.of()),
                (a, c) -> CompletableFuture.completedFuture(null)));
        registry.register(new McpPromptRegistration(
                new McpPromptDescriptor("alpha", null, null, List.of()),
                (a, c) -> CompletableFuture.completedFuture(null)));
        registry.register(new McpPromptRegistration(
                new McpPromptDescriptor("mu", null, null, List.of()),
                (a, c) -> CompletableFuture.completedFuture(null)));

        assertThat(registry.list())
                .extracting(McpPromptDescriptor::name)
                .containsExactly("alpha", "mu", "zeta");
    }

    @Test
    @DisplayName("list 在空注册表返回空列表")
    void listEmpty() {
        assertThat(new McpPromptRegistry().list()).isEmpty();
    }

    @Test
    @DisplayName("resolve 未找到时抛 MCP_PROMPT_NOT_FOUND")
    void resolveMissingFails() {
        McpPromptRegistry registry = new McpPromptRegistry();

        assertThatThrownBy(() -> registry.resolve("missing", Map.of()))
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32602);
                    assertThat(exception.getMessage()).isEqualTo("Prompt not found: missing");
                    assertThat(exception.data()).containsEntry("name", "missing");
                });
    }

    @Test
    @DisplayName("resolve 缺失必填参数时抛 MCP_INVALID_PARAMS")
    void resolveMissingRequiredArgumentFails() {
        McpPromptRegistry registry = new McpPromptRegistry();
        registry.register(new McpPromptRegistration(
                new McpPromptDescriptor("translate", null, null,
                        List.of(Map.of("name", "text", "required", true))),
                (a, c) -> CompletableFuture.completedFuture(null)));

        assertThatThrownBy(() -> registry.resolve("translate", Map.of()))
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32602);
                    assertThat(exception.errorCode()).isEqualTo("MCP_INVALID_PARAMS");
                    assertThat(exception.data())
                            .containsEntry("name", "translate")
                            .containsEntry("argument", "text");
                });
    }

    @Test
    @DisplayName("resolve 入参为 null 视作空映射（不抛 NPE）")
    void resolveNullArgumentsTreatedAsEmpty() {
        McpPromptRegistry registry = new McpPromptRegistry();
        registry.register(new McpPromptRegistration(
                new McpPromptDescriptor("translate", null, null, List.of()),
                (a, c) -> CompletableFuture.completedFuture(null)));

        McpPromptRegistration resolved = registry.resolve("translate", null);

        assertThat(resolved.descriptor().name()).isEqualTo("translate");
    }

    @Test
    @DisplayName("resolve 参数齐备时返回注册项")
    void resolveHappyPath() {
        McpPromptRegistry registry = new McpPromptRegistry();
        registry.register(new McpPromptRegistration(
                new McpPromptDescriptor("translate", null, null,
                        List.of(Map.of("name", "text", "required", true))),
                (a, c) -> CompletableFuture.completedFuture(null)));

        McpPromptRegistration resolved = registry.resolve("translate", Map.of("text", "hello"));

        assertThat(resolved.descriptor().name()).isEqualTo("translate");
    }

    @Test
    @DisplayName("resolve 参数 required=false 时不强制要求")
    void resolveOptionalArgumentNotEnforced() {
        McpPromptRegistry registry = new McpPromptRegistry();
        registry.register(new McpPromptRegistration(
                new McpPromptDescriptor("greet", null, null,
                        List.of(Map.of("name", "name", "required", false))),
                (a, c) -> CompletableFuture.completedFuture(null)));

        McpPromptRegistration resolved = registry.resolve("greet", Map.of());

        assertThat(resolved.descriptor().name()).isEqualTo("greet");
    }

    @Test
    @DisplayName("resolve 参数 name 非字符串时跳过该参数校验")
    void resolveArgumentWithoutStringNameSkipped() {
        McpPromptRegistry registry = new McpPromptRegistry();
        registry.register(new McpPromptRegistration(
                new McpPromptDescriptor("translate", null, null,
                        List.of(Map.of("name", 42, "required", true))),
                (a, c) -> CompletableFuture.completedFuture(null)));

        McpPromptRegistration resolved = registry.resolve("translate", Map.of());

        assertThat(resolved.descriptor().name()).isEqualTo("translate");
    }

    @Test
    @DisplayName("resolve 参数 required 字段非 true 时不强制要求")
    void resolveArgumentRequiredNonTrueNotEnforced() {
        McpPromptRegistry registry = new McpPromptRegistry();
        registry.register(new McpPromptRegistration(
                new McpPromptDescriptor("translate", null, null,
                        List.of(Map.of("name", "text", "required", "yes"))),
                (a, c) -> CompletableFuture.completedFuture(null)));

        McpPromptRegistration resolved = registry.resolve("translate", Map.of());

        assertThat(resolved.descriptor().name()).isEqualTo("translate");
    }
}