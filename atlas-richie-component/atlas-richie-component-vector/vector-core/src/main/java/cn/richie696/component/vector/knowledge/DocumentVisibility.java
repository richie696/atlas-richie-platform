package cn.richie696.component.vector.knowledge;

/**
 * 写入每个向量记录的知识库文档可见性。
 *
 * <p>每条 {@code VectorRecord} 在入库时必须携带本枚举之一，作为 ACL 预过滤的核心判定字段。
 * 它与 {@link AccessScope} 的 {@code departmentIds/principalIds/tenantAdmin} 一起被
 * {@link DefaultKnowledgeBaseVectorService#search} 翻译为
 * {@code visibility OR visibility+allowedDepartmentIds OR visibility+allowedPrincipalIds}
 * 三类互斥分支：</p>
 *
 * <ul>
 *   <li>{@link #COMPANY} — 全租户可见，匹配时不附带其他约束；任何非 {@code tenantAdmin}
 *       用户只要属于同一 {@code tenantId} 即可命中</li>
 *   <li>{@link #DEPARTMENT} — 仅命中主体所属部门之一，匹配 {@code allowedDepartmentIds}
 *       上的 {@code containsAny}；空部门集合自然导致零命中</li>
 *   <li>{@link #CUSTOM} — 命中部门或主体白名单中的任一成员；与 {@link #DEPARTMENT} 共享
 *       {@code allowedDepartmentIds} 维度但额外支持 {@code allowedPrincipalIds}</li>
 *   <li>{@link #PRIVATE} — 仅命中主体白名单成员，匹配 {@code allowedPrincipalIds} 上的
 *       {@code containsAny}；用于个人草稿、私有笔记等场景</li>
 * </ul>
 *
 * <p>枚举值是封闭集合，不允许业务方新增类型；扩展能力时只能新增二级 ACL 维度并在本枚举的
 * {@code value()} 上叠加，而不是新增可见性档位。新增档位会破坏
 * {@link DefaultKnowledgeBaseVectorService#search} 中已有的决策树。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public enum DocumentVisibility {

    /**
     * 全租户可见：当前 {@code tenantId} 下任何主体都可命中。
     *
     * <p>在 {@link DefaultKnowledgeBaseVectorService} 的 ACL filter 中此分支不附带
     * {@code allowedDepartmentIds/allowedPrincipalIds} 约束，因此即使主体部门/账号集合为空
     * 也能命中。是部门、账号未配置时的"安全默认"，但因为缺少任何粒度，发布为
     * {@code COMPANY} 的文档需要审批流控制。</p>
     */
    COMPANY,

    /**
     * 部门可见：仅主体所属部门之一能命中。
     *
     * <p>需要 {@link AccessScope#departmentIds()} 非空；空部门集合对应"非管理员用户看不到任何
     * 部门级文档"，是有意的安全降级而非缺漏。</p>
     */
    DEPARTMENT,

    /**
     * 自定义可见：在部门或主体白名单中任一命中即可。
     *
     * <p>与 {@link #DEPARTMENT} 共享 {@code allowedDepartmentIds} 字段，但同时支持
     * {@code allowedPrincipalIds}。通常用于"项目组/小组圈"等小范围可见场景。</p>
     */
    CUSTOM,

    /**
     * 私有可见：仅主体白名单成员可命中。
     *
     * <p>需要 {@link AccessScope#principalIds()} 非空。用于个人草稿、暂存笔记等场景。
     * 没有"部门级别降级"路径 — 空主体集合则全隐藏。</p>
     */
    PRIVATE
}
