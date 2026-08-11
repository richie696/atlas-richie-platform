package cn.richie696.component.mcp.api.model;

import java.util.Map;
import java.util.List;
import java.util.Objects;

/**
 * Resource 的稳定描述符。
 *
 * <p>描述一份可读取的 Resource：URI（必填，唯一索引）、业务名、MIME 类型、大小、图标与
 * 协议无关的注解（如 audience、priority）。把"业务属性"（{@code name/title/description}）
 * 与"协议属性"（{@code uri/mimeType/size}）分列，让业务侧只关心业务属性，协议属性由
 * 适配层自动填入。</p>
 *
 * @param uri 资源 URI（必填）
 * @param name 业务名（必填）
 * @param title 人类可读标题
 * @param description 用途描述
 * @param mimeType 协议 MIME 类型
 * @param size 字节数，可为 {@code null}
 * @param icons 图标列表
 * @param annotations 协议无关的扩展注解
 * @author richie696
 * @since 2026-08-11
 */
public record McpResourceDescriptor(
        String uri,
        String name,
        String title,
        String description,
        String mimeType,
        Long size,
        List<Map<String, Object>> icons,
        Map<String, Object> annotations) {

    /**
     * 兼容旧签名：省略 {@code size}、{@code icons} 的便捷构造器。
     *
     * @param uri 资源 URI
     * @param name 业务名
     * @param title 标题
     * @param description 描述
     * @param mimeType MIME 类型
     * @param annotations 扩展注解
     */
    public McpResourceDescriptor(
            String uri,
            String name,
            String title,
            String description,
            String mimeType,
            Map<String, Object> annotations) {
        this(uri, name, title, description, mimeType, null, List.of(), annotations);
    }

    /**
     * 紧凑构造器：必填校验 + 不可变拷贝 + 大小非负校验。
     *
     * @param uri 资源 URI
     * @param name 业务名
     * @param title 标题
     * @param description 描述
     * @param mimeType MIME 类型
     * @param size 字节数
     * @param icons 图标列表
     * @param annotations 扩展注解
     * @throws NullPointerException 当 {@code uri} 或 {@code name} 为 {@code null} 时
     * @throws IllegalArgumentException 当 {@code size} 为负数时
     */
    public McpResourceDescriptor {
        uri = Objects.requireNonNull(uri, "uri");
        name = Objects.requireNonNull(name, "name");
        if (size != null && size < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        icons = icons == null ? List.of() : List.copyOf(icons);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
}
