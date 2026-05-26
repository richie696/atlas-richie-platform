# AsyncThreadStorageManager 工作原理图

## 架构图

```mermaid
flowchart TB
    %% 主服务类 - 蓝色
    Manager[AsyncThreadStorageManager<br/>异步线程池数据库复制服务]
    Manager:::serviceClass

    %% 共享缓冲区组件 - 绿色
    SharedBuffer[sharedBuffer<br/>ConcurrentLinkedQueue<br/>所有虚拟线程共享此缓冲区]
    SharedCounter[sharedCounter<br/>AtomicInteger<br/>记录缓冲区消息数量]
    FlushLock[flushLock<br/>Object<br/>保证批量刷写原子性]
    SharedBuffer:::bufferComponent
    SharedCounter:::bufferComponent
    FlushLock:::bufferComponent

    %% 执行器和配置 - 橙色
    AsyncExecutor[asyncExecutor<br/>虚拟线程池<br/>Java 21 虚拟线程<br/>轻量级 I/O 优化]
    FlushScheduler[flushScheduler<br/>定时刷新调度器<br/>单线程虚拟线程<br/>定期检查缓冲区]
    DataSource[DataSource<br/>数据源<br/>自动检测数据库类型]
    DbType[dbType<br/>数据库类型<br/>MySQL/PostgreSQL/Oracle]
    AsyncExecutor:::executorComponent
    FlushScheduler:::executorComponent
    DataSource:::executorComponent
    DbType:::executorComponent

    %% 存储层 - 紫色
    StateStorage[StateStorage<br/>Redis<br/>读取状态和历史]
    CurrentMapper[CurrentMapper<br/>数据库<br/>批量写入当前状态]
    HistoryMapper[HistoryMapper<br/>数据库<br/>批量写入历史记录]
    StateStorage:::storageComponent
    CurrentMapper:::storageComponent
    HistoryMapper:::storageComponent

    %% 连接关系
    Manager --> SharedBuffer
    Manager --> SharedCounter
    Manager --> FlushLock
    Manager --> AsyncExecutor
    Manager --> FlushScheduler
    Manager --> DataSource
    Manager --> DbType

    AsyncExecutor -->|submitSync| SharedBuffer
    AsyncExecutor -->|increment| SharedCounter
    FlushScheduler -->|定时检查| SharedBuffer
    FlushScheduler -->|触发刷写| Manager

    SharedBuffer -->|doFlushBatch| StateStorage
    SharedBuffer -->|doFlushBatch| CurrentMapper
    SharedBuffer -->|doFlushBatch| HistoryMapper

    FlushLock -->|保护| SharedBuffer
    DataSource -->|检测| DbType
    DbType -->|选择策略| CurrentMapper

    StateStorage -->|读取数据| CurrentMapper
    StateStorage -->|读取数据| HistoryMapper

    %% 样式定义
    classDef serviceClass fill:#4A90E2,stroke:#2E5C8A,stroke-width:3px,color:#fff
    classDef bufferComponent fill:#50C878,stroke:#2D7A4E,stroke-width:2px,color:#fff
    classDef executorComponent fill:#FF8C42,stroke:#CC6F35,stroke-width:2px,color:#fff
    classDef storageComponent fill:#9B59B6,stroke:#6B3D7A,stroke-width:2px,color:#fff
```

## 核心组件说明

### 1. 异步执行线程池 (asyncExecutor)

- **作用**：执行 `submitSync()` 任务，将状态同步键添加到共享缓冲区
- **类型**：`ExecutorService`（虚拟线程）
- **实现**：`Executors.newVirtualThreadPerTaskExecutor()`
- **特点**：
  - 使用 Java 21 虚拟线程，轻量级，可创建数百万个线程
  - 在 I/O 阻塞时自动释放平台线程，提高资源利用率
  - 无需手动管理线程池大小，由 JVM 自动调度

### 2. 定时刷新调度器 (flushScheduler)

