package cn.richie696.component.mcp.schema;

import java.util.Map;

/**
 * JSON Schema 校验端口，对外屏蔽具体引擎类型（networknt / everit / json-schema-validator 等）。
 *
 * <p>本接口是中台"接口先行、依赖倒置"原则的体现：上游业务只面向 {@code McpJsonSchemaValidator} 编程，
 * 由 Starter 或装配层注入具体实现，从而在升级底层库时不影响业务代码。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public interface McpJsonSchemaValidator {
    /**
     * 将 JSON Schema 片段编译为可复用的校验闭包。
     *
     * @param schema JSON Schema 片段（通常由 {@link McpTypeSchemaGenerator} 生成），不可为空或空 Map
     * @return 可对实例重复调用的编译产物
     * @throws McpSchemaDefinitionException 当 schema 非法（空、外部引用、循环、深度/节点超限）时抛出
     */
    McpCompiledSchema compile(Map<String, Object> schema);
}
