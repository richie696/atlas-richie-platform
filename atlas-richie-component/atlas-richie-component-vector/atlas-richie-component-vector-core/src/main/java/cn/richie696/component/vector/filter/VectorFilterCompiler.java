package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;

/**
 * 将结构化过滤表达式翻译为某个 provider 的服务端过滤语法。
 *
 * <p>RAG 流程中"通用过滤树 → provider DSL"这一段的唯一扩展点。组件核心持有
 * {@code VectorFilter} 表达式树（与具体 provider 无关），每个 provider 通过实现本接口
 * 把同一棵树编译成它认识的字符串（如 Spring AI metadata DSL、Milvus 表达式、Qdrant
 * filter JSON 等），从而实现"调用侧只写一遍过滤，下游可换 provider"。</p>
 *
 * <p>调用关系：{@code AbstractVectorService} 在执行 {@code searchByText / hybridSearch} 时，
 * 取出 {@link cn.richie696.component.vector.model.SearchOptions#filter} 调
 * 用本接口的 {@link #compile(VectorFilter)}；结果作为 provider 原生 SDK 的过滤参数。
 * 已有实现见 {@link SpringAiVectorFilterCompiler}；其它 provider 在自己的模块中提供专属实现。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@FunctionalInterface
public interface VectorFilterCompiler {
    /**
     * 把结构化过滤树翻译成目标 provider 的服务端过滤语法。
     *
     * <p>实现要求：</p>
     * <ul>
 *   <li>对 {@link cn.richie696.component.vector.model.VectorFilter} sealed 接口中所有
 *       节点都做穷尽处理；新增节点后编译器应在编译期就发现问题。</li>
     *   <li>遇到 provider 不支持的节点（如部分 provider 不支持
     *       {@link cn.richie696.component.vector.model.VectorFilter.ContainsAny}）应主动
     *       抛 {@link UnsupportedOperationException}，让上层明确知道"过滤下推失败"。</li>
     *   <li>field 名应做合法性校验（避免 {@code ${...}} 等注入风险）。</li>
     *   <li>literal 值应做转义，避免单引号等破坏 DSL 解析。</li>
     * </ul>
     *
     * @param filter 通用过滤树（不会为 {@code null}）
     * @return 可直接交给 provider SDK 的过滤字符串
     */
    String compile(VectorFilter filter);
}
