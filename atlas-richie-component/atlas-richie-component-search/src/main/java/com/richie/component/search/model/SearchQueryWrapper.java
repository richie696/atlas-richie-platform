package com.richie.component.search.model;

import com.richie.component.search.enums.QueryOperator;
import com.richie.component.search.enums.QueryType;
import com.richie.component.search.util.LambdaUtils;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 搜索查询构建器
 *
 * <p>支持 Lambda 表达式，提供类型安全的查询构建。
 * 提供流式 API 接口，简化复杂查询的构建过程。
 *
 * <p>主要功能：
 * <ul>
 *   <li>类型安全的查询构建：使用 Lambda 表达式避免字段名硬编码</li>
 *   <li>流式 API：支持链式调用，提高代码可读性</li>
 *   <li>多种查询条件：支持等于、不等于、包含、范围、模糊匹配等</li>
 *   <li>排序和分页：支持多字段排序和分页查询</li>
 *   <li>聚合和高亮：支持聚合查询和高亮显示</li>
 *   <li>原生查询：支持搜索引擎原生查询语法</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * // 基础查询
 * SearchQuery&lt;User&gt; query = SearchQueryWrapper.create(User.class, "users")
 *     .eq(User::getName, "张三")
 *     .ge(User::getAge, 18)
 *     .page(0, 10)
 *     .build();
 *
 * // 复杂查询
 * SearchQuery&lt;Product&gt; query = SearchQueryWrapper.create(Product.class, "products")
 *     .eq(Product::getStatus, "active")
 *     .between(Product::getPrice, 100, 1000)
 *     .in(Product::getCategory, Arrays.asList("电子产品", "服装"))
 *     .like(Product::getName, "手机")
 *     .orderByDesc(Product::getCreateTime)
 *     .page(0, 20)
 *     .highlight(Product::getName, Product::getDescription)
 *     .build();
 *
 * // 聚合查询
 * SearchQuery&lt;Order&gt; query = SearchQueryWrapper.create(Order.class, "orders")
 *     .eq(Order::getStatus, "completed")
 *     .ge(Order::getCreateTime, "2024-01-01")
 *     .aggregations(Map.of("status_stats", Map.of("type", "terms", "field", "status")))
 *     .build();
 * </pre>
 *
 * @param <T> 实体类型，必须是 Map 或 Domain 对象
 * @author richie696
 * @version 1.0
 * @see SearchQuery
 * @see QueryCondition
 * @see LambdaUtils
 * @since 2025-08-12
 */
@Slf4j
@Accessors(chain = true)
public class SearchQueryWrapper<T> extends AbstractQueryWrapper<T, SearchQueryWrapper<T>> {


    /**
     * 查询条件列表
     */
    protected List<QueryCondition> conditions = new ArrayList<>();

    public SearchQueryWrapper(Class<T> entityClass, String indexOrCollection) {
        super(entityClass, indexOrCollection);
    }

    public static <T> SearchQueryWrapper<T> create(Class<T> entityClass, String indexOrCollection) {
        return new SearchQueryWrapper<>(entityClass, indexOrCollection);
    }

    // ==================== 基础条件查询方法组 ====================

    public SearchQueryWrapper<T> eq(CFunction<T, ?> column, Object value) {
        String fieldName = getFieldName(column);
        conditions.add(new QueryCondition(QueryOperator.EQ, fieldName, value));
        return this;
    }

    public SearchQueryWrapper<T> eq(String fieldName, Object value) {
        conditions.add(new QueryCondition(QueryOperator.EQ, fieldName, value));
        return this;
    }

    public SearchQueryWrapper<T> ne(CFunction<T, ?> column, Object value) {
        String fieldName = getFieldName(column);
        conditions.add(new QueryCondition(QueryOperator.NE, fieldName, value));
        return this;
    }

    public SearchQueryWrapper<T> in(CFunction<T, ?> column, Collection<?> values) {
        String fieldName = getFieldName(column);
        conditions.add(new QueryCondition(QueryOperator.IN, fieldName, values));
        return this;
    }

    public SearchQueryWrapper<T> notIn(CFunction<T, ?> column, Collection<?> values) {
        String fieldName = getFieldName(column);
        conditions.add(new QueryCondition(QueryOperator.NOT_IN, fieldName, values));
        return this;
    }

    public SearchQueryWrapper<T> gt(CFunction<T, ?> column, Object value) {
        String fieldName = getFieldName(column);
        conditions.add(new QueryCondition(QueryOperator.GT, fieldName, value));
        return this;
    }

