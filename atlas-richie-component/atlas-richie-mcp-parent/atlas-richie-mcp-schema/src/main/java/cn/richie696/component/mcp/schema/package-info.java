/**
 * 包作用说明：本包提供 MCP 体系下与 JSON Schema 相关的全部核心能力，
 * 是协议层 (atlas-richie-mcp-protocol) 与业务接入层 (atlas-richie-mcp-server-core / atlas-richie-mcp-server-spring-boot-starter)
 * 之间的"类型↔协议"桥。
 *
 * <p>本包核心类与职责：</p>
 * <ul>
 *   <li>{@link cn.richie696.component.mcp.schema.McpTypeSchemaGenerator}：将 Java/Kotlin 类型转换为 JSON Schema 片段的策略接口。</li>
 *   <li>{@link cn.richie696.component.mcp.schema.JacksonMcpTypeSchemaGenerator}：基于 Jackson 反射的默认实现，覆盖 POJO/集合/Map/枚举/时间类型。</li>
 *   <li>{@link cn.richie696.component.mcp.schema.McpJsonSchemaValidator}：对外校验端口，隐藏底层 JSON Schema 引擎类型。</li>
 *   <li>{@link cn.richie696.component.mcp.schema.NetworkntMcpJsonSchemaValidator}：基于 networknt 的默认实现，支持 Draft 2020-12 元模型校验 + 深度/节点/外联限制。</li>
 *   <li>{@link cn.richie696.component.mcp.schema.McpJsonSchemaValidators}：工厂类，提供"安全默认"实例。</li>
 *   <li>{@link cn.richie696.component.mcp.schema.McpCompiledSchema}：已编译的可执行校验闭包。</li>
 *   <li>{@link cn.richie696.component.mcp.schema.McpSchemaValidationResult} / {@link cn.richie696.component.mcp.schema.McpSchemaViolation}：不可变结果模型。</li>
 *   <li>{@link cn.richie696.component.mcp.schema.McpSchemaDefinitionException}：schema 非法时的受检异常载体，携带违规列表。</li>
 * </ul>
 *
 * <p>把这些类放在同一个包的原因：它们共同完成"Java 类型 → JSON Schema → 实例校验"的单向流水线，
 * 上游模块（API 注解、server-core）只依赖本包的端口与不可变结果模型，不会泄露 networknt/Jackson 实现细节到外层。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
package cn.richie696.component.mcp.schema;
