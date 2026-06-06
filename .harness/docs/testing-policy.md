# 测试策略

## 现状数据（2026-06-01）

- 891 个 Java 文件
- 31 个测试文件（按文件数计约 3.5% 覆盖率）
- 测试分布：statemachine (12)、desensitize (10)、ai (4)、vector (3)、gateway (2)、rest (0)
- CI 运行 `mvn -B clean verify -DskipTests -pl '!atlas-richie-component-template' -am`——测试被跳过
- jacoco 最低覆盖率 `0.50`（LINE coverage，PACKAGE 级别）——在 `pom.xml` 中定义了但**未绑定到 `verify`**，所以实际上没有门禁

## 约定

- `**/src/test/java/**/*Test.java` → Surefire 默认拾取（单元测试）
- `**/src/test/java/**/*IT.java` / `*ITCase.java` / `*IntegrationTest.java` → Failsafe（`integration-test` / `verify` phase）
- 所有测试运行都加 `--enable-preview`（JDK 25 preview features）

## 框架默认

- **JUnit 5**（Spring Boot 4.0.6 默认）
- 模块已用 AssertJ 就用 AssertJ；否则用普通 JUnit `Assertions` 即可
- **Mockito** 做 Mock（Spring Boot starter 自带）
- **Spring Test** 做 `@SpringBootTest` / `@SpringJUnitConfig`
- **Awaitility** 做异步 / 时序相关测试（单元测试里不用 `Thread.sleep`）

---

## 测试重点：Service 层

**所有与业务逻辑相关的单元测试 / 组合测试，集中在 Service 层完成**。

### Application Service 测试

覆盖：
- 核心用例的主业务流程与主要异常分支
- 并发场景中的关键路径（成功、部分失败、全部失败）
- 事务边界、幂等性处理
- 调用下游服务 / Repository 时的 Mock 策略

### Domain Service 测试

覆盖：
- 业务规则的各种输入组合
- 尽量用纯内存测试，无外部 IO 依赖

### Controller / Subscriber 测试

**不做业务逻辑测试**。只做少量集成级别验证：
- 参数绑定是否正确
- 是否调用了正确的 Service 方法
- `ApiResult` → HTTP 协议转换是否正确
- Subscriber 在失败时是否正确执行重试或写入死信队列（DLQ）

### Repository 测试

- 不直接测 SQL 字符串
- 优先通过 Application Service 的集成测试来覆盖 Repository 路径
- 若需单独测 Repository，聚焦在查询方法的边界条件（空结果、分页、排序）

## 测什么

- 公开类的公开契约：测
- package-private 实现细节：一般不测
- 静态工具方法：测（成本低，价值高）
- `ConfigurationProperties` 绑定：测（每个属性至少一个测试，验证约束）
- 自动配置：测（一个测试验证 bean 接线按 classpath 条件生效）
- Provider 特定行为（storage / vector / messaging 族）：按 provider 测

## 不测什么

- 生成代码（MapStruct、Lombok、target/generated-sources）
- 平凡 getter / setter
- 纯 DTO
- "冒烟"测试——只实例化类然后 assert 非空

## 文件 / 类命名

- `XxxTest.java` — 主要单元测试
- `XxxIT.java` — 集成测试（Spring context、DB、网络）
- `XxxTestSupport.java` — 共享 fixture builder
- 文件名与被测类对应（Spring Boot 约定）

## 新测试的约定

```java
// ✅ 好
@Test
@DisplayName("key 为 null 时应抛出 IllegalArgumentException")
void shouldThrowWhenKeyIsNull() {
    assertThatThrownBy(() -> cache.addString(null, "v", 1000))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("key");
}

// ❌ 差
@Test
void test1() { ... }

@Test
public void testCacheAdd() {
    try { ... } catch (Exception e) { /* ignore */ }
}
```

测试名描述行为：`shouldThrowWhenKeyIsNull()`，不是 `test1()`。

## 覆盖率政策

- **趋势工程，不搞英雄主义**。不要试图一次 PR 把覆盖率从 3.5% 干到 80%。
- **按爆炸半径选模块**。公开消费者最多的组件应优先有最深的测试。最近提交历史是很好的信号——正在活跃开发的模块值得新测试。
- **不要降低 jacoco 最低标准** 来让检查通过。50% 的底线是有意的；如果某个模块达不到，暴露缺口，不要隐藏它。
- **已知 bug 的新回归测试永远有价值**，即使对覆盖率数字没帮助。

## 缺口在哪里（优先级顺序）

1. `atlas-richie-component-cache` — 106 个文件，0 个测试。`redis/perf/` 兜底层尤其值得测试，因为它是正确性关键路径。
2. `atlas-richie-component-storage` — 68 个文件，0 个测试。多实现；至少每个 provider 一个测试。
3. `atlas-richie-component-mfa` — 71 个文件，0 个测试。安全敏感。
4. `atlas-richie-gateway-service` — 96 个文件，2 个测试。
5. `atlas-richie-component-mqtt` — 58 个文件，0 个测试。
6. `atlas-richie-component-logging` — 23 个文件，0 个测试。
7. `atlas-richie-component-vector` — 37 个文件，3 个测试。有 provider 表面。

## 不要做的事

- 单元测试里不用 `Thread.sleep`
- 不用 `@Disabled` 让失败测试通过
- 不加纯刷覆盖率的测试（没有实际测行为）
- 不用 `assertEquals` 配合 `toString()`——assert 实际字段
- 单元测试里不用真实 Redis / DB / MQ——Mock 或在 IT 级别用 Testcontainers
