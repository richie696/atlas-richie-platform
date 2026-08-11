package cn.richie696.component.mcp.schema;

/**
 * {@link McpJsonSchemaValidator} 实例工厂，封装"安全默认"参数。
 *
 * <p>之所以提供工厂方法而非直接 {@code new}：在多模块工程中，调用方往往希望直接获得"开箱即用、参数已对齐" 的实现，
 * 又不想被网络层/异常防御类参数（深度/节点数）污染业务代码。工厂内部隐藏 networknt 实现类，调用方只持有接口引用。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpJsonSchemaValidators {
    private McpJsonSchemaValidators() {
    }

    /**
     * 构造一个面向生产环境"安全默认"配置的 JSON Schema 校验器。
     *
     * <p>默认参数为 {@code maximumDepth=64, maximumNodes=10_000}，对应可防御性地拒绝畸形 schema
     * （如自引用、外部 $ref）所引发的栈溢出与内存膨胀；同时启用 Draft 2020-12 元模型校验。</p>
     *
     * @return 安全的默认 JSON Schema 校验器实例
     */
    public static McpJsonSchemaValidator secureDefaults() {
        return new NetworkntMcpJsonSchemaValidator(64, 10_000);
    }
}
