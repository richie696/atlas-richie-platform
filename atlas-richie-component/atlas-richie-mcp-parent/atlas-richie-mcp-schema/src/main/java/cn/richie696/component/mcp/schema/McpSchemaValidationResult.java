package cn.richie696.component.mcp.schema;

import java.util.List;

/**
 * JSON Schema 校验结果不可变载体。
 *
 * <p>采用 {@code record} + 紧凑构造器实现"构造即不可变"：违反列表在进入时即被 {@link List#copyOf} 防御性拷贝，
 * 避免外部修改已发出的结果对象。单例 {@link #valid()} 复用空违规结果，减少无违规场景下的对象分配。</p>
 *
 * @param violations 违规列表（构造时已规范化为不可变）
 * @author richie696
 * @since 2026-08-11
 */
public record McpSchemaValidationResult(List<McpSchemaViolation> violations) {
    // 不可变的"合法"单例，调用方可以 == 引用比较
    private static final McpSchemaValidationResult VALID = new McpSchemaValidationResult(List.of());

    public McpSchemaValidationResult {
        violations = List.copyOf(violations);
    }

    /**
     * 返回一个共享的"合法"结果对象，避免重复创建空结果。
     *
     * @return 共享的单例，{@link #violations()} 为空列表
     */
    public static McpSchemaValidationResult valid() {
        return VALID;
    }

    /**
     * 判断本次校验是否完全通过。
     *
     * @return {@code true} 表示无任何违规
     */
    public boolean isValid() {
        return violations.isEmpty();
    }
}