- **作用**：定期检查共享缓冲区，如果有数据则触发刷写，确保数据及时持久化
- **类型**：`ScheduledExecutorService`（单线程虚拟线程）
- **实现**：`Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory())`
- **调度间隔**：`flushIntervalMs`（默认 2000ms）
- **特点**：
  - 即使未达到批量大小，也会定期刷写缓冲区中的数据
  - 确保数据及时持久化，避免长时间停留在缓冲区
  - 使用虚拟线程，资源占用极低

### 3. 共享缓冲区 (sharedBuffer & sharedCounter)

- **sharedBuffer**：`ConcurrentLinkedQueue<StateSyncKey>`，存储待同步的状态同步键
- **sharedCounter**：`AtomicInteger`，记录当前缓冲区中的消息数量
- **特点**：
  - 所有虚拟线程共享同一个缓冲区，支持跨线程批量累积
  - 使用 `ConcurrentLinkedQueue` 和 `AtomicInteger` 保证线程安全
  - 不再使用 ThreadLocal，避免了虚拟线程场景下的问题

### 4. 批量刷写锁 (flushLock)

- **作用**：保证批量刷写的原子性，避免多个线程同时触发刷写导致数据丢失
- **类型**：`Object`（synchronized 锁）
- **使用场景**：在 `doFlushBatch()` 方法中使用 `synchronized` 保护

### 5. 数据库类型检测 (dbType)

- **作用**：根据不同的数据库类型选择不同的批量插入或更新策略
- **检测方式**：通过 `DataSource` 的 `DatabaseMetaData` 自动检测
- **支持类型**：
  - MySQL：使用 `INSERT ... ON DUPLICATE KEY UPDATE`
  - PostgreSQL：使用 `INSERT ... ON CONFLICT ... DO UPDATE`
  - Oracle：使用 `MERGE INTO`
  - 其他：回退到查询+分离方式

## 数据流向

```
状态变更事件
    │
    ▼
submitSync(stateMachineName, businessId)
    │
    ▼
asyncExecutor.execute() [虚拟线程异步执行]
    │
    ▼
sharedBuffer.offer(StateSyncKey) [添加到共享缓冲区]
    │
    ▼
sharedCounter.incrementAndGet() [增加计数器]
    │
    ▼
检查触发条件：
  - currentCount >= bufferCapacity? → doFlushBatch() [紧急刷写]
  - currentCount >= batchSize? → doFlushBatch() [批量刷写]
    │
    ▼
定时刷新任务（每 flushIntervalMs 执行一次）
    │
    ├─→ 检查 sharedBuffer 是否有数据
    └─→ 有数据 → doFlushBatch() [定时刷写]
    │
    ▼
doFlushBatch() [使用 flushLock 保证原子性]
    │
    ├─→ drainBuffer() [从共享缓冲区批量取出数据]
    │
    ├─→ StateStorage.getCurrentState() [从 Redis 读取当前状态]
    ├─→ StateStorage.getStateHistory() [从 Redis 读取历史记录]
    │
    ▼
批量写入数据库：
    ├─→ writeCurrentStateBatch() [根据 dbType 选择策略]
    │   ├─→ MySQL: insertOrUpdateBatchForMysql()
    │   ├─→ PostgreSQL: insertOrUpdateBatchForPostgresql()
    │   ├─→ Oracle: insertOrUpdateBatchForOracle()
    │   └─→ 其他: writeCurrentStateBatchWithQuery() [查询+分离]
    │
    └─→ writeHistoryBatch() [批量写入历史记录]
```

## 时序图

