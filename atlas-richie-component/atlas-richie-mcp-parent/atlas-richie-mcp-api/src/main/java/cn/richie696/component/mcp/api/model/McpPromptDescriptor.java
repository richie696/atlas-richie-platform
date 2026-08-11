package cn.richie696.component.mcp.api.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Prompt 描述符。
 *
 * <p>描述一个可被渲染的 Prompt 模板：模板名、业务标题、用途描述与参数定义。
 * 业务侧通过 {@code listPrompts} 拿到该描述符后，再以 {@code arguments} 调
 * {@code getPrompt} 完成一次模板渲染。</p>
 *
 * @param name Prompt 名（必填，唯一）
 * @param title 人类可读标题
 * @param description 用途描述，提示 LLM 何时使用
 * @param arguments 参数定义列表
 * @author richie696
 * @since 2026-08-11
 */
public record McpPromptDescriptor(
        String name,
        String title,
        String description,
        List<Map<String, Object>> arguments) {

    /**
     * 紧凑构造器：必填校验 + 不可变拷贝。
     *
     * @param name 模板名（非空）
     * @param title 标题
     * @param description 描述
     * @param arguments 参数定义列表
     * @throws NullPointerException 当 {@code name} 为 {@code null} 时
     */
    public McpPromptDescriptor {
        name = Objects.requireNonNull(name, "name");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}
