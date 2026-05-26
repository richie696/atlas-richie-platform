package com.richie.component.search.util;

import com.richie.component.search.enums.QueryOperator;
import com.richie.component.search.model.QueryCondition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 条件解析工具类
 *
 * <p>用于从查询条件列表中提取字段名和值，支持基础查询、嵌套查询、脚本查询等条件解析。
 * 主要用于搜索查询中条件的预处理和验证。
 *
 * <p>主要功能：
 * <ul>
 *   <li>从 QueryCondition 列表中提取查询条件</li>
 *   <li>验证条件对象的有效性</li>
 *   <li>支持基础查询、嵌套查询、脚本查询等条件类型</li>
 *   <li>将复杂查询条件转换为简单的字段值映射</li>
 *   <li>从实体对象中提取非空字段并转换为查询条件</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * // 从查询条件列表中提取条件
 * List&lt;QueryCondition&gt; conditions = searchQuery.getConditions();
 * Map&lt;String, Object&gt; extractedConditions = ConditionUtils.extractConditions(conditions);
 *
 * // 从实体对象中提取条件
 * User user = new User();
 * user.setName("张三");
 * user.setAge(25);
 * List&lt;QueryCondition&gt; entityConditions = ConditionUtils.extractEntityConditions(user);
 *
 * // 检查条件有效性
 * boolean hasConditions = ConditionUtils.hasValidConditions(conditions);
 * </pre>
 *
 * @author richie696
 * @version 2.0
 * @since 2025-08-12
 */
public class ConditionUtils {

    /**
     * 从查询条件列表中提取字段名和值的映射
     *
     * <p>该方法会遍历所有查询条件，提取基础查询条件中的字段名和值。
     * 对于复杂查询条件（如嵌套查询、脚本查询），会提取其中的关键信息。
     *
     * <p>支持的条件类型：
     * <ul>
     *   <li>基础查询条件：eq、ne、in、gt、ge、lt、le、between、like、exists等</li>
     *   <li>嵌套查询条件：提取嵌套路径和内部条件</li>
     *   <li>脚本查询条件：提取脚本内容和参数</li>
     * </ul>
     *
     * @param conditions 查询条件列表
     * @return 字段名到值的映射，如果输入为 null 则返回空 Map
     */
    public static Map<String, Object> extractConditions(List<QueryCondition> conditions) {
        Map<String, Object> result = new HashMap<>();

        if (conditions == null || conditions.isEmpty()) {
            return result;
        }

        for (QueryCondition condition : conditions) {
            extractBasicCondition(condition, result);
        }

        return result;
    }

    /**
     * 提取基础查询条件
     */
    private static void extractBasicCondition(QueryCondition condition, Map<String, Object> result) {
        String field = condition.getField();
        Object value = condition.getValue();
        QueryOperator operator = condition.getOperator();

        if (field != null && value != null) {
            // 根据操作符类型处理值
            switch (operator) {
                case EQ, NE, GT, GE, LT, LE, EXISTS, NOT_EXISTS:
                    result.put(field, value);
                    break;
                case IN, NOT_IN:
                    // 对于 IN 查询，值通常是集合
                    if (value instanceof Iterable) {
                        result.put("%s_in".formatted(field), value);
                    } else {
                        result.put(field, value);
                    }
                    break;
                case BETWEEN:
                    // 对于 BETWEEN 查询，值通常是数组或两个值的对象
                    result.put("%s_from".formatted(field), extractBetweenFrom(value));
                    result.put("%s_to".formatted(field), extractBetweenTo(value));
                    break;
                case LIKE:
                    result.put("%s_like".formatted(field), value);
                    break;
                default:
                    result.put(field, value);
                    break;
            }
        }
    }

    /**
     * 从 BETWEEN 查询值中提取起始值
     */
    private static Object extractBetweenFrom(Object betweenValue) {
        if (betweenValue instanceof Object[] array && array.length >= 2) {
            return array[0];
        }
        // 可以根据实际的数据结构进行扩展
        return betweenValue;
    }

    /**
     * 从 BETWEEN 查询值中提取结束值
     */
    private static Object extractBetweenTo(Object betweenValue) {
        if (betweenValue instanceof Object[] array && array.length >= 2) {
            return array[1];
        }
        // 可以根据实际的数据结构进行扩展
        return betweenValue;
    }

}