```mermaid
sequenceDiagram
    participant Client as 外部调用者
    participant Manager as AsyncThreadStorageManager
    participant Executor as asyncExecutor<br/>(虚拟线程)
    participant Buffer as sharedBuffer<br/>(共享缓冲区)
    participant Counter as sharedCounter<br/>(计数器)
    participant Lock as flushLock<br/>(刷写锁)
    participant Redis as StateStorage<br/>(Redis)
    participant DB as Database<br/>(数据库)

    Note over Client,DB: 正常流程：提交状态同步任务

    Client->>Manager: submitSync(stateMachine, businessId)
    Manager->>Manager: 检查 isShuttingDown
    alt 服务正在关闭
        Manager-->>Client: 跳过同步（返回）
    else 服务正常运行
        Manager->>Executor: execute(processSyncTask)
        activate Executor
        
        Executor->>Buffer: offer(StateSyncKey)
        Buffer-->>Executor: 添加成功
        
        Executor->>Counter: incrementAndGet()
        Counter-->>Executor: currentCount
        
        Executor->>Executor: 检查触发条件
        
        alt currentCount >= bufferCapacity (紧急刷写)
            Executor->>Manager: doFlushBatch() [紧急]
        else currentCount >= batchSize (批量刷写)
            Executor->>Manager: doFlushBatch() [批量]
        else 未达到触发条件
            Executor-->>Client: 任务完成（等待下次触发）
        end
        
        deactivate Executor
    end

    Note over Manager,DB: 批量刷写流程（使用锁保证原子性）

    Manager->>Manager: doFlushBatch()
    Manager->>Lock: synchronized(flushLock)
    activate Lock
    
    Manager->>Buffer: drainBuffer()
    Buffer-->>Manager: List<StateSyncKey>
    Manager->>Counter: set(0) [重置计数器]
    
    alt 缓冲区为空
        Lock-->>Manager: 返回（无数据）
    else 有数据需要刷写
        Manager->>Redis: getCurrentState(syncKeys)
        Redis-->>Manager: 当前状态数据
        
        Manager->>Redis: getStateHistory(syncKeys)
        Redis-->>Manager: 历史记录数据
        
        Manager->>Manager: writeSyncDataToDatabase()
        
        par 批量写入当前状态
            Manager->>Manager: writeCurrentStateBatch()
            alt dbType == MYSQL
                Manager->>DB: insertOrUpdateBatchForMysql()
            else dbType == POSTGRE_SQL
                Manager->>DB: insertOrUpdateBatchForPostgresql()
            else dbType == ORACLE
                Manager->>DB: insertOrUpdateBatchForOracle()
            else 其他数据库
                Manager->>DB: writeCurrentStateBatchWithQuery()<br/>(查询+分离)
            end
        and 批量写入历史记录
            Manager->>DB: writeHistoryBatch()
            Manager->>DB: insert(histories)
        end
        
        DB-->>Manager: 写入成功
    end
    
    deactivate Lock
    Manager-->>Client: 批量刷写完成
```

### 定时刷新时序图

```mermaid
sequenceDiagram
    participant Scheduler as flushScheduler<br/>(定时调度器)
    participant Manager as AsyncThreadStorageManager
    participant Buffer as sharedBuffer<br/>(共享缓冲区)
    participant Lock as flushLock<br/>(刷写锁)
    participant Redis as StateStorage<br/>(Redis)
    participant DB as Database<br/>(数据库)

    Note over Scheduler,DB: 定时刷新流程（每 flushIntervalMs 执行一次）

    loop 每 flushIntervalMs 执行
        Scheduler->>Manager: 定时任务触发
        Manager->>Buffer: isEmpty()
        
        alt 缓冲区为空
            Manager-->>Scheduler: 跳过（无数据）
        else 缓冲区有数据
            Manager->>Manager: doFlushBatch()
            Manager->>Lock: synchronized(flushLock)
            activate Lock
            
            Manager->>Buffer: drainBuffer()
            Buffer-->>Manager: List<StateSyncKey>
            
            Manager->>Redis: getCurrentState()<br/>getStateHistory()
            Redis-->>Manager: 状态数据
            
            Manager->>DB: writeCurrentStateBatch()<br/>writeHistoryBatch()
            DB-->>Manager: 写入成功
            
            deactivate Lock
            Manager-->>Scheduler: 定时刷写完成
        end
    end
```

## 触发刷写的条件

1. **批量大小触发**：`sharedCounter >= batchSize`（默认 200）
  - 当计数器达到批量大小时，自动触发刷写
  - 每个虚拟线程在添加数据后都会检查此条件
2. **缓冲区容量触发**：`sharedCounter >= bufferCapacity`（默认 10000，紧急刷写）
  - 当缓冲区达到最大容量时，立即触发紧急刷写
  - 避免缓冲区溢出，保证系统稳定性
