package cn.richie696.component.mcp.testkit;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.server.McpToolHandler;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.schema.McpJsonSchemaValidator;
import cn.richie696.component.mcp.schema.McpSchemaValidationResult;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * MCP 服务端 Tool 相关的测试夹具：构造 {@link McpCallContext} / {@link McpToolRegistration} 测试样例，
 * 并提供统一的 JSON Schema 断言工具。
 *
 * <p>把上下文构造、注册对象构造、schema 断言收拢到一个工具类，目的：让下游组件的测试用例不再重复
 * "new McpCallContext(...) + new McpToolRegistration(...) + 写一堆默认参数"，并避免"测试侧默认值与生产侧默认值"漂移。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpToolFixtures {
    private McpToolFixtures() {
    }

    /**
     * 构造一个无 scope 的测试调用上下文。
     *
     * @param tenantId 租户 ID（测试场景下可传任意字符串）
     * @param subject  调用主体（用户/服务标识）
     * @return 固定 requestId 与 V_2026_07_28 协议版本的 {@link McpCallContext}
     */
    public static McpCallContext callContext(String tenantId, String subject) {
        return callContext(tenantId, subject, java.util.Set.of());
    }

    /**
     * 构造一个带 scope 的测试调用上下文。
     *
     * @param tenantId 租户 ID
     * @param subject  调用主体
     * @param scopes   授权 scope 集合，{@code null} 时按空集合处理
     * @return 不可变的 {@link McpCallContext}
     */
    public static McpCallContext callContext(
            String tenantId,
            String subject,
            Collection<String> scopes) {
        return new McpCallContext(
                "test-request",
                McpProtocolVersions.V_2026_07_28,
                tenantId,
                subject,
                null,
                Map.of("scopes", new LinkedHashSet<>(scopes)),
                null,
                null);
    }

    /**
     * 构造一个测试用的 {@link McpToolRegistration}。
     *
     * <p>descriptors 的 {@code title} 与 {@code name} 相同，描述拼接为 {@code "Test fixture " + name}，
     * annotations 默认为空 Map。该工具方法强制保持 name/title 同步，避免在测试中触发"title 与 name 不一致"的
     * 业务规则校验分支，影响被测目标。</p>
     *
     * @param name         tool 名称
     * @param inputSchema  输入 JSON Schema
     * @param outputSchema 输出 JSON Schema
     * @param handler      实际执行器
     * @return 包含描述符与处理器的注册对象
     */
    public static McpToolRegistration registration(
            String name,
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema,
            McpToolHandler handler) {
        return new McpToolRegistration(new McpToolDescriptor(
                name, name, "Test fixture " + name,
                inputSchema, outputSchema, Map.of()), handler);
    }

    /**
     * 断言给定实例"应当"通过指定 JSON Schema 校验。
     *
     * <p>失败时抛 {@link AssertionError} 并附带全部违规列表，便于 JUnit 输出层直接展示根因。
     * 该方法只断言"应当通过"场景，反向断言（"应当不通过"）请直接调用 {@link McpJsonSchemaValidator#compile}。</p>
     *
     * @param validator JSON Schema 校验器
     * @param schema    待编译的 JSON Schema
     * @param instance  待校验实例
     * @throws AssertionError 当校验不通过时抛出，错误消息含全部违规
     */
    public static void assertValid(
            McpJsonSchemaValidator validator,
            Map<String, Object> schema,
            Object instance) {
        McpSchemaValidationResult result = validator.compile(schema).validate(instance);
        if (!result.isValid()) {
            throw new AssertionError("Expected valid MCP schema instance, violations="
                    + result.violations());
        }
    }
}
