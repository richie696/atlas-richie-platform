package cn.richie696.component.vector.projection;


/**
 * 业务文档在一个租户、知识库范围内的稳定引用。
 *
 * <p>本 record 是 vector 投影插件寻址模型的最小三元组：在 (tenantId, knowledgeBaseId)
 * 划定的逻辑空间内，唯一定位一份业务文档。
 *
 * <p>它解决"如何让同一份文档在不同重建批次下可识别、且跨 tenant 与知识库不混淆"的问题——
 * 三元组共同构成 projection 维度的主键，源文档可以反复重建向量，但引用本身保持稳定，
 * 因此 (tenantId, knowledgeBaseId, documentRef) 成为检索时跨版本聚合与权限过滤的统一口径。
 *
 * <p>调用关系：作为入参传入 {@link VectorProjectionLifecycleService#beginRebuild}，
 * 由 lifecycle 持久化到 {@link VectorProjectionVersion#reference()} 快照；
 * 同时被插件写入每条 {@link cn.richie696.component.vector.model.VectorRecord} 的
 * metadata（{@code tenantId} / {@code knowledgeBaseId}），用于检索阶段的租户与知识库
 * 预过滤。文档级 ACL 与可见性仍由业务侧在 metadata 中补充。
 *
 * <p>关键不变量：三字段均非空、非纯空白，由 compact constructor 在构造期校验，违反时
 * 抛出 {@link IllegalArgumentException}；record 本身不可变，构造后可安全共享与跨进程
 * 传递。三元组相等即可视为同一份业务文档，与 sourceVersion / embeddingSpaceId 无关。
 *
 * @param tenantId        租户标识；非空、非纯空白；用于跨租户隔离与检索预过滤。
 * @param knowledgeBaseId 知识库标识；非空、非纯空白；用于在同一租户内细分向量空间与权限域。
 * @param documentRef     业务文档在所属知识库内的稳定引用（如业务主键或外部文档 ID）；非空、非纯空白。
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public record VectorProjectionReference(String tenantId, String knowledgeBaseId, String documentRef) {
    public VectorProjectionReference {
        tenantId = required(tenantId, "tenantId");
        knowledgeBaseId = required(knowledgeBaseId, "knowledgeBaseId");
        documentRef = required(documentRef, "documentRef");
    }

    /**
     * 校验字符串参数非空且非纯空白，作为 compact constructor 的统一前置守卫。
     *
     * @param value 待校验的字段值。
     * @param name 字段名称，用于构造异常消息以便定位出错字段。
     * @return 校验通过的原始值（允许调用方继续使用）。
     * @throws IllegalArgumentException 当 {@code value} 为 {@code null} 或
     *                                  {@link String#isBlank()} 时抛出。
     */
    private static String required (String value, String name){
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
