package cn.richie696.component.mcp.schema;

/**
 * 单条 JSON Schema 违规描述，用于在异常与校验结果中结构化呈现问题位置。
 *
 * <p>四个字段分别记录"实例侧 JSON Pointer 位置" / "schema 侧 JSON Pointer 位置" / "违规的关键字" / "可读消息"，
 * 与 JSON Schema 标准错误结构对齐，便于上层渲染为人类可读错误或上报到 APM。
 * 该 record 不再额外封装，因为字段名即语义且不可变，符合值对象定位。</p>
 *
 * @param instanceLocation 实例侧的 JSON Pointer（如 {@code /foo/0/bar}）
 * @param schemaLocation   schema 侧的 JSON Pointer（如 {@code /properties/foo/type}）
 * @param keyword          触发该违规的 JSON Schema 关键字（如 {@code required} / {@code type}）
 * @param message          人类可读的违规消息
 * @author richie696
 * @since 2026-08-11
 */
public record McpSchemaViolation(
        String instanceLocation,
        String schemaLocation,
        String keyword,
        String message) {
}
