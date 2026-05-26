# Richie Messaging Azure Event Hubs Component

Azure Event Hubs 消息队列依赖管理组件，基于 Spring Cloud Azure Stream Binder。

## 📋 目录

- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [参考文档](#参考文档)

---

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-messaging-core</artifactId>
</dependency>

<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-messaging-eventhubs</artifactId>
</dependency>
```

### 2. 配置 Azure Event Hubs

```yaml
spring:
  cloud:
    stream:
      bindings:
        normalProcess-in-0:
          destination: normal-event-hub
          group: normal-consumer-group
        normalProcess-out-0:
          destination: normal-event-hub
    azure:
      eventhubs:
        connection-string: Endpoint=sb://...
        # 或使用
        namespace: your-namespace
        event-hub-name: your-event-hub
```

### 3. 使用消息服务

参考 [richie-component-messaging README](../README.md) 了解如何使用 `MessageService` 发送和消费消息。

---

## ⚙️ 配置说明

### Azure Event Hubs Binder 配置

| 配置项                                              | 说明               | 默认值 |
|--------------------------------------------------|------------------|-----|
| `spring.cloud.azure.eventhubs.connection-string` | Event Hubs 连接字符串 | -   |
| `spring.cloud.azure.eventhubs.namespace`         | Event Hubs 命名空间  | -   |
| `spring.cloud.azure.eventhubs.event-hub-name`    | Event Hub 名称     | -   |

### 详细配置

参考 [Spring Cloud Azure Event Hubs Binder 文档](https://microsoft.github.io/spring-cloud-azure/current/reference/html/index.html#spring-cloud-stream-binder-for-azure-event-hubs)

---

## 📖 参考文档

- [Spring Cloud Stream 文档](https://spring.io/projects/spring-cloud-stream#overview)
- [Spring Cloud Azure Event Hubs 文档](https://microsoft.github.io/spring-cloud-azure/current/reference/html/index.html#spring-cloud-stream-binder-for-azure-event-hubs)
- [Azure Event Hubs 官方文档](https://docs.microsoft.com/azure/event-hubs/)
- [richie-component-messaging README](../README.md)

---

## ✨ 特性

- ✅ 支持 Azure 身份验证
- ✅ 支持消费者组
- ✅ 支持分区
- ✅ 高吞吐量、低延迟
- ✅ 适合 Azure 云环境
