package cn.richie696.component.vector.projection;

/**
 * 一次文档向量化重建的不可变规格。
 *
 * <p>本 record 描述"本次重建要把哪份源版本写到哪个索引空间、用哪个向量模型空间"，是
 * projection version 的不可变标识三元素。
 *
 * <p>它解决"如何判断两次重建是否落到同一可比向量空间、因而可以走版本切换路径而非全量
 * 重写"的问题——只有三元素完全相同的重建才视为版本兼容，可直接复用既有 manifest 与
 * vectorId；任一元素改变（如切换 embedding 模型、调整索引名）即视为跨空间，必须以新
 * projection version 重新写入并清理旧版本。
 *
 * <p>调用关系：作为入参传入 {@link VectorProjectionLifecycleService#beginRebuild}，
 * 由 lifecycle 持久化到 {@link VectorProjectionVersion#specification()} 快照；
 * 同时由 {@link VectorProjectionWriter} 在写入阶段将 {@code sourceVersion} 与
 * {@code embeddingSpaceId} 投影到每条 {@link cn.richie696.component.vector.model.VectorRecord}
 * 的 metadata，用于检索阶段的精确过滤与跨空间隔离。
 *
 * <p>关键不变量：三字段均非空、非纯空白，由 compact constructor 在构造期校验；record
 * 本身不可变，重建期间不会变更。如 {@code sourceVersion} / {@code embeddingSpaceId} /
 * {@code indexName} 任一改变，应创建新的 projection version 而非复用本 record。
 *
 * @param sourceVersion 业务源文档版本号；非空、非纯空白；用于回溯与检索过滤。
 * @param indexName 目标索引名（对应向量库的 collection / index）；非空、非纯空白。
 * @param embeddingSpaceId 嵌入模型空间标识（如模型名 + 维度指纹）；非空、非纯空白；用于跨模型空间隔离。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public record VectorProjectionSpecification(String sourceVersion, String indexName, String embeddingSpaceId) {
    public VectorProjectionSpecification {
        sourceVersion = required(sourceVersion, "sourceVersion");
        indexName = required(indexName, "indexName");
        embeddingSpaceId = required(embeddingSpaceId, "embeddingSpaceId");
    }

    /**
     * 校验字符串参数非空且非纯空白，作为 compact constructor 的统一前置守卫。
     *
     * @param value 待校验的字段值。
     * @param name 字段名称，用于构造异常消息以便定位出错字段。
     * @return 校验通过的原始值。
     * @throws IllegalArgumentException 当 {@code value} 为 {@code null} 或
     *                                  {@link String#isBlank()} 时抛出。
     */
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
