package cn.richie696.component.mcp.api.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Protocol-neutral resource template descriptor.
 *
 * <p>描述一组参数化 Resource：使用 URI Template（如 {@code file:///{path}}）表达一类
 * 共享结构但参数不同的资源。客户端在拿到该描述符后，可以基于模板动态生成具体 URI 再
 * 调用 {@code readResource}，避免对每份 Resource 单独维护描述。</p>
 *
 * @param uriTemplate URI 模板（必填）
 * @param name 业务名（必填）
 * @param title 标题
 * @param description 用途描述
 * @param mimeType 协议 MIME 类型
 * @param icons 图标列表
 * @param annotations 扩展注解
 * @author richie696
 * @since 2026-08-11
 */
public record McpResourceTemplateDescriptor(
        String uriTemplate,
        String name,
        String title,
        String description,
        String mimeType,
        List<Map<String, Object>> icons,
        Map<String, Object> annotations) {

    /**
     * 紧凑构造器：必填校验 + 不可变拷贝。
     *
     * @param uriTemplate URI 模板
     * @param name 业务名
     * @param title 标题
     * @param description 描述
     * @param mimeType MIME 类型
     * @param icons 图标列表
     * @param annotations 扩展注解
     * @throws NullPointerException 当 {@code uriTemplate} 或 {@code name} 为 {@code null} 时
     */
    public McpResourceTemplateDescriptor {
        uriTemplate = Objects.requireNonNull(uriTemplate, "uriTemplate");
        name = Objects.requireNonNull(name, "name");
        icons = icons == null ? List.of() : List.copyOf(icons);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
}
