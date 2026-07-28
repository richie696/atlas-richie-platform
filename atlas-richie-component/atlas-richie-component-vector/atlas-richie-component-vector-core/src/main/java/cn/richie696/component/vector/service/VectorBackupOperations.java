package cn.richie696.component.vector.service;

/**
 * 可选的 provider 原生备份恢复能力。
 *
 * <p>它把 provider 自带的 snapshot/export 工具包装成统一方法；不同 provider 的实现路径
 * 差异极大：Milvus 委托给 {@code milvus-backup} 外部工具，Qdrant 走 {@code qdrant-backup}，
 * PostgreSQL 用 {@code pg_dump}。当前绝大多数 provider 通过 throwUnsupportedOps
 * 抛 {@link UnsupportedOperationException}，业务方应在使用前确认 provider 是否实际支持。</p>
 *
 * <p>{@code targetPath} / {@code sourcePath} 语义：
 * <ul>
 *   <li>对于文件型备份（{@code pg_dump}）：是本地文件系统路径</li>
 *   <li>对于对象存储型备份（{@code qdrant-backup → S3}）：是远端 URI</li>
 *   <li>对于进程内 snapshot（Milvus）：可能是 provider 内部 storage 路径</li>
 * </ul>
 * 业务方应按 provider 文档传入合法路径。
 *
 * <p>调用关系：
 * <ul>
 *   <li>由 {@code AbstractVectorService} 委托实现</li>
 *   <li>由业务层（灾备脚本、迁移任务）通过 {@code instanceof} 调用</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorBackupOperations {

    /**
     * 把指定索引备份到 {@code targetPath}。
     *
     * <p>备份粒度为整个索引，不支持单条记录。备份过程中 provider 可能仍允许读写，
     * 但快照通常对应某个时间点的一致性视图，跨 partition 的事务由 provider 决定保证力度。</p>
     *
     * @param indexName  索引名称，非空
     * @param targetPath 备份目标路径（本地路径或远端 URI，provider 决定）；非空
     * @return {@code true} = 备份成功；{@code false} = 备份失败（路径不可写、磁盘满等）
     * @throws UnsupportedOperationException provider 不支持时抛出
     * @throws IllegalArgumentException     {@code indexName} 或 {@code targetPath} 为空时
     */
    boolean backup(String indexName, String targetPath);

    /**
     * 从 {@code sourcePath} 恢复出索引 {@code indexName}。
     *
     * <p>通常要求 {@code indexName} 当前不存在；存在时由 provider 决定覆盖/拒绝/重命名。
     * 恢复会消耗较长时间，调用方应在异步任务中执行并设置超时。</p>
     *
     * @param sourcePath 备份源路径（本地路径或远端 URI）；非空
     * @param indexName  目标索引名称；非空
     * @return {@code true} = 恢复成功；{@code false} = 恢复失败
     * @throws UnsupportedOperationException provider 不支持时抛出
     * @throws IllegalArgumentException     {@code sourcePath} 或 {@code indexName} 为空时
     */
    boolean restore(String sourcePath, String indexName);
}
