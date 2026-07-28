package cn.richie696.component.vector.knowledge;

/**
 * 面向业务知识库的安全检索门面。
 *
 * <p>它是向量中台对外暴露的"知识库场景"入口，业务层（问答、文档助手、智能体 RAG 检索）
 * 只需依赖本接口而无需关心底层 provider 的 ACL 表达差异、混合检索实现差异、多样性策略。
 * 唯一的实现是 {@link DefaultKnowledgeBaseVectorService}，但保留接口形态便于：
 * <ul>
 *   <li>在测试中注入桩实现，验证 ACL filter 是否如预期构造</li>
 *   <li>在未来引入预过滤策略可插拔（例如"按知识库路由到不同 provider"）</li>
 *   <li>与项目其它"门面接口 + 默认实现"模式保持一致</li>
 * </ul>
 *
 * <p>关键不变量：
 * <ul>
 *   <li>任何 {@link KnowledgeSearchRequest} 在进入本接口前 {@code accessScope} 必须已
 *       经 {@link AccessScope} 校验并不可变；本接口自身不再做二次校验</li>
 *   <li>返回的 {@link RetrievalResult#citations()} 已应用 ACL + 多样性约束，
 *       业务层可直接用于回答引用展示而无需再做授权判断</li>
 *   <li>失败语义：参数非法抛 {@link IllegalArgumentException}；provider 未声明
 *       {@link cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations}
 *       时请求 hybrid 模式抛 {@link UnsupportedOperationException}</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface KnowledgeBaseVectorService {

    /**
     * 在指定知识库上执行一次安全、可多样化的语义检索。
     *
     * <p>执行流水线（按序）：
     * <ol>
     *   <li>校验 {@code knowledgeBaseId} 非空，并构造
     *       {@code (tenantId AND knowledgeBaseId AND status=ACTIVE AND ...visibility tree)} 的
     *       复合 {@link cn.richie696.component.vector.model.VectorFilter}</li>
     *   <li>如有 {@link ActiveProjectionVersionResolver}，附加
     *       {@code projectionVersionId IN (active)} 过滤；空集直接短路返回</li>
     *   <li>根据 {@link KnowledgeSearchRequest#hybrid()} 选择
     *       {@link cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations#hybridSearch}
     *       或 {@link cn.richie696.component.vector.service.VectorService#searchByText}</li>
     *   <li>从候选池执行 MMR（可选）和单文档多样性截断，得到最终
     *       {@link RetrievalCitation} 列表</li>
     * </ol>
     *
     * <p>{@link RetrievalResult#diagnostics()} 携带候选数、返回数、是否混合、是否重排、
     * 耗时，调用方可据此做 UI 提示或自适应策略。</p>
     *
     * @param knowledgeBaseId 知识库 ID，非空；用于构造
     *                        {@code (knowledgeBaseId = ?) AND (tenantId = ?)} 的过滤
     * @param request         检索参数；{@code accessScope} 必须非空且 {@code query} 必须非空；
     *                        详见 {@link KnowledgeSearchRequest} 的紧凑构造器
     * @return 检索结果；候选数为 0 时返回空 {@code citations} 但仍携带 diagnostics
     * @throws IllegalArgumentException                              {@code knowledgeBaseId}
     *                                                                为空或 {@code request}
     *                                                                内部校验失败时抛出
     * @throws UnsupportedOperationException                          请求 hybrid 但当前 provider
     *                                                                未实现
     *                                                                {@link cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations}
     *                                                                时抛出
     * @throws cn.richie696.component.vector.exceptions.UnsupportedModalityException
     *                                                                当请求以图像内容检索但 provider 未配置 image 嵌入模型时抛出
     */
    RetrievalResult search(String knowledgeBaseId, KnowledgeSearchRequest request);
}
