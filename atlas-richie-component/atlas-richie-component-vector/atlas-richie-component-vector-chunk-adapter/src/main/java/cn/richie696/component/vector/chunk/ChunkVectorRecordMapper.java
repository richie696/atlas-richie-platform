package cn.richie696.component.vector.chunk;

import cn.richie696.component.chunking.model.Chunk;
import cn.richie696.component.vector.model.VectorRecord;

import java.util.*;

/**
 * 把 chunking 组件的 {@link Chunk} 翻译成 vector 组件的 {@link VectorRecord} 的纯函数式桥接。
 *
 * <p>它位于两个组件的接缝处：上游是 {@code atlas-richie-component-document-chunking} 产出的
 * 文本切片（{@link Chunk}），下游是 {@code atlas-richie-component-vector} 接收的向量记录
 * （{@link VectorRecord}）。本类的职责仅限"按规则把 Chunk 字段映射到 VectorRecord
 * 字段"，不调用任何向量服务、不读取 {@code embedding}、不发起 IO，也不持有可写状态。</p>
 *
 * <p>关键映射规则：
 * <ul>
 *   <li>{@code chunk.text()} → {@link VectorRecord} 的文本内容（经
 *       {@link VectorRecord#text(String, String, String)} 工厂包装为
 *       {@link cn.richie696.component.vector.model.VectorContent.TextContent}，
 *       MIME 固定为 {@code text/plain}）</li>
 *   <li>{@code chunk.ordinal()} → {@code VectorRecord.chunkNo}，同时作为业务
 *       ID 后缀</li>
 *   <li>{@code chunk.charStart} / {@code chunk.charEnd} → 写入 metadata，供检索时做
 *       原文定位引用</li>
 *   <li>{@code context.indexName()} → {@code VectorRecord.indexName}，决定入库到哪个
 *       向量集合</li>
 *   <li>{@code context.documentId() + ":" + context.version() + ":" + chunk.ordinal()}
 *       → {@code VectorRecord.id}，三段拼接保证跨 chunk 唯一且同文档同版本的多次写入
 *       可被向量库 upsert 幂等覆盖</li>
 *   <li>{@code context.version()} → {@code VectorRecord.version}，让 ACL/检索过滤可以
 *       按文档版本切活</li>
 *   <li>{@code context.namespace()} → {@code VectorRecord.namespace}，用于多租户或分组
 *       隔离</li>
 *   <li>{@code context.metadata()} → 作为基线合并入 metadata（{@code null} 视为空 Map），
 *       chunk 级的 {@code chunkOrdinal / charStart / charEnd} 会覆盖同名字段以保证
 *       切片信息优先</li>
 * </ul>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由业务侧的入库编排器（典型路径：parser → chunking → 本类 → embedding →
 *       vector 入库）在切分完成后、Embedding 之前调用</li>
 *   <li>不依赖任何 vector service 实现，因此可以脱离真实向量库做单测</li>
 *   <li>本类不构造 {@code embedding} 字段；embedding 由 AI 组件在拿到
 *       {@code VectorRecord} 后回填</li>
 * </ul>
 *
 * <p>该类是 {@code final} 的无状态工具类，线程安全，可作为 Spring 单例 bean
 * （在 {@code atlas-richie-component-vector-chunk-adapter} 模块的自动装配中已注册）。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public final class ChunkVectorRecordMapper {
    /**
     * 将单个 {@link Chunk} 与其所属文档上下文合并为一条可入库的 {@link VectorRecord}。
     *
     * <p>详细字段映射规则见 class-level Javadoc；本方法本身只做三件事：
     * <ol>
     *   <li>校验 {@code chunk} 与 {@code context} 均非空；任一为 {@code null} 立即抛
     *       {@link NullPointerException}，避免下游静默消费损坏数据</li>
     *   <li>把 chunk 级别的位置信息（{@code chunkOrdinal / charStart / charEnd}）合并入
     *       context 元数据；合并时 chunk 级字段覆盖 context 同名字段以保证切片位置是
     *       唯一真相</li>
     *   <li>按上述拼接规则生成 {@code id}，构造 {@link VectorRecord} 并填齐
 *       {@code documentId / chunkNo / version / namespace / metadata}</li>
 * </ol>
 *
 * <p>本方法不会调用任何向量服务，也不会写入 embedding；embedding 由后续 AI 组件
     * 回填。本方法可在没有真实向量库的环境下被单元测试独立调用。</p>
     *
     * @param chunk   chunking 组件产出的文本切片；必填，{@code ordinal} / {@code text} /
     *               {@code charStart} / {@code charEnd} 由 chunking 自身的紧凑构造器保证合法
     * @param context 文档级上下文载体，详见 {@link VectorRecordContext}；必填，
     *               其内部 {@code metadata} 可以为 {@code null}（视作空 Map）
     * @return 一条尚未嵌入（{@code embedding=null}）的 {@link VectorRecord}，可直接交给
     *         AI 组件做 embedding 后调用 {@code VectorService.upsert}
     * @throws NullPointerException 当 {@code chunk} 或 {@code context} 为 {@code null} 时抛出
     */
    public VectorRecord map(Chunk chunk, VectorRecordContext context) {
        Objects.requireNonNull(chunk);
        Objects.requireNonNull(context);
        Map<String, Object> meta = new HashMap<>(context.metadata() == null ? Map.of() : context.metadata());
        meta.put("chunkOrdinal", chunk.ordinal());
        meta.put("charStart", chunk.charStart());
        meta.put("charEnd", chunk.charEnd());
        return VectorRecord.text(context.indexName(), context.documentId() + ":" + context.version() + ":" + chunk.ordinal(), chunk.text())
                .setDocumentId(context.documentId())
                .setChunkNo(chunk.ordinal())
                .setVersion(context.version())
                .setNamespace(context.namespace())
                .setMetadata(meta);
    }
}
