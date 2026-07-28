# Vector Projection DAO Plugin

`atlas-richie-component-vector-projection-dao` 将业务文档在向量库中的数据视为**可重建投影**。
它只管理投影版本、`vectorId` manifest、延迟清理与 Outbox；不管理业务文档表，不判断用户权限，
也不负责解析、切片或模型调用。

## 何时引入

适用于需要文档更新、删除、版本保留、失败重试或多 provider 精确清理的商用 RAG。简单的一次性
向量写入无需引入。

```xml
<dependency>
  <groupId>cn.richie696.component</groupId>
  <artifactId>atlas-richie-component-vector-projection-dao</artifactId>
</dependency>
```

```yaml
platform:
  component:
    vector:
      projection:
        enabled: true
        cleanup-delay: 24h
        delete-batch-size: 200
```

执行 [schema/vector-projection-schema.sql](src/main/resources/schema/vector-projection-schema.sql) 中的 DDL，
或将其转换为项目自己的 Flyway/Liquibase migration。插件不会自动建表。

## 生命周期

```text
PREPARING → WRITING → READY → ACTIVE → RETIRING → CLEANED
                    └──────────────→ FAILED
```

新版本先完整写入并记录 manifest，只有 `activate` 后才成为当前版本；原 ACTIVE 版本被标记为
`RETIRING`，到期后由清理任务按 manifest 中的 `vectorId` 批量删除。这样即使 provider 不支持
`deleteByDocumentId` 或 metadata filter，仍可可靠清理旧数据。

关系库状态和 Outbox 在同一事务中更新；向量库写入与删除是可重试的最终一致操作，并非伪造的分布式事务。

## 使用方式

```java
VectorProjectionVersion version = lifecycle.beginRebuild(
    new VectorProjectionReference("tenant-a", "hr", "document:123"),
    new VectorProjectionSpecification("42", "knowledge_chunks", "text-embedding-v3"));

writer.write(version.versionId(), vectorRecords)
    .subscribe(event -> observeBulkEvent(event));

// 仅在流完成且版本已进入 READY 后执行。
lifecycle.activate(version.versionId(), Duration.ofHours(24));

// 由 Quartz / XXL-Job / 任意任务框架定期调用；插件不偷偷启动调度线程。
cleanupService.cleanupDueProjections(20);
```

写入时插件会将 `tenantId`、`knowledgeBaseId`、`projectionId`、`projectionVersionId`、`sourceVersion` 和
`embeddingSpaceId` 投影到每条 `VectorRecord.metadata`。业务可继续补充 `visibility`、部门和用户 ACL；
权限关系的计算与授权判断仍属于业务或统一权限域。

## 必须遵守的约束

- 业务检索必须只检索已激活、且满足 tenant / ACL 预过滤条件的数据；
- 相同 `sourceVersion` 的失败重建会生成新的 projection version，便于保留失败诊断并安全重试；
- 调用方应等待 `writer.write(...)` 的 Flux 正常完成后再 `activate`；
- `VectorRecordDeleteOperations.deleteByIds` 应具备幂等删除语义；
- 不要在检索 TopK 后才根据关系库过滤权限或版本。
