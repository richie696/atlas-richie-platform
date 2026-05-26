package com.richie.component.search.model;

import com.richie.component.search.enums.QueryOperator;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 查询条件基类
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class QueryCondition {

    /**
     * 查询操作符
     */
    private QueryOperator operator;

    /**
     * 字段名
     */
    private String field;

    /**
     * 查询值
     */
    private Object value;

    /**
     * 构造函数
     */
    protected QueryCondition(QueryOperator operator, String field, Object value) {
        this.operator = operator;
        this.field = field;
        this.value = value;
    }
}

