package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;

import java.util.stream.Collectors;
import java.util.List;

/**
 * Spring AI metadata filter DSL 编译器；仅适用于明确声明支持该 DSL 的 provider。
 *
 * <p>把通用的 {@link VectorFilter} 树翻译成 Spring AI 元数据过滤字符串，形如
 * {@code "country == 'CN' AND status == 'active'"} 或 {@code "tenantId IN ['t1', 't2']"}。
 * 该 DSL 由 Spring AI 在
 * {@code org.springframework.ai.vectorstore.filter.Filter.Expression} 中定义，可被
 * 支持 Spring AI 的 provider（Redis、PostgreSQL/pgvector、Weaviate、Milvus 适配器
 * 等）直接消费。</p>
 *
 * <p>调用关系：被 {@code AbstractVectorService} 注入到
 * {@link cn.richie696.component.vector.model.SearchOptions#filter} 的处理路径；
 * provider 实现若使用其它 DSL（如 Milvus 表达式、Qdrant JSON），应提供自己的
 * {@link VectorFilterCompiler} 子实现，而不是复用本类。</p>
 *
 * <p><b>安全约束</b>：field 名通过 {@link #field(String)} 强校验为合法 Java 标识符，
 * 防止 {@code "${...}"} / {@code "a b"} / 包含特殊字符的字段名污染 DSL 解析；literal
 * 值会做单引号转义，规避 SQL/DSL 注入式字段值。这两点保证
 * 调用方不能借此把 RAG 业务过滤转成 provider 侧的任意表达式执行。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public final class SpringAiVectorFilterCompiler implements VectorFilterCompiler {
    /**
     * 编译入口。
     *
     * <p>穷尽匹配 {@link VectorFilter} sealed 接口的全部 8 个 permitted 子类；新增节点时
     * 编译器应同步更新，否则会在编译期报"非穷尽 switch"。</p>
     *
     * @param filter 通用过滤树
     * @return Spring AI metadata filter DSL 字符串
     */
    @Override
    public String compile(VectorFilter filter) {
        return switch (filter) {
            case VectorFilter.Eq eq -> field(eq.field()) + " == " + literal(eq.value());
            case VectorFilter.In in -> field(in.field()) + " IN " + list(in.values());
            case VectorFilter.ContainsAny containsAny ->
                    field(containsAny.field()) + " IN " + list(containsAny.values());
            case VectorFilter.Range range -> range(range);
            case VectorFilter.Exists exists -> field(exists.field()) + " != null";
            case VectorFilter.Not not -> "NOT (" + compile(not.filter()) + ")";
            case VectorFilter.And and ->
                    and.filters().stream().map(this::compile).collect(Collectors.joining(" AND ", "(", ")"));
            case VectorFilter.Or or ->
                    or.filters().stream().map(this::compile).collect(Collectors.joining(" OR ", "(", ")"));
        };
    }

    private String range(VectorFilter.Range range) {
        if (range.greaterThanOrEqual() == null) return field(range.field()) + " <= " + literal(range.lessThanOrEqual());
        if (range.lessThanOrEqual() == null) return field(range.field()) + " >= " + literal(range.greaterThanOrEqual());
        return "(" + field(range.field()) + " >= " + literal(range.greaterThanOrEqual()) + " AND "
                + field(range.field()) + " <= " + literal(range.lessThanOrEqual()) + ")";
    }

    private String list(java.util.List<?> values) {
        return values.stream().map(this::literal).collect(Collectors.joining(", ", "[", "]"));
    }

    private String literal(Object value) {
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        return "'" + String.valueOf(value).replace("'", "\\'") + "'";
    }

    private String field(String field) {
        if (!field.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("illegal filter field: " + field);
        return field;
    }
}