    public SearchQueryWrapper<T> ge(CFunction<T, ?> column, Object value) {
        String fieldName = getFieldName(column);
        conditions.add(new QueryCondition(QueryOperator.GE, fieldName, value));
        return this;
    }

    public SearchQueryWrapper<T> lt(CFunction<T, ?> column, Object value) {
        String fieldName = getFieldName(column);
        conditions.add(new QueryCondition(QueryOperator.LT, fieldName, value));
        return this;
    }

    public SearchQueryWrapper<T> le(CFunction<T, ?> column, Object value) {
        String fieldName = getFieldName(column);
        conditions.add(new QueryCondition(QueryOperator.LE, fieldName, value));
        return this;
    }

    public SearchQueryWrapper<T> between(CFunction<T, ?> column, Object from, Object to) {
        String fieldName = getFieldName(column);
        Map<String, Object> range = new HashMap<>();
        range.put("from", from);
        range.put("to", to);
        conditions.add(new QueryCondition(QueryOperator.BETWEEN, fieldName, range));
        return this;
    }

    public SearchQueryWrapper<T> like(CFunction<T, ?> column, String value) {
        String fieldName = getFieldName(column);
        conditions.add(new QueryCondition(QueryOperator.LIKE, fieldName, value));
        return this;
    }

    public SearchQueryWrapper<T> or(CFunction<T, ?> column, String value) {
        String fieldName = getFieldName(column);
        conditions.add(new QueryCondition(QueryOperator.OR, fieldName, value));
        return this;
    }

    public SearchQueryWrapper<T> exists(CFunction<T, ?> column) {
        String fieldName = getFieldName(column);
        conditions.add(new QueryCondition(QueryOperator.EXISTS, fieldName, null));
        return this;
    }

    public SearchQueryWrapper<T> notExists(CFunction<T, ?> column) {
        String fieldName = getFieldName(column);
        conditions.add(new QueryCondition(QueryOperator.NOT_EXISTS, fieldName, null));
        return this;
    }
    // ==================== 排序和分页方法组 ====================

    public SearchQueryWrapper<T> orderByAsc(CFunction<T, ?> column) {
        String fieldName = getFieldName(column);
        sort.put(fieldName, "asc");
        return this;
    }

    public SearchQueryWrapper<T> orderByDesc(CFunction<T, ?> column) {
        String fieldName = getFieldName(column);
        sort.put(fieldName, "desc");
        return this;
    }

    // ==================== 高亮和建议方法组 ====================

    /**
     * 高亮字段设置
     */
    public SearchQueryWrapper<T> highlight(CFunction<T, ?>... columns) {
        for (CFunction<T, ?> column : columns) {
            String fieldName = getFieldName(column);
            highlightFields.add(fieldName);
        }
        return this;
    }

    /**
     * 高亮字段设置
     */
    public SearchQueryWrapper<T> highlight(String... fields) {
        highlightFields.addAll(Arrays.asList(fields));
        return this;
    }

    /**
     * 建议查询设置
     */
    public SearchQueryWrapper<T> suggest(CFunction<T, ?> column, String prefix) {
        return suggest(column, prefix, 5);
    }

    /**
     * 建议查询设置
     */
    public SearchQueryWrapper<T> suggest(String fieldName, String prefix) {
        Map<String, Object> suggestConfig = new HashMap<>();
        suggestConfig.put("field", fieldName);
        suggestConfig.put("prefix", prefix);
        suggestConfig.put("size", size);
        suggestions.put("%s_suggest".formatted(fieldName), suggestConfig);
        return this;
    }

    /**
     * 建议查询设置
     */
    public SearchQueryWrapper<T> suggest(CFunction<T, ?> column, String prefix, int size) {
        String fieldName = getFieldName(column);
        Map<String, Object> suggestConfig = new HashMap<>();
        suggestConfig.put("field", fieldName);
        suggestConfig.put("prefix", prefix);
        suggestConfig.put("size", size);
        suggestions.put("%s_suggest".formatted(fieldName), suggestConfig);
        return this;
    }

    /**
     * 建议查询设置（使用自定义建议名称）
     */
    public SearchQueryWrapper<T> suggest(String suggestName, CFunction<T, ?> column, String prefix) {
        return suggest(suggestName, column, prefix, 5);
    }

