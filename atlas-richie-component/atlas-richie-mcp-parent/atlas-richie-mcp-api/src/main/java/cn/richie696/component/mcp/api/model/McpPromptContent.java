package cn.richie696.component.mcp.api.model;

import java.util.List;
import java.util.Map;

/**
 * Prompt 渲染后的多轮消息内容。
 *
 * <p>Prompt 是 MCP 体系中"提示词模板"的概念：模板名 + 参数 → 一组多轮对话消息（system/user/assistant）
 * 组成的渲染结果。{@code McpPromptContent} 即为该渲染结果，消息列表以
 * {@code List<Map<String, Object>>} 表达——这样既保持协议无关（每条消息是键值对，
 * 与 JSON 协议层对齐），又避免提前锁定消息的具体子类型。</p>
 *
 * @param description 描述，提示该 Prompt 的用途
 * @param messages 多轮消息列表，每条为协议无关的键值对
 * @author richie696
 * @since 2026-08-11
 */
public record McpPromptContent(String description, List<Map<String, Object>> messages) {
    /**
     * 紧凑构造器：对消息列表做防御性不可变拷贝。
     *
     * @param description 描述
     * @param messages 消息列表
     */
    public McpPromptContent {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
