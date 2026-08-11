package cn.richie696.component.mcp.schema;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.introspect.ClassIntrospector;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于 Jackson 反射的 {@link McpTypeSchemaGenerator} 默认实现，专为 Spring 服务端装配场景设计。
 *
 * <p>本类遵循"POJO/Map/集合/基本类型"四类覆盖策略：基本类型直接映射为 JSON Schema 标量，
 * 集合/数组统一抽象为 {@code {type: "array", items: ...}}，Map 抽象为
 * {@code {type: "object", additionalProperties: ...}}，POJO 通过 Jackson 反射产出 {@code properties + required}。
 * 同时引入"访问集 (visiting) + 最大深度 (maximumDepth)"两道防护，避免双向引用、巨型树、过深泛型导致栈溢出或无限递归。</p>
 *
 * <p>实现线程安全：内部使用 {@link ConcurrentMap} 缓存 {@code JavaType → Schema}，schema 副本均做
 * {@link Collections#unmodifiableMap} 包裹，对外只暴露不可变 Map。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class JacksonMcpTypeSchemaGenerator implements McpTypeSchemaGenerator {
    private static final int DEFAULT_MAXIMUM_DEPTH = 32;

    private final ObjectMapper objectMapper;
    private final int maximumDepth;
    private final ConcurrentMap<JavaType, Map<String, Object>> cache = new ConcurrentHashMap<>();

    /**
     * 使用默认 Jackson 配置构造生成器，{@code maximumDepth=32}。
     *
     * <p>适用于 Spring Boot 装配路径下用户未自定义 ObjectMapper 的常见场景。</p>
     */
    public JacksonMcpTypeSchemaGenerator() {
        this(JsonMapper.builder().build());
    }

    /**
     * 注入自定义 Jackson 配置，深度仍取默认值。
     *
     * @param objectMapper Jackson {@link ObjectMapper}，不可为 {@code null}
     */
    public JacksonMcpTypeSchemaGenerator(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_MAXIMUM_DEPTH);
    }

    /**
     * 全量构造：自定义 Jackson + 自定义最大深度。
     *
     * @param objectMapper Jackson {@link ObjectMapper}，不可为 {@code null}
     * @param maximumDepth schema 允许的最大递归深度，必须大于 0
     * @throws IllegalArgumentException 当 {@code maximumDepth < 1}
     */
    public JacksonMcpTypeSchemaGenerator(ObjectMapper objectMapper, int maximumDepth) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        if (maximumDepth < 1) {
            throw new IllegalArgumentException("maximumDepth must be positive");
        }
        this.maximumDepth = maximumDepth;
    }

    /**
     * 生成指定 Java 类型的 JSON Schema 片段，结果会被自动缓存。
     *
     * <p>缓存键为 {@link JavaType}（Jackson 强类型抽象，含泛型信息），从而避免 {@code List<String>} 与
     * {@code List<Integer>} 在不同调用处产生不同 schema 时互相覆盖。</p>
     *
     * @param type 任意 Java/Kotlin 反射类型
     * @return 不可变 JSON Schema Map
     * @throws McpSchemaDefinitionException 当类型层级超过 {@code maximumDepth} 时抛出
     */
    @Override
    public Map<String, Object> generate(Type type) {
        JavaType javaType = objectMapper.constructType(Objects.requireNonNull(type, "type"));
        return cache.computeIfAbsent(javaType,
                ignored -> schema(javaType, 0, Collections.newSetFromMap(new IdentityHashMap<>())));
    }

    /**
     * 按类型分派到具体的 JSON Schema 片段构造逻辑，是本类的核心分派器。
     *
     * @param type     Jackson 强类型
     * @param depth    当前递归深度
     * @param visiting 正在访问的 Class 集合（基于 IdentityHashMap），用于阻断循环引用
     * @return 不可变 schema 片段
     * @throws McpSchemaDefinitionException 当 {@code depth > maximumDepth} 时抛出
     */
    private Map<String, Object> schema(JavaType type, int depth, Set<Class<?>> visiting) {
        if (depth > maximumDepth) {
            throw new McpSchemaDefinitionException(
                    "Java type schema exceeds maximum depth " + maximumDepth);
        }
        Class<?> raw = type.getRawClass();
        if (raw == Void.TYPE || raw == Void.class) return Map.of();
        // java.lang.Object / Kotlin Any means an unconstrained JSON value, not
        // necessarily a JSON object. This is especially important for
        // Map<String, Object> business responses whose values may be strings,
        // numbers, arrays or nested objects.
        if (raw == Object.class) return Map.of();
        if (raw == String.class || raw == Character.class || raw == char.class
                || raw == UUID.class) return Map.of("type", "string");
        if (raw == boolean.class || raw == Boolean.class) return Map.of("type", "boolean");
        if (raw == byte.class || raw == Byte.class || raw == short.class || raw == Short.class
                || raw == int.class || raw == Integer.class || raw == long.class
                || raw == Long.class || raw == java.math.BigInteger.class) {
            return Map.of("type", "integer");
        }
        if (Number.class.isAssignableFrom(raw) || raw == float.class || raw == double.class) {
            return Map.of("type", "number");
        }
        if (raw.isEnum()) {
            List<String> values = new ArrayList<>();
            for (Object constant : raw.getEnumConstants()) values.add(((Enum<?>) constant).name());
            return Map.of("type", "string", "enum", List.copyOf(values));
        }
        if (Temporal.class.isAssignableFrom(raw) || raw == java.util.Date.class) {
            return Map.of("type", "string", "format", temporalFormat(raw));
        }
        if (Optional.class.isAssignableFrom(raw)) {
            JavaType content = type.containedTypeCount() == 0
                    ? objectMapper.constructType(Object.class)
                    : type.containedTypeOrUnknown(0);
            return schema(content, depth + 1, visiting);
        }
        if (type.isArrayType() || type.isCollectionLikeType()
                || Collection.class.isAssignableFrom(raw)) {
            JavaType content = type.getContentType() == null
                    ? objectMapper.constructType(Object.class)
                    : type.getContentType();
            return Map.of(
                    "type", "array",
                    "items", schema(content, depth + 1, visiting));
        }
        if (type.isMapLikeType() || Map.class.isAssignableFrom(raw)) {
            JavaType content = type.getContentType() == null
                    ? objectMapper.constructType(Object.class)
                    : type.getContentType();
            return Map.of(
                    "type", "object",
                    "additionalProperties", schema(content, depth + 1, visiting));
        }
        if (!visiting.add(raw)) {
            return Map.of("type", "object");
        }
        try {
            return beanSchema(type, depth, visiting);
        } finally {
            visiting.remove(raw);
        }
    }

    /**
     * 为 POJO/Bean 类型生成含 {@code properties + required} 的对象 schema。
     *
     * <p>通过 {@code Jackson ClassIntrospector} 读取序列化视角下的属性描述、注解（{@code @JsonPropertyDescription}）与必填状态，
     * 并显式设置 {@code additionalProperties: false}——这是 MCP 协议期望"严格对象"的安全默认，
     * 避免上游业务"自由字段"被静默接受。</p>
     *
     * @param type     目标 POJO 类型
     * @param depth    当前递归深度
     * @param visiting 正在访问的 Class 集合
     * @return 不可变 POJO schema
     */
    private Map<String, Object> beanSchema(JavaType type, int depth, Set<Class<?>> visiting) {
        MapperConfig<?> config = objectMapper.serializationConfig();
        ClassIntrospector introspector = config.classIntrospectorInstance().forOperation(config);
        AnnotatedClass annotated = introspector.introspectClassAnnotations(type);
        BeanDescription description = introspector.introspectForSerialization(type, annotated);
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (BeanPropertyDefinition property : description.findProperties()) {
            if (!property.couldSerialize()) continue;
            Map<String, Object> propertySchema = new LinkedHashMap<>(
                    schema(property.getPrimaryType(), depth + 1, visiting));
            String propertyDescription = property.getMetadata().getDescription();
            if (propertyDescription != null && !propertyDescription.isBlank()) {
                propertySchema.put("description", propertyDescription);
            }
            properties.put(property.getName(), immutable(propertySchema));
            if (property.isRequired() || property.getRawPrimaryType().isPrimitive()) {
                required.add(property.getName());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        result.put("properties", immutable(properties));
        result.put("additionalProperties", false);
        if (!required.isEmpty()) result.put("required", List.copyOf(required));
        return immutable(result);
    }

    /**
     * 将 {@link java.time} / {@link java.util.Date} 类型映射为 JSON Schema 的 {@code format} 字段。
     *
     * @param raw 原始 Class
     * @return 对应的 JSON Schema format 字符串（{@code date} / {@code time} / {@code date-time}）
     */
    private String temporalFormat(Class<?> raw) {
        if (raw == java.time.LocalDate.class) return "date";
        if (raw == java.time.LocalTime.class || raw == java.time.OffsetTime.class) return "time";
        return "date-time";
    }

    /**
     * 把可变 Map 包装为不可变视图，避免外部修改破坏 schema 缓存的不变性约束。
     *
     * @param values 任意 Map
     * @return 不可变包装
     */
    private Map<String, Object> immutable(Map<String, Object> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
