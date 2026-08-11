package cn.richie696.component.mcp.schema;

/**
 * 已编译完成的 JSON Schema 校验闭包。
 *
 * <p>将"编译 schema"与"对实例做校验"两阶段拆分，使同一份 schema 可被一次性编译后多次复用，
 * 避免每次校验都重新解析 JSON Schema。该接口被设计为函数式接口，便于上游以 lambda 形式缓存并直接调用。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpCompiledSchema {
    /**
     * 对给定实例执行已编译的 JSON Schema 校验。
     *
     * @param instance 待校验的 Java 对象（通常为 Map/List/POJO，由调用方保证可被 JSON 序列化）
     * @return 校验结果，永不为 {@code null}；无违规时返回 {@link McpSchemaValidationResult#valid()}
     */
    McpSchemaValidationResult validate(Object instance);
}
