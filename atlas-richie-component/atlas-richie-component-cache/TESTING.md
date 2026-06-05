# atlas-richie-component-cache 测试规范（蓝本）

> 其他 `atlas-richie-component-*` 模块请参照本目录结构与 Maven 配置复制。

## 目标

- **行覆盖率 ≥ 80%**（JaCoCo `jacoco:check`，见 `pom.xml`）
- **单元测试**：快、Mock 外部 I/O，覆盖分支与异常
- **集成测试**：Testcontainers（Docker）默认，验证 Ops 闭环

## 目录与命名

```
src/test/java/com/richie/component/cache/
├── commons/              # 纯工具单测          → *Test.java
├── operations/           # 容量/转换纯逻辑     → *Test.java
├── ops/
│   ├── impl/             # Ops 委托 + 守卫     → *OpsImplTest.java
│   └── L2SyncHelperTest  # L2 门控逻辑
├── redis/
│   ├── perf/             # 性能守卫            → *Test.java
│   ├── migration/        # 迁移窗口            → *Test.java
│   └── manage/           # 小型领域对象        → *Test.java
├── integration/          # Redis 集成          → *IT.java
└── support/              # 基类、TestConfig、Mock 辅助
    ├── AbstractRedisIntegrationTest.java
    ├── CacheIntegrationTestConfiguration.java
    ├── RedisIntegrationTestSupport.java
    └── OpsTestSupport.java
```

| 类型   | 类名后缀    | Maven 阶段 | 插件       |
|------|---------|----------|----------|
| 单元测试 | `*Test` | `test`   | surefire |
| 集成测试 | `*IT`   | `verify` | failsafe |

Surefire **排除** `*IT.java`，避免与 Failsafe 重复执行。

## 运行命令

```bash
# 仅单元测试（无需 Docker）
mvn -pl atlas-richie-component/atlas-richie-component-cache test

# 单元 + 集成 + 覆盖率门禁（需 Docker 已启动）
mvn -pl atlas-richie-component/atlas-richie-component-cache verify

# CI：无 Docker 时直接失败，避免静默跳过 IT
IT_REQUIRE_DOCKER=true mvn -pl atlas-richie-component/atlas-richie-component-cache verify

# 查看报告（仓库根目录 coverage-reports/{artifactId}/）
open coverage-reports/atlas-richie-component-cache/index.html
```

### 可选：本机已有 Redis（勿用于 CI / 勿提交到仓库）

仅当想跳过容器启动、且本机 Redis 可用时：

```bash
export IT_USE_EXTERNAL=true          # 或 CACHE_IT_USE_EXTERNAL=true（兼容）
export CACHE_IT_REDIS_HOST=localhost # 或 REDIS_IT_HOST
export CACHE_IT_REDIS_PORT=16379     # 或 REDIS_IT_PORT
export CACHE_IT_REDIS_PASSWORD='your-password'
export CACHE_IT_REDIS_DATABASE=15    # 默认 15，避免污染 0 号库

mvn -pl atlas-richie-component/atlas-richie-component-cache verify
```

未设置 `IT_USE_EXTERNAL`（或 `CACHE_IT_USE_EXTERNAL`）时，即使 shell 里残留 `*_IT_REDIS_*` 也会**优先走 Docker**，避免换机器连错环境。

公共集测框架详见 [`atlas-richie-testing-support/README.md`](../../atlas-richie-base/atlas-richie-testing-support/README.md)；Maven 插件模板见 `atlas-richie-component-dependencies` 的 `pluginManagement`。

## 单测编写约定

1. **纯逻辑**：无 Spring 上下文，JUnit 5 + AssertJ。
2. **OpsImpl / Manager**：`@ExtendWith(MockitoExtension.class)`，`@Mock` Function/Helper，`@InjectMocks` 被测类。
3. **L2 路径**：使用 `OpsTestSupport.passthroughL2Get(...)` 让 mock L2 执行 Redis loader。
4. **断言**：优先 AssertJ（`assertThat(...).isEqualTo(...)`）。

## 集成测试约定

1. 继承 `AbstractRedisIntegrationTest`。
2. **Redis 连接策略**（见 `RedisIntegrationTestSupport`）：
   - **默认**：Docker 可用 → Testcontainers `redis:7-alpine`（`flushDb`，隔离、可复现）
   - **opt-in**：`IT_USE_EXTERNAL=true` + 连接信息 → 本机/共享 Redis（`SCAN` 清理 `it:*`，兼容禁 `FLUSHDB/KEYS`）
   - **无 Docker 且未 opt-in 外部** → IT 跳过（`@EnabledIf`），`mvn test` 不失败
   - **CI 推荐**：`IT_REQUIRE_DOCKER=true`，无 Docker 时构建失败
3. 测试 key 使用 `it:` 前缀；通过 **`GlobalCache.<ops>()`** 走完整 Spring 装配链。

## 覆盖率排除项（JaCoCo）

JaCoCo 采用 **includes 白名单 + excludes 接口** 策略（见 `pom.xml`），仅统计可用单测稳定覆盖的类型：

| 计入                                            | 不计入                                           |
|-----------------------------------------------|-----------------------------------------------|
| `ops.impl.*`、`L2SyncHelper`                   | Ops 接口、`CacheInfrastructure`                  |
| `commons.*`（除 DTO）                            | `function/**`、`*Manager`、`*AutoConfiguration` |
| `operations/BoundedList*`、`Set/ZSetCapacity*` | `BoundedQueue/Stack`（由 `*IT` 覆盖）              |
| `redis.perf.*`、`redis.migration.*`            | `local/**`、`config/**`、`enums/**`             |

`mvn verify` 合并 `jacoco.exec` + `jacoco-it.exec` 后执行 **行覆盖率 ≥ 80%** 门禁。

## 集成测试清单（本模块）

| 类                | 验证能力                                   |
|------------------|----------------------------------------|
| `ValueOpsIT`     | String KV、setIfAbsent、increment、Ops 注入 |
| `L2ValueOpsIT`   | L2 读写、removeCache                      |
| `KeyOpsIT`       | hasKey、TTL、删除                          |
| `BoundedQueueIT` | FIFO、满则驱逐、drain、grow                   |
| `BoundedStackIT` | LIFO、满则拒绝                              |

## 复制到新模块的检查清单

- [ ] `pom.xml`：surefire 排除 `*IT`、failsafe 包含 `*IT`、jacoco 80%
- [ ] 依赖 `atlas-richie-testing-support` + 声明 surefire/failsafe/jacoco 插件
- [ ] `src/test/.../support/`：组件专属 `@SpringBootTest` 配置与基类
- [ ] 纯逻辑类：`*Test` 优先
- [ ] 门面 OpsImpl：Mock Function 单测
- [ ] 每类对外能力：至少 1 个 `*IT` happy path
- [ ] CI：`IT_REQUIRE_DOCKER=true` + `mvn verify`（Docker runner）
