# 代码规范

超越 checkstyle / spotbugs / PMD 已经强制的部分。这些是用户评审过程中逐步沉淀的规则。

## 已有自动检查的风格（已强制）

- Checkstyle via `maven-checkstyle-plugin`（仓库根目录 `checkstyle.xml`）
- SpotBugs 设为 `Max` 努力 / `Low` 阈值，运行在 `verify` phase
- PMD 6 个 rulesets：bestpractices / codestyle / design / errorprone / performance / security
- `maven-enforcer-plugin` 强制 Maven 3.9+ 和 JDK 25
- `compilerArgs` 包含 `-parameters` 和 `--enable-preview`

## Lombok

- 自由使用 `@Getter` / `@Setter` / `@RequiredArgsConstructor` / `@Slf4j`
- Builder 用 `@Builder`；有继承层级时用 `@SuperBuilder`
- **不要在需要正确 `equals` / `hashCode` 的类上用 `@Data`**——非平凡字段集要显式写
- **不要用 `@SneakyThrows` 处理应该被显式处理的 checked exception**

## MapStruct

- Mapper 接口放在它所映射的 model 旁边
- 需要 Spring 注入的 Mapper：`componentModel = "spring"`；默认 `nullValuePropertyMappingStrategy = IGNORE`
- 不要在同一个 mapper 里混用 MapStruct 和手动 getter——二选一

## Import

- 与相邻类保持一致。项目使用标准 IntelliJ 顺序；没有自定义星号导入。
- 不允许通配符导入。

## 命名

- 包：`com.richie.<layer>.<module>.<sub>`（如 `com.richie.component.cache.redis.perf`）
- 公开类：`PascalCase`；工具类：`<Name>Utils` 或 `<Name>Helper`，都可以
- 常量：`UPPER_SNAKE`
- 自动配置类：`<Module>AutoConfiguration`（Spring Boot 约定）
- Properties：`@ConfigurationProperties(prefix = "platform.component.<module>")`

## 组件内部的分层

通用模式（以 `atlas-richie-component-cache` 为典范）：

```
com.richie.component.<name>/
├── <Name>AutoConfiguration.java          # @Configuration，按 classpath 条件加载
├── <Name>Properties.java                  # @ConfigurationProperties
├── <Name>Template.java / Abstract<Name> # provider-specific 实现的模板方法
├── core/                                 # 公开 API 表面
├── <provider>/                           # 每个 provider 一个子包
│   └── <Name><Provider>Engine.java
├── perf/                                 # 兜底（guards、inspectors）
└── config/                               # 任何 Spring @Bean 定义
```

公开 API 放在 `core/`；provider 特定代码留在 `<provider>/`。从 `core/` 跨包 import 进 `<provider>/` 是 bug。

## 源码头

- Apache 2.0 license header 由 `license-maven-plugin`（`apache_v2`）管理
- 新文件跑 `mvn license:update-file-header` 补头
- `inceptionYear` 是 2024；新文件沿用现有 header

---

## Javadoc 规范

目标：**阅读者能在 30 秒内理解类/方法的职责与核心逻辑**。

### 类 / 接口文档

所有对外可见的类、接口、枚举**必须**使用 Javadoc：

```java
/**
 * 简要说明该类/接口的职责、场景和核心设计意图。
 *
 * <p>必要时补充：
 * - 使用场景（例如：用于处理对账任务的调度入口）；
 * - 关键约束（例如：线程安全性、一致性假设、性能假设）。
 *
 * @author richie696
 * @since 2026-03-15
 */
public class ExampleService {
}
```

规则：
- **`@author`**：必须包含，当前开发者的姓名/花名/工号
- **`@since`**：必须包含，格式 `yyyy-MM-dd`，填写首次引入该类的日期
- 内容至少包含：一句话功能说明、主要职责/使用场景
- 可选：线程安全、幂等性、性能假设等关键设计要点

### 方法文档

必须为以下方法编写 Javadoc：
- 公开 Service 的 public 方法（Application Service / Domain Service）
- Controller / Subscriber 的对外入口方法
- 工具类/公共组件的 public 方法
- 复杂业务逻辑方法（package-private 也推荐写）

简单的 getter/setter、显而易见的委托方法可以省略 Javadoc，**但不能用无意义的注释**。

方法 Javadoc 示例：

```java
/**
 * 【一句话说明：做什么】
 *
 * <p>补充说明：
 * - 业务背景或约束条件；
 * - 重要的前置条件/后置条件；
 * - 典型错误码对应的业务含义。
 *
 * @param request 入参含义（写业务含义，不写"请求对象"这种空话）
 * @return 返回结果的业务含义
 */
public ApiResult<OrderVO> createOrder(CreateOrderRequest request) {
}
```

规则：
- `@param` 必须体现**业务语义**，如"用户下单请求，含商品明细和支付方式"，不写"请求参数"
- `@return` 描述返回值的**关键业务信息**
- 若返回 `ApiResult`，在注释中说明成功 code 约定和典型错误码

### 行内注释

用途：
- 解释**一段代码中不直观的业务规则或边界情况**
- 指明"看起来多余"的代码存在的必要性

禁止：
- 注释显而易见的语句：`i++; // i 自增`、`return result; // 返回结果`

推荐：

```java
// 由于第三方接口最多只支持 50 条明细，每次拆分为 50 条一批，避免报错
List<List<OrderItem>> batches = Lists.partition(items, 50);
```

### 块注释（多行）

适用场景：
- 在方法内部，对某一大段逻辑的**整体意图**做说明
- 解释一段算法或流程的整体思路
- 对异常边界、历史兼容逻辑做说明

推荐：

```java
/*
 * 对账逻辑说明：
 * 1. 先根据本地账单分组聚合，按业务主键建立索引；
 * 2. 再逐条扫描第三方账单，对上一步的索引进行匹配；
 * 3. 允许金额在小于 1 分的误差范围内视为匹配成功；
 * 4. 未匹配项记录到差异表，供人工复核。
 */
```

### 复杂逻辑的示例与公式

对于明显不直观的复杂算法，可在 Javadoc 中增加示例或算法说明：

```java
/**
 * 使用 EMA（指数移动平均）平滑交易金额波动：
 *
 * EMA_t = alpha * amount_t + (1 - alpha) * EMA_{t-1}
 *
 * 其中：
 * - alpha 由配置决定，范围 (0, 1]；
 * - EMA_0 取首笔交易金额。
 */
```

## 注释原则

- 解释**为什么**，不解释**是什么**
- `// TODO(name): 原因`——不要裸 `// TODO`
- 正在进行的迁移，加上注释说明 `@MigrationWindow` 截止日期
- 修改逻辑时必须同步更新相关注释；来不及更新宁可先删掉错误注释

**禁止**：
- 长期注释掉的大段旧代码（用 git 追溯历史，不在代码中保留"坟场"）
- 与实现严重不符的注释

---

## 不要做的事（反复出现的违规项）

- 单元测试里用 `Thread.sleep`（用 Awaitility）
- 生产代码里用 `System.out.println`
- 捕获 `Throwable` / `Exception` 然后吞掉
- 源码里硬编码 URL / IP / 密钥
- Spring `@Autowired` 字段注入——优先用构造器注入（Lombok `@RequiredArgsConstructor`）
- `static` 字段持有大对象——GC 压力
- `ThreadLocal` 做跨切上下文——改用 context 模块（`atlas-richie-context`）
- Provider 特定类型（如 `software.amazon.awssdk.services.s3.S3Client`）泄露进 `core/`
