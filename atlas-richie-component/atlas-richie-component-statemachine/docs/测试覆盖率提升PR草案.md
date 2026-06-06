# atlas-richie-component-statemachine 测试覆盖率提升 PR 草案

> 状态：**待审核**
> 模块：`atlas-richie-component-statemachine`
> 目标：补齐 JaCoCo 覆盖盲区,提升核心组件单测覆盖率
> 方法：纯单元测试,**不需要 Docker / Redis**

---

## 1. 背景

### 1.1 当前状态

- **测试总数**：116 单元测试 + 3 集成测试 = 119 个,全部通过
- **构建状态**：`mvn clean verify -q` BUILD SUCCESS
- **JaCoCo 行覆盖率（bundle 维度）**：**86.9%**(238/274),已通过 85% 阈值校验

### 1.2 覆盖率盲区（jacoco scope 内）

通过 `target/site/jacoco/jacoco.csv` + `jacoco.xml` 行级分析,识别出 7 个未充分覆盖的类:

| 类 | 行覆盖率 | 未覆盖行 | 原因 |
|---|---|---|---|
| `StateMachineEvent$EnumEvent` | 36.4% (4/11) | 7 | `getEvent`、`toString`、`equals`(3 分支)、`hashCode` |
| `StateMachineName$EnumStateMachineName` | 36.4% (4/11) | 7 | `getStateMachineNameEnum`、`toString`、`equals`(3 分支)、`hashCode` |
| `StateMachineKeyBuilder` | 43.8% (7/16) | 9 | `getKeyPrefix` 默认回退、`buildHistoryKey`、`buildDbSync*Key`、`buildSeqKey` |
| `StateTransitionRule` | 86.5% (64/74) | 10 | 条件空值短路、日志分支、不安全表达式拒绝、表达式执行异常 |
| `ExpressionConfigHolder` | 92.3% (12/13) | 1 | 静态字段声明(不可避免,**保留**) |
| `StateMachineEvent$StringEvent` | 90% (9/10) | 1 | `toString` |
| `StateMachineName$StringStateMachineName` | 90% (9/10) | 1 | `toString` |

---

## 2. 改进方案

### 2.1 总体策略

**纯单元测试**,无外部依赖,不需要 Docker / Redis。共新增 **~16 个测试方法**,分布在 4 个测试文件中。

### 2.2 详细测试设计

#### 2.2.1 `StateMachineKeyBuilderTest` (+6 个测试)

覆盖 `StateMachineKeyBuilder` 9 个未覆盖行:

| 测试方法 | 覆盖目标 | 期望行为 |
|---|---|---|
| `getKeyPrefix_shouldReturnDefaultWhenNull` | L44 默认回退 | `getKeyPrefix(null)` 返回 `"sm"` |
| `buildHistoryKey_shouldUseCreateTimeEpoch` | L74-77 | 生成形如 `sm:history:default:tenant1:biz1:instance1:1700000000000` |
| `buildDbSyncQueueKey_shouldFollowConvention` | L118 | 生成 `sm:dbsync:queue:default` |
| `buildDbSyncSetKey_shouldFollowConvention` | L129 | 生成 `sm:dbsync:set:default` |
| `buildDbSyncLockKey_shouldFollowConvention` | L140 | 生成 `sm:dbsync:lock:default` |
| `buildSeqKey_shouldFollowConvention` | L159 | 生成 `sm:seq:default:tenant1:biz1:instance1` |

#### 2.2.2 `StateTransitionRuleTest` (+3 个测试)

覆盖 `StateTransitionRule` 10 个未覆盖行中的高价值路径:

| 测试方法 | 覆盖目标 | 期望行为 |
|---|---|---|
| `testEvaluateCondition_BlankCondition` | L109 短路 | `condition` 为 `null` / `""` / `"   "` → 命中 transition,无表达式求值 |
| `testExecuteAction_UnsafeExpression` | L148-149 安全 | `action` 为 `Runtime.getRuntime()` → 拒绝执行,记录 WARN |
| `testExecuteAction_InvalidExpression` | L155 异常路径 | 表达式语法错误 → 抛 `StateMachineException` |

