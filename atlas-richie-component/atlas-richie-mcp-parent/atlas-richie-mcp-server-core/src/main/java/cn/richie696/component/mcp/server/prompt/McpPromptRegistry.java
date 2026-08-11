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
/**
 * 确定性 Prompt 注册表：维护命名 Prompt 集合并在解析时执行必填参数校验。
 *
 * <p>使用 {@link ConcurrentSkipListMap} 保证按名称字典序枚举，便于客户端
 * 按确定顺序展示 Prompt 列表；名称通过严格正则 {@link #NAME} 校验，
 * 拒绝空格、斜杠、控制字符等可能污染协议路径的字符。解析阶段会对比
 * 描述符中声明的必填参数与实际入参，避免处理器在缺参下进入歧义状态。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpPromptRegistry {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.-]{1,128}");
    private final ConcurrentNavigableMap<String, McpPromptRegistration> registrations =
            new ConcurrentSkipListMap<>();

    /**
     * 注册一个 Prompt；名称非法或重复时抛异常。
     *
     * @param registration Prompt 注册项
     * @return 注册完成后的总条目数
     * @throws IllegalArgumentException 当名称不符合命名规则或已存在同名 Prompt 时
     */
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

    /**
     * 列出全部已注册 Prompt 的描述符，按名称字典序返回。
     *
     * @return 不可变描述符列表
     */
    public List<McpPromptDescriptor> list() {
        return registrations.values().stream().map(McpPromptRegistration::descriptor).toList();
    }

    /**
     * 根据名称解析 Prompt，并校验必填参数是否齐备。
     *
     * @param name      Prompt 名称
     * @param arguments 调用方提供的参数映射，null 视作空映射
     * @return 通过校验的 Prompt 注册项
     * @throws McpProtocolException 当 Prompt 不存在或缺失必填参数时
     */
    public McpPromptRegistration resolve(String name, Map<String, Object> arguments) {
        McpPromptRegistration registration = registrations.get(name);
        if (registration == null) {
            throw new McpProtocolException(
                    "MCP_PROMPT_NOT_FOUND", -32602, "Prompt not found: " + name, Map.of("name", name));
        }
        // 中文说明：null 视作空映射，避免 NPE 并对齐 "可选参数可省略" 的常规 HTTP/RPC 语义
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
