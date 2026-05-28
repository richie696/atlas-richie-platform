# sample-desensitize

数据脱敏组件演示模块，覆盖以下场景：

- API 返回值字段脱敏（`@Sensitive`）
- API 返回值 `Map` 键名脱敏（`sensitive-keys`）
- 日志参数脱敏（`SensitiveLogArg` + TurboFilter）
- 日志 JSON 文本脱敏（`%desensitizeJsonMsg`）
- MDC 字段脱敏（`SensitiveMdcTurboFilter`）

## 1. 启动

在项目根目录执行：

```bash
mvn -pl atlas-richie-component-template/sample-desensitize -am spring-boot:run
```

默认端口：`8891`

## 2. 演示接口

### 2.1 API 返回值脱敏

```bash
curl "http://localhost:8891/desensitize-sample/api"
```

预期现象：

- `phone` 字段返回 `138****8000`
- `idCard` 字段返回掩码结果
- `extra.phone`、`extra.idCard` 按 `sensitive-keys` 自动脱敏
- `extra.orderId` 保持原值

### 2.2 日志脱敏（参数 / JSON 文本 / MDC）

```bash
curl "http://localhost:8891/desensitize-sample/log"
```

预期现象（查看控制台日志）：

- `SensitiveLogArg.phone("13812348000")` 输出为 `138****8000`
- JSON message 中 `phone`/`idCard` 脱敏，`bizNo` 保持原值
- MDC 中 `phone` 脱敏，`traceId` 保持原值

## 3. 关键配置

`application.yml` 中已打开演示所需开关：

```yaml
platform:
  component:
    desensitize:
      enabled: true
      sensitive-keys:
        phone: PHONE
        idCard: ID_CARD
      log:
        features:
          auto-register-turbo-filters: true
          sensitive-log-arg-turbo-filter-enabled: true
          sensitive-mdc-turbo-filter-enabled: true
```

## 4. Logback 说明

`logback-spring.xml` 已配置：

- `%msg`（原始消息）
- `%desensitizeMsg`（参数脱敏视图）
- `%desensitizeJsonMsg`（JSON 文本脱敏视图）
- `%X{phone}` / `%X{traceId}`（MDC 输出）

可直观对比同一条日志在不同输出位的脱敏效果。

