package cn.richie696.component.mcp.api.server;

import java.util.Map;

/**
 * 参数自动补全的请求数据。
 *
 * <p>承载 {@link McpCompletionHandler} 执行补全所需的全部输入：</p>
 * <ul>
 *   <li>{@code reference}：被补全参数所属的 Tool/Prompt 引用（协议层通用结构）；</li>
 *   <li>{@code argumentName}：被补全的参数业务名；</li>
 *   <li>{@code value}：当前已输入字符串（缺省回落为 {@code ""}）；</li>
 *   <li>{@code contextArguments}：同级其他已收集参数，用于上下文敏感的补全
 *       （例如先填了"国家"再补全"城市"）。</li>
 * </ul>
 *
 * @param reference 引用信息（被补全参数所属的 Tool 或 Prompt）
 * @param argumentName 被补全参数名（必填）
 * @param value 当前已输入字符串
 * @param contextArguments 同级其他已收集参数
 * @author richie696
 * @since 2026-08-11
 */
public record McpCompletionRequest(
        Map<String, Object> reference,
        String argumentName,
        String value,
        Map<String, String> contextArguments) {
    /**
     * 紧凑构造器：必填校验 + 不可变拷贝 + null 值回落。
     *
     * @param reference 引用信息
     * @param argumentName 被补全参数名
     * @param value 当前已输入字符串
     * @param contextArguments 同级其他参数
     * @throws IllegalArgumentException 当 {@code argumentName} 为空时
     */
    public McpCompletionRequest {
        reference = reference == null ? Map.of() : Map.copyOf(reference);
        if (argumentName == null || argumentName.isBlank()) throw new IllegalArgumentException("argumentName must not be blank");
        value = value == null ? "" : value;
        contextArguments = contextArguments == null ? Map.of() : Map.copyOf(contextArguments);
    }
}