> **注意**:慢日志/详细日志路径(L127/L129/L161/L163)只影响日志输出,不影响业务行为,且需要 mock 一个慢表达式才能触发,本次**不覆盖**。

#### 2.2.3 `StateMachineEventTest` (+5 个测试)

覆盖 `EnumEvent` 7 行 + `StringEvent` 1 行:

| 测试方法 | 覆盖目标 |
|---|---|
| `testEnumEvent_getEvent` | L125 |
| `testEnumEvent_toString` | L130 |
| `testEnumEvent_equals_DifferentEvent` | L135-138 不同 enum 值 |
| `testEnumEvent_equals_NullAndDifferentClass` | L135-138 null/不同类型 |
| `testEnumEvent_hashCode` + `testStringEvent_toString` | L143, L76 |

#### 2.2.4 `StateMachineNameTest` (+5 个测试)

覆盖 `EnumStateMachineName` 7 行 + `StringStateMachineName` 1 行:

| 测试方法 | 覆盖目标 |
|---|---|
| `testEnumStateMachineName_getStateMachineNameEnum` | L125 |
| `testEnumStateMachineName_toString` | L130 |
| `testEnumStateMachineName_equals_Different` | L135-138 |
| `testEnumStateMachineName_equals_NullAndDifferentClass` | L135-138 |
| `testEnumStateMachineName_hashCode` + `testStringStateMachineName_toString` | L143, L76 |

### 2.3 预期效果

| 指标 | 当前 | 目标 |
|---|---|---|
| 总测试数 | 119 | ~135 |
| `StateMachineKeyBuilder` 覆盖率 | 43.8% | **100%** |
| `StateMachineEvent$EnumEvent` | 36.4% | **100%** |
| `StateMachineName$EnumStateMachineName` | 36.4% | **100%** |
| `StateTransitionRule` | 86.5% | **95%+** |
| Bundle 行覆盖率 | 86.9% | **92%+** |
| 构建状态 | ✅ | ✅ |

---

## 3. 风险评估

| 风险 | 等级 | 缓解措施 |
|---|---|---|
| 改动现有测试文件导致回归 | 低 | 仅新增 `@Test` 方法,不修改现有断言 |
| 表达式安全测试可能影响生产逻辑判断 | 低 | 现有 `RuleExpressionValidator` 已就位,本测试仅验证拒绝行为 |
| 测试执行时间增加 | 极低 | 纯单元测试,预计 < 100ms |

---

## 4. 实施步骤

1. ✅ 覆盖率分析(已完成)
2. ⏳ **待审核**:PR 草案
3. 🔲 创建 `StateMachineKeyBuilderTest`(如不存在)
4. 🔲 在 `StateTransitionRuleTest` 新增 3 个测试方法
5. 🔲 创建 `StateMachineEventTest`
6. 🔲 创建 `StateMachineNameTest`
7. 🔲 运行 `mvn clean verify -q` 验证
8. 🔲 检查 `target/site/jacoco/jacoco.csv` 确认覆盖率达标
9. 🔲 提交 commit + push PR

---

## 5. 不在本次范围

| 类 | 原因 |
|---|---|
| `ExpressionConfigHolder` L(静态字段) | 不可避免,JaCoCo 不统计 static 字段初始化行 |
| `StateMachineEvent$StringEvent` (非 toString 部分) | 已有测试覆盖,仅 toString 缺 |
| `StateMachineEngine` 集成流程 | 不在 jacoco 范围内,但若有"全流程"需求可后续单独提 |

---

## 6. 验证命令

```bash
# 在 atlas-richie-component-statemachine 目录下
mvn clean verify -q

# 检查 jacoco 报告
open target/site/jacoco/index.html

# 查看具体未覆盖行
cat target/site/jacoco/jacoco.csv | sort -t, -k4 -n
```