    /**
     * 建议查询设置（使用自定义建议名称）
     */
    public SearchQueryWrapper<T> suggest(String suggestName, CFunction<T, ?> column, String prefix, int size) {
        String fieldName = getFieldName(column);
        Map<String, Object> suggestConfig = new HashMap<>();
        suggestConfig.put("field", fieldName);
        suggestConfig.put("prefix", prefix);
        suggestConfig.put("size", size);
        suggestions.put(suggestName, suggestConfig);
        return this;
    }

    /**
     * 构建 SearchQuery 对象
     */
    @Override
    public SearchQuery<T> build() {
        return super.build()
                .setType(QueryType.BASIC)
                .setConditions(this.conditions);
    }

    /**
     * 添加实体对象的非空属性作为查询条件
     *
     * @param entity 实体对象
     */
    public void addEntityConditions(T entity) {
        addEntityConditionsRecursive(entity, "");
    }

    /**
     * 递归添加实体对象的非空属性作为查询条件
     *
     * @param entity     实体对象
     * @param parentPath 父级字段路径
     */
    private void addEntityConditionsRecursive(Object entity, String parentPath) {
        if (entity == null) {
            return;
        }

        try {
            Class<?> clazz = entity.getClass();

            // 跳过基本类型、包装类型、String、Date等简单类型
            if (isSimpleType(clazz)) {
                return;
            }

            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(entity);

                // 跳过 null 值
                if (value == null) {
                    continue;
                }

                // 构建完整字段路径
                String fieldPath = parentPath.isEmpty() ? field.getName() : parentPath + "." + field.getName();

                // 如果是简单类型且值有效，添加查询条件
                if (isSimpleType(value.getClass()) && isValidValue(value)) {
                    conditions.add(new QueryCondition(QueryOperator.EQ, fieldPath, value));
                }
                // 如果是集合类型，递归处理集合中的每个元素
                else if (value instanceof Collection<?> collection) {
                    if (!collection.isEmpty()) {
                        // 对于集合，可以添加 size 条件或者处理第一个元素
                        // 这里选择处理第一个元素作为示例
                        Object firstElement = collection.iterator().next();
                        if (firstElement != null && !isSimpleType(firstElement.getClass())) {
                            addEntityConditionsRecursive(firstElement, fieldPath);
                        }
                    }
                }
                // 如果是Map类型，递归处理Map的值
                else if (value instanceof Map<?, ?> map) {
                    if (!map.isEmpty()) {
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            Object mapValue = entry.getValue();
                            if (mapValue != null && !isSimpleType(mapValue.getClass())) {
                                String mapFieldPath = fieldPath + "." + entry.getKey();
                                addEntityConditionsRecursive(mapValue, mapFieldPath);
                            }
                        }
                    }
                }
                // 如果是复杂对象，递归处理
                else if (!isSimpleType(value.getClass())) {
                    addEntityConditionsRecursive(value, fieldPath);
                }
            }
        } catch (Exception e) {
            log.warn("无法从实体对象提取查询条件: {}", e.getMessage());
        }
    }

    /**
     * 判断是否为简单类型
     *
     * @param clazz 类型
     * @return 如果是简单类型返回 true，否则返回 false
     */
    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz == String.class ||
                clazz == Boolean.class ||
                clazz == Character.class ||
                clazz == Byte.class ||
                clazz == Short.class ||
                clazz == Integer.class ||
                clazz == Long.class ||
                clazz == Float.class ||
                clazz == Double.class ||
                clazz == java.util.Date.class ||
                clazz == java.sql.Date.class ||
                clazz == java.sql.Timestamp.class ||
                clazz == java.time.LocalDate.class ||
                clazz == java.time.LocalTime.class ||
                clazz == java.time.LocalDateTime.class ||
                clazz == java.time.ZonedDateTime.class ||
                clazz == java.time.Instant.class ||
                clazz == java.math.BigDecimal.class ||
                clazz == java.math.BigInteger.class ||
                clazz.isEnum();
    }

    /**
     * 判断值是否有效（非空且有意义）
     *
     * @param value 要检查的值
     * @return 如果值有效返回 true，否则返回 false
     */
    private boolean isValidValue(Object value) {
        return switch (value) {
            case null -> false;
            case String s -> !s.trim().isEmpty();
            case Collection<?> collection -> !collection.isEmpty();
            case Map<?, ?> map -> !map.isEmpty();
            default -> true;
        };

    }

    /**
     * 获取字段名
     */
    private String getFieldName(CFunction<T, ?> column) {
        return LambdaUtils.getFieldName(column);
    }
}
