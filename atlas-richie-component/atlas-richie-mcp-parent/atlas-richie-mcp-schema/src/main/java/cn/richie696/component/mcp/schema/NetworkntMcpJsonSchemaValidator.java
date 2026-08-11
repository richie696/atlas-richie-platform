package cn.richie696.component.mcp.schema;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于 networknt/json-schema-validator 的 {@link McpJsonSchemaValidator} 默认实现。
 *
 * <p>本类被设计为包级私有（{@code final class} 且无 {@code public} 修饰符）：外部仅通过
 * {@link McpJsonSchemaValidator} 接口或 {@link McpJsonSchemaValidators} 工厂获取实例，
 * 由此屏蔽 networknt 的具体类型（{@code SchemaRegistry}/{@code Schema} 等），符合"接口先行、依赖倒置"原则。</p>
 *
 * <p>默认启用 Draft 2020-12 元模型校验，关闭远程资源加载（防止 SSRF），并对 schema 做深度/节点数/循环引用三重防御，
 * 与 {@link McpJsonSchemaValidators#secureDefaults()} 的"安全默认"配置对齐。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
final class NetworkntMcpJsonSchemaValidator implements McpJsonSchemaValidator {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final SchemaRegistry registry;
    private final Schema metaSchema;
    private final ConcurrentMap<String, Schema> cache = new ConcurrentHashMap<>();
    private final int maximumDepth;
    private final int maximumNodes;

    /**
     * 构造 networknt 实现的 JSON Schema 校验器。
     *
     * @param maximumDepth schema 嵌套深度上限，越界时抛 {@link McpSchemaDefinitionException}
     * @param maximumNodes schema 节点总数上限，越界时抛 {@link McpSchemaDefinitionException}
     */
    NetworkntMcpJsonSchemaValidator(int maximumDepth, int maximumNodes) {
        this.maximumDepth = maximumDepth;
        this.maximumNodes = maximumNodes;
        SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                .failFast(false)
                .formatAssertionsEnabled(true)
                .typeLoose(false)
                .preloadSchema(true)
                .build();
        this.registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder
                        .schemaRegistryConfig(config)
                        .schemaLoader(loader -> loader.fetchRemoteResources(false)));
        this.metaSchema = registry.getSchema(SchemaLocation.of(
                SpecificationVersion.DRAFT_2020_12.getDialectId()));
    }

    /**
     * 将 JSON Schema 片段编译为可复用的校验闭包。
     *
     * <p>流程分四步：(1) 防御性深度/节点/循环检查；(2) 序列化为 JSON 字符串（作为缓存 key + networknt 入参）；
     * (3) 用 Draft 2020-12 元 schema 校验"schema 自身是否合法"；(4) 通过 networknt 真正编译并按 JSON 字符串缓存。</p>
     *
     * @param schema JSON Schema 片段，不可为空
     * @return 可重复调用的 {@link McpCompiledSchema}
     * @throws McpSchemaDefinitionException 当 schema 为空、超出深度/节点限制、含外部引用、含循环或元模型校验失败时抛出
     */
    @Override
    public McpCompiledSchema compile(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            throw new McpSchemaDefinitionException("JSON Schema must be a non-empty object");
        }
        inspect(schema, 0, new int[]{0}, new IdentityHashMap<>());
        String schemaJson = writeJson(schema, "schema");
        List<McpSchemaViolation> definitionErrors =
                violations(metaSchema.validate(schemaJson, InputFormat.JSON));
        if (!definitionErrors.isEmpty()) {
            throw new McpSchemaDefinitionException(
                    "Invalid JSON Schema definition",
                    definitionErrors,
                    null);
        }
        Schema compiled;
        try {
            compiled = cache.computeIfAbsent(
                    schemaJson,
                    json -> registry.getSchema(json, InputFormat.JSON));
        } catch (RuntimeException exception) {
            throw new McpSchemaDefinitionException(
                    "Unable to compile JSON Schema",
                    List.of(),
                    exception);
        }
        return instance -> {
            List<McpSchemaViolation> errors =
                    violations(compiled.validate(writeJson(instance, "instance"), InputFormat.JSON));
            return errors.isEmpty()
                    ? McpSchemaValidationResult.valid()
                    : new McpSchemaValidationResult(errors);
        };
    }

    /**
     * 递归扫描 schema 数据结构，统计深度、节点数、检测循环引用与外部引用，是安全默认的核心实现。
     *
     * @param value      当前节点（Map 或 List）
     * @param depth      当前深度
     * @param nodeCount  单元素数组（用于在递归间共享计数）
     * @param visiting   正在访问的节点集合（基于 IdentityHashMap，阻断对象图循环）
     * @throws McpSchemaDefinitionException 当深度/节点超限、存在对象图循环或非字符串 key 时抛出
     */
    private void inspect(
            Object value,
            int depth,
            int[] nodeCount,
            IdentityHashMap<Object, Boolean> visiting) {
        if (depth > maximumDepth) {
            throw new McpSchemaDefinitionException(
                    "JSON Schema exceeds maximum depth " + maximumDepth);
        }
        if (++nodeCount[0] > maximumNodes) {
            throw new McpSchemaDefinitionException(
                    "JSON Schema exceeds maximum node count " + maximumNodes);
        }
        if (!(value instanceof Map<?, ?>) && !(value instanceof List<?>)) {
            return;
        }
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new McpSchemaDefinitionException("JSON Schema object graph contains a cycle");
        }
        try {
            if (value instanceof Map<?, ?> map) {
                rejectExternalReference(map, "$ref");
                rejectExternalReference(map, "$dynamicRef");
                rejectExternalReference(map, "$recursiveRef");
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String)) {
                        throw new McpSchemaDefinitionException(
                                "JSON Schema contains a non-string object key");
                    }
                    inspect(entry.getValue(), depth + 1, nodeCount, visiting);
                }
            } else {
                for (Object entry : (List<?>) value) {
                    inspect(entry, depth + 1, nodeCount, visiting);
                }
            }
        } finally {
            visiting.remove(value);
        }
    }

    /**
     * 拒绝非 fragment 形式的外部 JSON Pointer 引用，防止恶意 schema 触达远端资源。
     *
     * @param schema   当前 Map 节点
     * @param keyword  JSON Schema 关键字名（{@code $ref} / {@code $dynamicRef} / {@code $recursiveRef}）
     * @throws McpSchemaDefinitionException 当引用不是以 {@code #} 开头时抛出
     */
    private void rejectExternalReference(Map<?, ?> schema, String keyword) {
        Object reference = schema.get(keyword);
        if (reference instanceof String ref && !ref.startsWith("#")) {
            throw new McpSchemaDefinitionException(
                    "External " + keyword + " is disabled by default: " + ref);
        }
    }

    /**
     * 将任意对象序列化为 JSON 字符串，用于缓存 key 与 networknt 入参。
     *
     * @param value 待序列化对象
     * @param kind  上下文标识（{@code "schema"} 或 {@code "instance"}），用于错误信息
     * @return 序列化结果
     * @throws IllegalArgumentException 当 Jackson 序列化失败时抛出
     */
    private String writeJson(Object value, String kind) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Unable to encode JSON " + kind, exception);
        }
    }

    /**
     * 将 networknt 的 {@link Error} 列表转换为不可变且按 (instanceLocation, schemaLocation, keyword, message) 排序的
     * {@link McpSchemaViolation} 列表。
     *
     * <p>固定排序保证：相同 schema + 相同实例在多次校验下的违规顺序稳定，简化上层断言与日志比对。</p>
     *
     * @param errors networknt 原始错误列表
     * @return 不可变违规列表
     */
    private List<McpSchemaViolation> violations(List<Error> errors) {
        if (errors.isEmpty()) {
            return List.of();
        }
        List<McpSchemaViolation> result = new ArrayList<>(errors.size());
        for (Error error : errors) {
            result.add(new McpSchemaViolation(
                    error.getInstanceLocation().toString(),
                    error.getSchemaLocation().toString(),
                    error.getKeyword(),
                    error.getMessage()));
        }
        result.sort(Comparator
                .comparing(McpSchemaViolation::instanceLocation)
                .thenComparing(McpSchemaViolation::schemaLocation)
                .thenComparing(McpSchemaViolation::keyword)
                .thenComparing(McpSchemaViolation::message));
        return List.copyOf(result);
    }
}
