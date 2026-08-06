package cn.richie696.component.vector.model;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 可序列化、可下推到向量数据库的过滤表达式。
 *
 * <p>RAG 流程中"检索条件"的统一表达。sealed 设计让穷尽 {@code switch} 可以在编译期保证新
 * 节点被全量处理；八种 permitted 子类（{@link Eq} / {@link In} / {@link ContainsAny} /
 * {@link Range} / {@link Exists} / {@link Not} / {@link And} / {@link Or}）覆盖了"等值、集合归属、
 * 集合交集、区间、字段存在、取反、组合"等典型 RAG 过滤诉求。</p>
 *
 * <p>调用关系：{@link cn.richie696.component.vector.knowledge.KnowledgeSearchRequest#additionalFilter}
 * 持有本类型实例，作为业务侧追加的过滤条件（{@code null} 时由知识库门面
 * 替换为 {@code VectorFilter.exists("tenantId")} 兜底断言）；{@link cn.richie696.component.vector.filter.VectorFilterCompiler}
 * 负责把本类型编译为目标 provider 的服务端过滤语法。所有 provider 共用同一棵表达式树，避免在调用侧写
 * 7 套字符串 DSL；代价是部分 provider 不支持 {@link ContainsAny} / {@link Range}，由编译器在该场景下
 * 抛 {@link UnsupportedOperationException}。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public sealed

interface VectorFilter permits VectorFilter.Eq, VectorFilter.In, VectorFilter.ContainsAny, VectorFilter.Range,
        VectorFilter.Exists, VectorFilter.Not, VectorFilter.And, VectorFilter.Or {

    /**
     * 等值过滤：{@code field == value}。
     *
     * @param field 字段名（必填非空）
     * @param value 比较值（必填非 {@code null}）
     * @return 等值节点
     */
    static Eq eq(String field, Object value) {
        return new Eq(requireField(field), Objects.requireNonNull(value, "value 不能为空"));
    }

    /**
     * 集合归属过滤：{@code field IN (values...)}。
     *
     * @param field  字段名（必填非空）
     * @param values 候选值集合（必填且非空）
     * @return IN 节点
     * @throws IllegalArgumentException 当 {@code values} 为空时
     */
    static In in(String field, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values 不能为空");
        }
        return new In(requireField(field), List.copyOf(values));
    }

    /**
     * 集合字段与给定值集合存在交集；用于部门、用户等 ACL 投影字段。
     *
     * <p>与 {@link #in(String, Collection)} 的区别：{@code In} 是"标量字段 ∈ 集合"，
     * {@code ContainsAny} 是"集合字段 ∩ 集合 ≠ ∅"。RAG 中典型用法：把当前用户所属部门列表
     * 与向量记录里的 {@code visibleDepartments} 字段做交集判断。</p>
     *
     * @param field  字段名（必填非空）
     * @param values 候选值集合（必填且非空）
     * @return ContainsAny 节点
     * @throws IllegalArgumentException 当 {@code values} 为空时
     */
    static ContainsAny containsAny(String field, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        return new ContainsAny(requireField(field), List.copyOf(values));
    }

    /**
     * 组合 AND：所有子过滤同时成立。
     *
     * @param filters 子过滤集合（至少一个）
     * @return AND 节点
     * @throws IllegalArgumentException 当 {@code filters} 为空时
     */
    static And and(VectorFilter... filters) {
        return new And(nonEmpty(filters));
    }

    /**
     * 组合 OR：任一子过滤成立即可。
     *
     * @param filters 子过滤集合（至少一个）
     * @return OR 节点
     * @throws IllegalArgumentException 当 {@code filters} 为空时
     */
    static Or or(VectorFilter... filters) {
        return new Or(nonEmpty(filters));
    }

    /**
     * 区间过滤：{@code greaterThanOrEqual <= field <= lessThanOrEqual}。
     *
     * <p>两个边界至少要有一个非 {@code null}；编译器在两端都给出时使用 {@code AND} 合并，
     * 单边时省略缺失端。</p>
     *
     * @param field              字段名（必填非空）
     * @param greaterThanOrEqual 下界（含），{@code null} 表示无下界
     * @param lessThanOrEqual    上界（含），{@code null} 表示无上界
     * @return Range 节点
     * @throws IllegalArgumentException 当上下界同时为 {@code null} 时
     */
    static Range range(String field, Object greaterThanOrEqual, Object lessThanOrEqual) {
        if (greaterThanOrEqual == null && lessThanOrEqual == null) {
            throw new IllegalArgumentException("range bounds must not both be null");
        }
        return new Range(requireField(field), greaterThanOrEqual, lessThanOrEqual);
    }

    /**
     * 字段存在性过滤：{@code field != null}。
     *
     * @param field 字段名（必填非空）
     * @return Exists 节点
     */
    static Exists exists(String field) {
        return new Exists(requireField(field));
    }

    /**
     * 取反节点：包住任意子过滤。
     *
     * @param filter 被取反的子过滤
     * @return Not 节点
     * @throws NullPointerException 当 {@code filter} 为 {@code null} 时
     */
    static Not not(VectorFilter filter) {
        return new Not(Objects.requireNonNull(filter, "filter must not be null"));
    }

    /**
     * 等值节点 — 表达 {@code field == value}。
     *
     * <p>最常用、几乎所有 provider 都支持的下推条件。配合 {@link #and(VectorFilter...)}
     * 可表达多字段精确匹配。</p>
     *
     * @param field 字段名（必填非空，紧凑校验在 {@link VectorFilter#eq(String, Object)} 工厂中）。
     * @param value 比较值（必填非 {@code null}）。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record Eq(String field, Object value) implements

    VectorFilter {
    }

    /**
     * 集合归属节点 — 表达 {@code field IN (values...)}。
     *
     * <p>适用于"命中多个标签之一"、"属于多个部门之一"等场景。注意 {@code In} 与
     * {@link ContainsAny} 的区别：{@code In} 用于标量字段，{@code ContainsAny} 用于集合字段。</p>
     *
     * @param field  字段名（必填非空）。
     * @param values 候选值集合（不可变快照；工厂校验非空）。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record In(String field, List<?> values) implements

    VectorFilter {
    }

    /**
     * 集合交集节点 — 表达"集合字段与给定值集合存在交集"。
     *
     * <p>用于部门、用户等 ACL 投影字段：把当前用户所属部门列表与向量记录里的
     * {@code visibleDepartments} 字段做交集判断。注意：部分 provider 不支持该语义，
     * 编译器在该场景下抛 {@link UnsupportedOperationException}。</p>
     *
     * @param field  字段名（必填非空）。
     * @param values 候选值集合（不可变快照；工厂校验非空）。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record ContainsAny(String field, List<?> values) implements

    VectorFilter {
    }

    /**
     * 区间节点 — 表达 {@code greaterThanOrEqual <= field <= lessThanOrEqual}。
     *
     * <p>两个边界至少要有一个非 {@code null}；编译器在两端都给出时使用 {@code AND} 合并，
     * 单边时省略缺失端。注意：部分 provider 不支持区间语义，编译器在该场景下抛
     * {@link UnsupportedOperationException}。</p>
     *
     * @param field              字段名（必填非空）。
     * @param greaterThanOrEqual 下界（含），{@code null} 表示无下界。
     * @param lessThanOrEqual    上界（含），{@code null} 表示无上界。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record Range(String field, Object greaterThanOrEqual, Object lessThanOrEqual) implements

    VectorFilter {
    }

    /**
     * 存在性节点 — 表达 {@code field != null}。
     *
     * <p>用于"记录是否有指定字段"判断；不关心字段值具体是什么。</p>
     *
     * @param field 字段名（必填非空）。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record Exists(String field) implements

    VectorFilter {
    }

    /**
     * 取反节点 — 包住任意子过滤。
     *
     * <p>用于表达"不属于 / 不命中 / 不存在"等取反语义。常与 {@link Eq} / {@link In} 配合做
     * 白名单排除或黑名单匹配。</p>
     *
     * @param filter 被取反的子过滤（必填非 {@code null}）。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record Not(VectorFilter filter) implements

    VectorFilter {
    }

    /**
     * AND 组合节点 — 所有子过滤同时成立。
     *
     * <p>最常用的组合节点；多个条件并列要求。深度不限制，但过深时应考虑业务侧抽象。</p>
     *
     * @param filters 子过滤集合（不可变快照；工厂校验至少一个）。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record And(List<VectorFilter> filters) implements

    VectorFilter {
    }

    /**
     * OR 组合节点 — 任一子过滤成立即可。
     *
     * <p>用于"要么命中 A 要么命中 B"的兜底查询；同样深度不限。</p>
     *
     * @param filters 子过滤集合（不可变快照；工厂校验至少一个）。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record Or(List<VectorFilter> filters) implements

    VectorFilter {
    }

    private static String requireField(String field) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field 不能为空");
        }
        return field;
    }

    private static List<VectorFilter> nonEmpty(VectorFilter[] filters) {
        if (filters == null || filters.length == 0) {
            throw new IllegalArgumentException("filters 不能为空");
        }
        return List.of(filters);
    }
}
