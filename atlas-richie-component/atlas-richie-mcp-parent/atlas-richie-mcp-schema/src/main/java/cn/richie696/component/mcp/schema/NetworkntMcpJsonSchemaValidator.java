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
 * networknt 实现细节；第三方类型不会越过 McpJsonSchemaValidator 边界。
 */
final class NetworkntMcpJsonSchemaValidator implements McpJsonSchemaValidator {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final SchemaRegistry registry;
    private final Schema metaSchema;
    private final ConcurrentMap<String, Schema> cache = new ConcurrentHashMap<>();
    private final int maximumDepth;
    private final int maximumNodes;

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

    private void rejectExternalReference(Map<?, ?> schema, String keyword) {
        Object reference = schema.get(keyword);
        if (reference instanceof String ref && !ref.startsWith("#")) {
            throw new McpSchemaDefinitionException(
                    "External " + keyword + " is disabled by default: " + ref);
        }
    }

    private String writeJson(Object value, String kind) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Unable to encode JSON " + kind, exception);
        }
    }

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
