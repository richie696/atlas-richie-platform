package com.richie.component.search.enums;

import com.richie.component.search.model.QueryCondition;

/**
 * 查询操作符枚举
 *
 * <p>用于描述查询条件的类型，如精确匹配、区间、复合等。
 * 提供统一的查询操作符定义，支持不同搜索引擎的查询语法。
 *
 * <p>主要分类：
 * <ul>
 *   <li>比较操作符：EQ、NE、LE、GE、LT、GT、BETWEEN</li>
 *   <li>集合操作符：IN、NOT_IN</li>
 *   <li>文本操作符：LIKE、TERM、TERMS、MATCH</li>
 *   <li>复合操作符：BOOL、MUST、SHOULD、MUST_NOT、FILTER</li>
 *   <li>字段操作符：EXISTS、NOT_EXISTS</li>
 *   <li>高级操作符：NESTED、PARENT_ID、SCRIPT、SCRIPT_SCORE</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 示例：构建一个区间查询条件
 * QueryCondition cond = new QueryCondition();
 * cond.setOperator(QueryOperator.RANGE);
 * cond.setField("age");
 * cond.setValue(Map.of("from", 18, "to", 30));
 *
 * // 精确匹配
 * QueryCondition termCond = new QueryCondition();
 * termCond.setOperator(QueryOperator.TERM);
 * termCond.setField("status");
 * termCond.setValue("active");
 *
 * // 多值匹配
 * QueryCondition termsCond = new QueryCondition();
 * termsCond.setOperator(QueryOperator.TERMS);
 * termsCond.setField("category");
 * termsCond.setValue(List.of("电子产品", "服装"));
 * }</pre>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 * @see QueryCondition
 */
public enum QueryOperator {

    // ==================== 基础比较操作符 ====================

    /**
     * 等于操作符
     *
     * <p>精确匹配字段值，相当于 SQL 的 = 操作符。
     * <p>示例：status = 'active'
     */
    EQ,

    /**
     * 不等于操作符
     *
     * <p>精确不匹配字段值，相当于 SQL 的 != 操作符。
     * <p>示例：status != 'deleted'
     */
    NE,

    /**
     * 包含操作符
     *
     * <p>字段值在指定集合中，相当于 SQL 的 IN 操作符。
     * <p>示例：category IN ('电子产品', '服装')
     */
    IN,

    /**
     * 不包含操作符
     *
     * <p>字段值不在指定集合中，相当于 SQL 的 NOT IN 操作符。
     * <p>示例：category NOT IN ('已删除', '测试')
     */
    NOT_IN,

    /**
     * 小于等于操作符
     *
     * <p>字段值小于等于指定值，相当于 SQL 的 &lt;= 操作符。
     * <p>示例：age &lt;= 30
     */
    LE,

    /**
     * 大于等于操作符
     *
     * <p>字段值大于等于指定值，相当于 SQL 的 &gt;= 操作符。
     * <p>示例：price &gt;= 100
     */
    GE,

    /**
     * 小于操作符
     *
     * <p>字段值小于指定值，相当于 SQL 的 &lt; 操作符。
     * <p>示例：age &lt; 18
     */
    LT,

    /**
     * 大于操作符
     *
     * <p>字段值大于指定值，相当于 SQL 的 &gt; 操作符。
     * <p>示例：price &gt; 1000
     */
    GT,

    /**
     * 区间操作符
     *
     * <p>字段值在指定区间内，相当于 SQL 的 BETWEEN 操作符。
     * <p>示例：age BETWEEN 18 AND 30
     */
    BETWEEN,

    // ==================== 文本操作符 ====================

    /**
     * 模糊匹配操作符
     *
     * <p>字段值包含指定字符串，相当于 SQL 的 LIKE 操作符。
     * <p>示例：name LIKE '%张%'
     */
    LIKE,

    /**
     * 精确匹配操作符
     *
     * <p>Elasticsearch 的 term 查询，精确匹配字段值。
     * <p>示例：
     * <pre>{@code
     * cond.setOperator(QueryOperator.TERM);
     * }</pre>
     */
    TERM,

    /**
     * 多值匹配操作符
     *
     * <p>Elasticsearch 的 terms 查询，匹配多个值中的任意一个。
     * <p>示例：
     * <pre>{@code
     * cond.setOperator(QueryOperator.TERMS);
     * cond.setValue(List.of("A", "B"));
     * }</pre>
     */
    TERMS,

    /**
     * 分词匹配操作符
     *
     * <p>Elasticsearch 的 match 查询，支持分词和全文搜索。
     * <p>示例：
     * <pre>{@code
     * cond.setOperator(QueryOperator.MATCH);
     * cond.setValue("全文");
     * }</pre>
     */
    MATCH,

    // ==================== 字段操作符 ====================

    /**
     * 字段存在操作符
     *
     * <p>Elasticsearch 的 exists 查询，检查字段是否存在。
     * <p>示例：
     * <pre>{@code
     * cond.setOperator(QueryOperator.EXISTS);
     * cond.setField("fieldName");
     * }</pre>
     */
    EXISTS,

    /**
     * 字段不存在操作符
     *
     * <p>字段不存在或为空，相当于 SQL 的 IS NULL 操作符。
     * <p>示例：deleted_at IS NULL
     */
    NOT_EXISTS,

    // ==================== 复合查询操作符 ====================

    /**
     * 复合查询操作符
     *
     * <p>Elasticsearch 的 bool 查询，支持 must、should、must_not 组合。
     * <p>示例：
     * <pre>{@code
     * cond.setOperator(QueryOperator.BOOL);
     * cond.setConditions(List.of(...));
     * }</pre>
     */
    OR,

    /**
     * 取反操作符
     *
     * <p>对查询条件取反，相当于 NOT 操作。
     * <p>示例：
     * <pre>{@code
     * cond.setOperator(QueryOperator.NOT);
     * cond.setConditions(List.of(...));
     * }</pre>
     */
    NOT,

    // ==================== 区间查询操作符 ====================

    /**
     * 区间查询操作符
     *
     * <p>Elasticsearch 的 range 查询，支持数值和日期区间。
     * <p>示例：
     * <pre>{@code
     * cond.setOperator(QueryOperator.RANGE);
     * cond.setValue(Map.of("from", 1, "to", 10));
     * }</pre>
     */
    RANGE
}
