package cn.richie696.component.vector.service;

import cn.richie696.component.vector.model.VectorRecord;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 可选的按 vectorId 精确读取能力。
 *
 * <p>它处理"已知 vectorId，需要取回完整记录"的常见场景：知识库引用回链、cite 后端
 * 跳转、人工审计。并非所有 provider 都原生支持精确读取（Milvus 通过
 * {@code query(expr=id in [...]) }，PostgreSQL 通过主键 SELECT），因此是可选接口，
 * 业务层通过 {@code instanceof} 检测后再调用。</p>
 *
 * <p>与"按条件查询"不同：本接口只接受 {@code vectorId} 这一稳定主键，不接受
 * metadata / content 模糊查询。后者属于业务层组合的多步骤操作。</p>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由 {@code AbstractVectorService} 默认实现单条 → 多条的归一化（{@code getById}
 *       内部委托 {@code getByIds}）</li>
 *   <li>由 {@code AbstractVectorService.getByIds} 提供去重 + 不可变 Map 拷贝</li>
 *   <li>由业务层（知识库详情页、引用跳转）通过 {@code instanceof} 调用</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorRecordReadOperations {

    /**
     * 按单个 vectorId 取回完整 {@link VectorRecord}。
     *
     * <p>记录不存在时返回 {@link Optional#empty()}，而不是抛异常 — 这是有意的：
     * "未命中"是合法业务结果（如已被清理的过期记录），调用方通常会按 {@code empty}
     * 路径走降级逻辑。</p>
     *
     * @param indexName 索引名称，非空
     * @param vectorId  向量主键，非空
     * @return 命中的 {@link VectorRecord}；未命中返回 {@link Optional#empty()}
     * @throws IllegalArgumentException {@code indexName} 或 {@code vectorId} 为空时
     */
    Optional<VectorRecord> getById(String indexName, String vectorId);

    /**
     * 按 vectorId 集合批量取回，返回以 vectorId 为 key 的结果，不暗示底层存储返回顺序。
     *
     * <p>未命中的 ID 不会出现在结果 Map 中（不会映射到 {@code null}）；
     * {@code AbstractVectorService.getByIds} 进一步做 {@link Map#copyOf} 防御性拷贝
     * 与去重。空集合视为无操作。</p>
     *
     * @param indexName 索引名称，非空
     * @param vectorIds 待查询的 vectorId 集合
     * @return 以 {@code vectorId} 为 key 的不可变 Map；未命中的 ID 不出现在结果中
     * @throws IllegalArgumentException {@code indexName} 为空时
     */
    Map<String, VectorRecord> getByIds(String indexName, Collection<String> vectorIds);
}
