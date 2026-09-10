# Multi-store Core 测试设计

> 范围：Named Multi-store 的配置、核心契约、连接生命周期与拓扑装配。  
> 边界：Fake Factory 只能证明 Core 编排语义；真实客户端、连接池和 Provider 请求必须在各 Adapter 集成测试中验证。

## 风险矩阵

| ID | 优先级 | 风险 | 最低证据 | 后续真实证据 |
|---|---|---|---|---|
| T-CORE-001 | P0 | 多个 Store 重复创建同一物理连接 | Core 单元测试断言同一 `connectionId` 只 open 一次 | Provider 客户端创建计数 |
| T-CORE-002 | P0 | 同 Provider 的不同 Connection 错误共享资源 | Core 单元测试断言两个 ID 得到不同 Handle | 两个真实连接/连接池隔离 |
| T-CORE-003 | P0 | required Store 初始化失败后应用继续启动 | Bootstrap 单元测试断言失败并回滚已开资源 | Spring Context 启动失败测试 |
| T-CORE-004 | P0 | optional Store 失败后错误回退到其他 Store | Bootstrap 单元测试断言 Registry 不含失败 Store且记录降级 | Provider 故障集成测试 |
| T-CORE-005 | P0 | 关闭遗漏或重复导致连接泄漏 | 单元测试断言逆序、幂等关闭及异常聚合 | 客户端/连接池真实生命周期 |
| T-CORE-006 | P1 | 缺少或重复 Provider Factory 时路由错误 | 单元测试断言初始化失败 | Spring 自动装配契约测试 |
| T-CORE-007 | P1 | 连接定义与 Factory/Handle Provider 不一致 | 单元测试 fail-fast | Adapter 契约测试 |
| T-CORE-008 | P1 | legacy 单 Store 被新生命周期接管后行为变化 | legacy 自动装配回归 | 现有项目启动与基础检索回归 |
| T-CORE-009 | P0 | 单 Named Store 的兼容 Service 与 Registry 指向不同实例 | Spring 自动装配断言对象同一性 | 现有单库项目基础检索回归 |
| T-CORE-010 | P0 | 多 Named Store 仍暴露全局 Service 导致错误路由 | Spring 自动装配负例断言无全局 Service | 消费项目按授权 Store 路由验收 |
| T-CORE-011 | P0 | 一个 Store 健康失败扩大为所有 Store 失败 | 双 Store 独立探针与 DEGRADED 聚合断言 | 两个异构 live Provider 故障注入 |
| T-CORE-012 | P0 | 健康和错误输出泄露 Provider 地址/异常正文 | 快照字段与异常消息负向断言 | Actuator/Metrics/Trace 输出审计 |
| T-CORE-013 | P0 | 指标标签或 Trace 携带查询/物理索引等高基数敏感值 | 通用事件字段、标签键和索引指纹断言 | 实际观测后端输出审计 |
| T-CORE-014 | P0 | 不同 Store 的模型归一化或模态约定串用 | 双 Store Binding、默认值、显式绑定和指纹断言 | 实际模型向量范数与跨模态检索验证 |
| T-PG-001 | P0 | 同连接的两个 PG Store 串表 | Factory/Store-bound SQL 契约测试 | 真实 PGVector 两表写入与检索 |
| T-PG-002 | P0 | `SET LOCAL` 与 SELECT 不在同一连接/事务 | 纯 JDK JDBC 代理时序断言 | `current_setting` 与查询计划证据 |
| T-PG-003 | P0 | 未配置高级参数仍改写数据库会话 | 无参数调用断言不执行 `set_config` | 真实默认请求 SQL/会话观测 |
| T-PG-004 | P0 | 请求借逻辑索引名越权访问任意表 | Named Handle 负例拒绝未声明索引 | 跨 Store 负向集成测试 |
| T-WEA-001 | P0 | hybrid 的关键词分支绕过 ACL | Java Client 回环 HTTP 请求断言同一 resolver 含 `where` + `hybrid` | live Weaviate 跨租户负例 |
| T-WEA-002 | P0 | 请求借索引名访问其他 Class | Named ACL hybrid 入口在网络前拒绝未声明索引 | live Weaviate 跨 Class 负例 |
| T-QDR-001 | P0 | 请求借逻辑索引名访问任意 Collection | Named Handle 在网络前拒绝未声明索引 | live Qdrant 跨 Collection 负例 |
| T-QDR-002 | P1 | 创建 Named Handle 时执行启动期网络探测 | 无 Provider 的 Factory 契约测试 | live Qdrant 生命周期验证 |
| T-QDR-003 | P1 | Qdrant 宣称未接通的高级能力 | Capability 精确集合断言 | 每个后续能力的数据面请求测试 |
| T-RED-001 | P0 | 同 Redis Connection 下两个 Store 串索引或 Key 前缀 | 双 Handle 白名单负例 | live Redis Stack 双索引写入与检索 |
| T-RED-002 | P0 | ACL 字段未进入 Provider 过滤 | 类型化编译器转义/能力集合断言 | live Redis Stack 跨租户负例 |
| T-RED-003 | P1 | 未填写高级配置时装配期修改 Redis | `initialize-schema=false` Factory 测试 | live Redis 命令观测 |
| T-MON-001 | P0 | MongoDB 逻辑 Store 串 Collection/Search Index | 双 Handle 白名单负例 | live Atlas 双 Collection 检索 |
| T-MON-002 | P0 | 未建 Filter 字段却声明 ACL | 条件 Capability 集合断言 | live Atlas 跨租户负例 |
| T-NEO-001 | P0 | Label 被误作 Vector Index 或 Session 串 database | 受控派生名与 SessionConfig 契约测试 | live Neo4j 双 database/Label 检索 |
| T-NEO-002 | P0 | 召回后 Filter 被误报为 ACL-safe | Capability 负向断言 | 构造候选截断数据集验证 |
| T-VIK-001 | P0 | Store 逻辑索引串 Collection/Index | 双 Handle 白名单负例 | live VikingDB 双 Collection 检索 |
| T-VIK-002 | P1 | Android fastjson2 破坏 SDK 签名 | Maven 依赖树断言 JVM 版 2.0.58 | live SDK ping/检索 |
| T-DASH-001 | P0 | DashVector 的 sparse 写入和原生 hybrid 使用不同 ACL 条件 | Factory/过滤适配器/能力集合断言 | live DashVector 跨租户负例与 collection 清理 |
| T-TCV-001 | P0 | 腾讯云 VectorDB 的 native 与 Core RRF 回退任一分支绕过 ACL | 回退分类、两路 filter 形态与能力集合断言 | live VectorDB 跨租户负例、非能力错误不回退和 collection 清理 |
| T-ALL-001 | P0 | 多个 Provider Jar 同 classpath 抢占默认实例或创建外部资源 | 独立组合测试模块加载 10 个 AutoConfiguration | 业务应用完整依赖集启动回归 |

