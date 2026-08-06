package cn.richie696.component.vector.service;

import cn.richie696.component.vector.model.VectorSearchResult;

import java.util.List;

/**
 * 可选的 named/multi-vector 检索能力。
 *
 * <p>它针对"同一记录存在多个向量列"（named vectors）的场景：业务层把多个向量的查询
 * 同时下发，provider 在多个向量空间上分别检索后融合。典型用例：
 * <ul>
 *   <li>多模态索引 — 一个文档同时有 dense（文本）和 multimodal（图像）向量列</li>
 *   <li>多模型索引 — 一个文档同时有"通用模型"和"领域模型"的向量列，用于交叉验证</li>
 *   <li>聚簇索引 — 一个文档同时有"全局向量"和"聚簇向量"，用于粗排 + 精排的两阶段</li>
 * </ul>
 *
 * <p>当前 provider 中只有 Qdrant 原生支持 named vectors；其他 provider 通过
 * {@code AbstractVectorService.searchByMultiVector} 默认抛
 * {@link UnsupportedOperationException}。本接口不提供向量"权重"或"命名"等参数 —
 * provider 把 {@code vectors} 视为有序列表，与索引中 named vectors 一一对应，
 * 融合算法由 provider 决定。</p>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由业务层（多模态检索、聚簇精排）通过 {@code instanceof} 调用</li>
 *   <li>{@code AbstractVectorService.searchByMultiVector} 默认抛 UOE，避免
 *       "不支持的 provider 误以为只返回零结果"</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorMultiVectorSearchOperations {

    /**
     * 在多 named-vector 索引上以多向量联合检索。
     *
     * <p>{@code vectors} 的顺序必须与索引声明的 named vector 顺序一致；语义由
     * provider 端定义。返回的 {@link VectorSearchResult} 通常只携带主向量的 embedding
     * （其它向量被合并到 score 里）。</p>
     *
     * @param indexName 索引名称，非空
     * @param vectors   查询向量列表，顺序与 named vector 声明对应；非空，长度应等于
     *                  索引声明的向量列数
     * @param limit     返回条数上限
     * @return 多向量融合后的命中候选
     * @throws IllegalArgumentException      {@code vectors} 数量与索引 named vector 数
     *                                       不一致时
     * @throws UnsupportedOperationException provider 不支持 named vectors 时
     */
    List<VectorSearchResult> searchByMultiVector(String indexName, List<float[]> vectors, int limit);
}