3. **定时触发**：`flushScheduler` 每 `flushIntervalMs`（默认 2000ms）执行一次
  - 定期检查共享缓冲区，如果有数据则触发刷写
  - 确保数据及时持久化，即使未达到批量大小也会定期刷写
  - 使用共享缓冲区，定时任务可以正常访问
4. **服务关闭触发**：`@PreDestroy` 方法中刷写剩余缓冲区
  - 优雅关闭时，确保所有待同步数据都被写入数据库

## 线程安全机制

- **共享缓冲区**：使用 `ConcurrentLinkedQueue` 保证线程安全的队列操作
- **共享计数器**：使用 `AtomicInteger` 保证线程安全的计数操作
- **批量刷写锁**：使用 `synchronized` 锁保证批量刷写的原子性
- **关闭标志**：`isShuttingDown` 使用 `AtomicBoolean` 防止关闭期间继续处理

## 数据库兼容性

### 支持的数据库及策略


| 数据库类型      | 批量插入或更新策略                          | SQL 语法                                 |
| ---------- | ---------------------------------- | -------------------------------------- |
| MySQL      | `insertOrUpdateBatchForMysql`      | `INSERT ... ON DUPLICATE KEY UPDATE`   |
| PostgreSQL | `insertOrUpdateBatchForPostgresql` | `INSERT ... ON CONFLICT ... DO UPDATE` |
| Oracle     | `insertOrUpdateBatchForOracle`     | `MERGE INTO ...`                       |
| 其他         | `writeCurrentStateBatchWithQuery`  | 查询+分离方式（兼容所有数据库）                       |


### 自动检测机制

- 在 `@PostConstruct` 方法中，通过 `DataSource.getConnection()` 获取连接
- 使用 `DatabaseMetaData.getDatabaseProductName()` 获取数据库产品名称
- 根据产品名称自动映射到对应的 `DbType`
- 检测失败时自动回退到查询+分离方式，保证兼容性

## 优雅关闭流程

```
@PreDestroy shutdown()
    │
    ├─→ isShuttingDown.set(true) [设置关闭标志]
    │
    ├─→ shutdownExecutor(flushScheduler) [关闭定时刷新调度器]
    │   └─→ 停止定时任务
    │
    ├─→ shutdownExecutor(asyncExecutor) [关闭虚拟线程执行器]
    │   └─→ 等待任务完成或超时后强制关闭
    │
    ├─→ doFlushBatch() [刷写剩余的共享缓冲区]
    │   └─→ 确保所有待同步数据都被写入数据库
    │
    └─→ 完成关闭
```

## 性能优化

### 虚拟线程优势

- **轻量级**：可以创建数百万个虚拟线程，不受平台线程数量限制
- **I/O 优化**：在 I/O 阻塞时自动释放平台线程，提高资源利用率
- **简化管理**：无需手动管理线程池大小，由 JVM 自动调度

### 批量操作优化

- **数据库原生语法**：支持的数据库使用原生批量插入或更新语法，性能更优
- **减少数据库交互**：从 3 次（查询+插入+更新）减少到 1 次（批量插入或更新）
- **智能回退**：不支持的数据库自动回退到查询+分离方式，保证功能正常

### 线程安全优化

- **无锁队列**：使用 `ConcurrentLinkedQueue` 实现高性能的并发队列
- **原子操作**：使用 `AtomicInteger` 实现无锁的计数操作
- **最小化锁范围**：只在批量刷写时使用锁，减少锁竞争

## 配置参数


| 参数               | 类型  | 默认值   | 说明                        |
| ---------------- | --- | ----- | ------------------------- |
| `batchSize`      | int | 200   | 批量写入数据库的记录数，达到此数量时触发批量写入  |
| `bufferCapacity` | int | 10000 | 最大缓冲条数，当缓冲区达到此大小时立即触发紧急刷写 |


## 注意事项

1. **虚拟线程要求**：需要 Java 21+ 版本支持
2. **数据库类型检测**：首次启动时会自动检测数据库类型，检测失败会回退到查询+分离方式
3. **批量大小设置**：根据实际业务量和数据库性能调整 `batchSize` 和 `bufferCapacity`
4. **关闭时机**：服务关闭时会自动刷写剩余缓冲区，确保数据不丢失