## VMS-004 自动化用例

### T-CORE-001：连接按 ID 复用

- 前置：两个 Store 引用同一个 Connection。
- 操作：装配拓扑。
- 断言：Factory `openConnection` 调用一次；两个 Store 创建时收到同一个 Connection Handle。
- 清理：关闭拓扑运行时，Handle 关闭一次。

### T-CORE-002：相同 Provider 多连接隔离

- 前置：两个 Connection 使用同一个 Provider。
- 操作：分别获取连接。
- 断言：创建两个不同 Handle；按 ID 精确返回；不得按 Provider 合并。

### T-CORE-003：required Store 失败回滚

- 前置：先成功创建一个 Store，再让 required Store 创建失败。
- 操作：启动拓扑。
- 断言：启动失败；已创建 Store/连接按逆序关闭；不存在半初始化 Registry。

### T-CORE-004：optional Store 隔离

- 前置：required Store 成功、optional Store 失败。
- 操作：启动拓扑。
- 断言：Registry 只包含成功 Store；降级描述包含失败 Store ID 和非敏感错误分类；查找失败 Store 不回退。

### T-CORE-005：确定性关闭

- 前置：打开两个 Connection。
- 操作：连续调用两次 `close()`，并让一个 Handle 关闭时报错。
- 断言：每个 Handle 最多关闭一次；关闭顺序与打开顺序相反；其余资源仍被关闭；最终抛聚合异常。

## 证据记录规则

- Core 单元测试使用纯 JDK Fake/Proxy，不使用 Mockito 自附加能力。
- 错误断言只能包含 Store/Connection ID 和错误分类，不包含设置值。
- `VMS-004` 只有在 Core 测试全部通过后才能勾选；真实 Provider 生命周期仍由 `VMS-008` 至 `VMS-010` 和 `VMS-014` 验证。
- PGVector Factory/事务测试只证明装配、SQL 形态和 JDBC 调用时序；在真实扩展中确认表路由、索引类型和会话参数前，不勾选 `VMS-009` 父任务。
- Weaviate 回环 HTTP 测试使用真实 Java Client 和真实请求序列化，但不是 live Provider 行为证据；跨租户数据集验证完成前不勾选 `VTC-007` 父任务。
- Qdrant Factory 测试只证明独立 Client、受控 Collection 映射、关闭生命周期和能力不虚报；真实 Collection CRUD/检索完成前不勾选对应 Provider 父任务。
- Redis Factory 与 legacy 配置/装配测试共 8 个无 Mockito 用例通过；旧 `RedisVectorServiceImplTest` 的 68 个用例在 JDK 25 下被 Mockito/Byte Buddy 自附加初始化阻断，未进入断言，不能记为业务失败或回归通过。
- MongoDB Atlas、Neo4j、VikingDB Factory 各 5 个无 Mockito 契约测试通过；这些测试证明配置、资源归属、逻辑白名单和 Capability 边界，不替代真实云服务/数据库的请求与效果证据。
- `atlas-richie-vector-integration-tests` 在同一 Spring Context 加载 10 个 Provider 自动配置，断言 10 个 Factory 均存在且无未配置 Client、DataSource、Driver 或 VectorStore Bean。
- `MilvusPostgresqlLiveCoexistenceTest` 在两个真实本地 Provider 中验证同一 Spring Context 的命名 Store 隔离：连接、物理 schema/collection、写入/检索、调优参数和 Store/Provider 观测标签必须分别归属；trace 属性不含检索正文。
- Legacy 自动装配测试证明旧 Service 被同一对象包装为 `default` Store，单 Named Store 的兼容 Bean 与 Handle Service 是同一对象，多 Named Store 不暴露全局 Service；这仍不替代现有项目的真实基础检索回归。
- Store 诊断测试覆盖 UP/DOWN/UNKNOWN、单 Store 故障隔离、optional 启动失败、legacy `default` 聚合、route/capability 稳定错误分类，并断言 Provider 异常正文不进入快照。
- Store 观测测试覆盖成功/失败事件、稳定错误分类、固定 metric 标签集合、脱敏 trace 属性、Hook 故障隔离及装配期可选启用；这些是导出契约证据，实际 Micrometer/OpenTelemetry 后端绑定仍需集成验证。
- Embedding Binding 测试覆盖精确 Bean、维度、`UNSPECIFIED`/`UNIT_L2` 归一化、TEXT/IMAGE 模态、脱敏模型对象和契约参与指纹；真实模型是否遵守声明仍需消费项目或 Provider 集成验证。
