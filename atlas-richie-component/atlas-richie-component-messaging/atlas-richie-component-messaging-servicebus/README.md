# Richie Messaging Azure Service Bus Component

Azure Service Bus 消息队列依赖管理组件，基于 Spring Cloud Azure Stream Binder。

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
    <artifactId>atlas-richie-component-messaging-servicebus</artifactId>
</dependency>
```

### 2. 配置 Azure Service Bus

```yaml
spring:
  cloud:
    stream:
      bindings:
        normalProcess-in-0:
          destination: normal-queue
          group: normal-subscription
        normalProcess-out-0:
          destination: normal-queue
    azure:
      servicebus:
        connection-string: Endpoint=sb://...
        # 或使用
        namespace: your-namespace
        queue-name: your-queue-name
```

### 3. 使用消息服务

参考 [richie-component-messaging README](../README.md) 了解如何使用 `MessageService` 发送和消费消息。

---

## ⚙️ 配置说明

### Azure Service Bus Binder 配置

| 配置项                                               | 说明                | 默认值 |
|---------------------------------------------------|-------------------|-----|
| `spring.cloud.azure.servicebus.connection-string` | Service Bus 连接字符串 | -   |
| `spring.cloud.azure.servicebus.namespace`         | Service Bus 命名空间  | -   |
| `spring.cloud.azure.servicebus.queue-name`        | Queue 名称          | -   |
| `spring.cloud.azure.servicebus.topic-name`        | Topic 名称          | -   |

### 详细配置

参考 [Spring Cloud Azure Service Bus Binder 文档](https://microsoft.github.io/spring-cloud-azure/current/reference/html/index.html#spring-cloud-stream-binder-for-azure-service-bus)

---

## 📖 参考文档

- [Spring Cloud Stream 文档](https://spring.io/projects/spring-cloud-stream#overview)
- [Spring Cloud Azure Service Bus 文档](https://microsoft.github.io/spring-cloud-azure/current/reference/html/index.html#spring-cloud-stream-binder-for-azure-service-bus)
- [Azure Service Bus 官方文档](https://docs.microsoft.com/azure/service-bus-messaging/)
- [richie-component-messaging README](../README.md)

---

## ✨ 特性

- ✅ 支持 Queue 和 Topic
- ✅ 支持会话（Session）
- ✅ 支持死信队列（DLQ）
- ✅ 支持事务消息
- ✅ 适合 Azure 云环境
