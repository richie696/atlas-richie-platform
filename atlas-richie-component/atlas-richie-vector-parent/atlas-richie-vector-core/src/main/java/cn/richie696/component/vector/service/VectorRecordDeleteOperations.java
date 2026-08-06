package cn.richie696.component.vector.service;

import java.util.Collection;
import java.util.List;

/**
 * 以向量记录主键精确删除的核心能力。
 *
 * <p>它是 {@link VectorService} 的四个必选子接口之一，定义"已知 vectorId"场景下的删除入口。
 * 按 {@code documentId} 删除、按条件删除等需要 provider 支持的能力不在本接口中，由各自
 * 独立窄接口表达。本接口聚焦于"业务层拿到了上游返回的 ID，需要删除对应记录"这一最常见路径。</p>
 *
 * <p>关键不变量：
 * <ul>
 *   <li>删除是幂等的 — 重复删除同一 ID 不抛异常（典型实现：先 exists 再 delete，
 *       或 DELETE 语义本身支持 not-found）</li>
 *   <li>{@code vectorId} 为 {@code null}/空白字符视为非法入参，应抛
 *       {@link IllegalArgumentException}</li>
 *   <li>批量接口空集合视为无操作，避免无意义的网络往返</li>
 * </ul>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由 {@link VectorService} 继承暴露给业务层</li>
 *   <li>由 {@link cn.richie696.component.vector.knowledge.DefaultKnowledgeBaseVectorService}
 *       在需要"按 ID 精确清理"时调用</li>
 *   <li>由 {@code AbstractVectorService} 提供单条 → 多条的归一化（{@code deleteById}
 *       内部委托 {@code deleteByIds(List.of(id))}）</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorRecordDeleteOperations {

    /**
     * 按单个 vectorId 删除一条向量记录。
     *
     * <p>{@code AbstractVectorService} 默认实现把单条删除归一化为"批量删除一条"，由
     * provider 决定底层是一条 DELETE 还是批量操作。</p>
     *
     * @param indexName 索引名称，非空
     * @param vectorId  待删除记录的向量主键，非空
     * @throws IllegalArgumentException {@code indexName} 或 {@code vectorId} 为空时
     */
    void deleteById(String indexName, String vectorId);

    /**
     * 按 vectorId 集合批量删除。
     *
     * <p>实现建议：provider 端可拆分为内部 batch 调用（如 Milvus 的
     * {@code delete_by_ids}）以摊薄 RTT。空集合视为无操作，直接返回。</p>
     *
     * @param indexName 索引名称，非空
     * @param vectorIds 待删除的 vectorId 集合；为空时视为无操作
     * @throws IllegalArgumentException {@code indexName} 为空时
     */
    void deleteByIds(String indexName, Collection<String> vectorIds);

}
