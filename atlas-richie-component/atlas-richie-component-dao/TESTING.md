# atlas-richie-component-dao 测试说明

本模块为 **MyBatis-Plus 基础设施组件**，仅编写**单元测试**，不配置集成测试（`*IT` / Testcontainers）。

## 运行测试

```bash
# 单测
mvn -pl atlas-richie-component/atlas-richie-component-dao test

# 单测 + JaCoCo 覆盖率报告 + 门禁（推荐）
mvn -pl atlas-richie-component/atlas-richie-component-dao verify
```

> `mvn test` 只跑用例；**覆盖率 HTML 在 `verify` 阶段生成**。有无集成测试不影响报告产出，仅单测的 `jacoco.exec` 会参与 merge。

## 覆盖率报告

`verify` 成功后查看：

```
coverage-reports/atlas-richie-component-dao/index.html
```

同目录还有 `jacoco.xml`、`jacoco.csv`，可供 CI 或 Sonar 消费。

## 门禁规则

继承父 `atlas-richie-component-dependencies` 默认配置：

- 统计范围：`com/richie/component/**`
- 排除：`*AutoConfiguration`、`config/**`、`enums/**`
- 行覆盖率下限：**85%**

## 测试类一览

| 类 | 被测目标 |
|----|----------|
| `IdBuilderTest` | 雪花 ID 生成 |
| `BatchUpdateLimitInterceptorTest` | 批量更新拦截 |
| `PaginationInterceptorTest` | 分页排序列转换 |
| `DefaultFieldHandlerTest` | 审计字段自动填充 |
| `DaoPropertiesTest` | 配置默认值 |
