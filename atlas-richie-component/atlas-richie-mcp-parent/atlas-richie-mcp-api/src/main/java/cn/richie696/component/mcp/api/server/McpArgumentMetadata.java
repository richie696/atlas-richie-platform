package cn.richie696.component.mcp.api.server;

import java.util.List;

/**
 * Stable metadata used while binding one annotated tool argument.
 *
 * <p>该 record 是 {@link cn.richie696.component.mcp.api.annotation.McpArgument} 注解在
 * 运行期的不可变快照：业务方法被扫描时由框架生成，供 {@link McpArgumentBinder} 做出
 * 转换决策（如根据 {@code format=date-time} 决定是否走时间解析分支、根据
 * {@code sensitive} 决定是否在日志中脱敏等）。</p>
 *
 * @param name 参数业务名
 * @param description 业务描述
 * @param required 是否必填
 * @param defaultValue 默认值
 * @param format JSON Schema format
 * @param example 示例值
 * @param enumValues 候选枚举值
 * @param sensitive 是否敏感字段
 * @author richie696
 * @since 2026-08-11
 */
public record McpArgumentMetadata(
        String name,
        String description,
        boolean required,
        String defaultValue,
        String format,
        String example,
        List<String> enumValues,
        boolean sensitive) {

    /**
     * 紧凑构造器：对候选枚举值做防御性不可变拷贝。
     *
     * @param name 参数业务名
     * @param description 业务描述
     * @param required 是否必填
     * @param defaultValue 默认值
     * @param format JSON Schema format
     * @param example 示例值
     * @param enumValues 候选枚举值
     * @param sensitive 是否敏感字段
     */
    public McpArgumentMetadata {
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }
}
