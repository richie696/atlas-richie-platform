package cn.richie696.component.vector.knowledge;

import java.util.Set;

/**
 * 可选 SPI：由向量投影持久化插件提供某租户、知识库当前可见的 projection version。
 *
 * <p>它用来解决"双写期/灰度期"的数据可见性收敛问题：同一份文档可能被多次投影进不同的
 * projection version（例如 v1/v2 不同切片方案），下游只需要看当前活跃版本的结果。
 * 返回的 {@code activeVersionIds} 会被 {@link DefaultKnowledgeBaseVectorService} 附加为
 * {@code projectionVersionId IN (...)} 的过滤条件，使未启用/已下线的版本自然被排除。</p>
 *
 * <p>该 SPI 是可选的：{@link DefaultKnowledgeBaseVectorService} 通过两构造器
 * （{@link DefaultKnowledgeBaseVectorService#DefaultKnowledgeBaseVectorService(cn.richie696.component.vector.service.VectorService)}
 * 与 {@link DefaultKnowledgeBaseVectorService#DefaultKnowledgeBaseVectorService(cn.richie696.component.vector.service.VectorService, ActiveProjectionVersionResolver)}）
 * 兼容无 resolver 场景；未注入时相当于"不过滤投影版本"，行为退化为单一版本语义。</p>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由 {@link DefaultKnowledgeBaseVectorService#search} 在拼装 ACL filter 之后调用一次，
 *       拿到 active 版本集合后附加 {@code VectorFilter.in("projectionVersionId", ...)}</li>
 *   <li>由 {@link AccessScope} 的 {@code tenantId} + 业务 {@code knowledgeBaseId} 联合定位，
 *       保证多租户隔离</li>
 *   <li>实现方通常是持久化插件自身：它知道哪些 projection 已被标记为下线、哪些被灰度</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@FunctionalInterface
public interface ActiveProjectionVersionResolver {

    /**
     * 解析某租户 + 知识库组合下当前对外可见的所有 projection version ID。
     *
     * <p>返回值会被直接拼装成 {@code VectorFilter.in("projectionVersionId", ids)}，
     * 因此调用方应注意：空集合被 {@link DefaultKnowledgeBaseVectorService} 解释为
     * "无任何可用版本"并直接返回空结果，而不是绕过过滤。该语义是有意的 — 它避免了
     * "resolver 抛异常"与"resolver 返回空集合"两种失败被混为同一行为。</p>
     *
     * <p>实现约束：
     * <ul>
     *   <li>返回值应是不可变集合（建议 {@link Set#copyOf}），以避免调用方在并发请求中
     *       看到被中途修改的视图</li>
     *   <li>应允许在合理时间内完成查询（建议 &lt; 100 ms），因为它处于检索热路径</li>
     *   <li>不应抛业务异常 — 异常会导致 {@code KnowledgeBaseVectorService#search} 整链路失败；
     *       resolver 内部应当捕获并返回空集或上一次缓存值</li>
     * </ul>
     *
     * @param tenantId        调用方所属租户，已由 {@link AccessScope} 校验非空
     * @param knowledgeBaseId 知识库 ID，业务层传入；实现应避免对未知 ID 抛异常
     * @return 当前可见的 projection version ID 集合；为空表示"无可用版本"，应让调用方短路返回
     */
    Set<String> activeVersionIds(String tenantId, String knowledgeBaseId);
}
