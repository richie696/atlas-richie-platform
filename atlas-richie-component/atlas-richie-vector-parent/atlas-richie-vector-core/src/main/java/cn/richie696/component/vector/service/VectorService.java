/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.service;

/**
 * 向量中台的最小公共门面。
 *
 * <p>它只声明所有 provider 都应支持的检索、幂等写入、按 vectorId 删除和批量能力。
 * 精确读取、按 documentId 删除、collection/index 运维由独立的可选能力接口表达。</p>
 *
 * <p>设计动机：业务层面对 7 种向量数据库（Milvus / Qdrant / Redis / PostgreSQL /
 * MongoDB / Neo4j / Weaviate）时，期望一份"任何 provider 都能跑起来"的最小契约。
 * 集合类操作（精确读取、按 documentId 删除）、运维类操作（collection/index 生命周期、
 * 别名、备份）由独立窄接口表达 — provider 实现类按能力选择性实现，业务层通过
 * {@code instanceof} 检测后再调用，避免"某 provider 不支持则全项目不能用"。</p>
 *
 * <p>关键不变量：
 * <ul>
 *   <li>所有 {@code VectorService} 子类必须实现 {@link VectorSearchOperations}、
 *       {@link VectorRecordWriteOperations}、{@link VectorRecordDeleteOperations}、
 *       {@link VectorBulkOperations} 四个子接口</li>
 *   <li>写入按 {@code vectorId} 幂等 — 同 ID 重复写入语义为"覆盖"，非"新增"</li>
 *   <li>批量入口 {@link VectorBulkOperations#upsertAll} / {@link VectorBulkOperations#deleteAll}
 *       返回冷 {@code Flux}，订阅即启动、取消即停止；持久化由应用层负责</li>
 * </ul>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由 Spring 容器按 {@code platform.component.vector.provider} 选择唯一一个实现类激活
 *       （{@code VectorMultiProviderGuard} 拒绝多 bean 共存）</li>
 *   <li>由业务代码（智能体、文档助手、问答 RAG）通过注入的 {@code VectorService} 调用</li>
 *   <li>由 {@code cn.richie696.component.vector.knowledge.DefaultKnowledgeBaseVectorService}
 *       作为底层 provider 句柄消费</li>
 *   <li>由 {@code cn.richie696.component.vector.knowledge.DefaultKnowledgeBaseVectorService}
 *       在 ACL 预过滤后调用</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorService extends VectorSearchOperations,
        VectorRecordWriteOperations,
        VectorRecordDeleteOperations,
        VectorBulkOperations {
}
