# Richie Messaging Apache Pulsar Component

Apache Pulsar 消息队列依赖管理组件，基于 Spring Cloud Stream Pulsar Binder。

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
    <artifactId>atlas-richie-component-messaging-pulsar</artifactId>
</dependency>
```

### 2. 配置 Apache Pulsar

```yaml
spring:
  cloud:
    stream:
      bindings:
        normalProcess-in-0:
          destination: normal-topic
          group: normal-subscription
        normalProcess-out-0:
          destination: normal-topic
  pulsar:
    client:
      service-url: pulsar://localhost:6650
      # 或使用
      # service-url: pulsar+ssl://localhost:6651
```

### 3. 使用消息服务

参考 [richie-component-messaging README](../README.md) 了解如何使用 `MessageService` 发送和消费消息。

---

## ⚙️ 配置说明

### Apache Pulsar Binder 配置

| 配置项                                     | 说明            | 默认值                       |
|-----------------------------------------|---------------|---------------------------|
| `spring.pulsar.client.service-url`      | Pulsar 服务 URL | `pulsar://localhost:6650` |
| `spring.pulsar.client.authentication.*` | 认证配置          | -                         |
| `spring.pulsar.producer.*`              | 生产者配置         | -                         |
| `spring.pulsar.consumer.*`              | 消费者配置         | -                         |

### 详细配置

参考 [Spring Cloud Stream Pulsar Binder 文档](https://docs.spring.io/spring-cloud-stream-binder-pulsar/docs/current/reference/html/)

---

## 📖 参考文档

- [Spring Cloud Stream 文档](https://spring.io/projects/spring-cloud-stream#overview)
- [Spring Cloud Stream Pulsar Binder 文档](https://docs.spring.io/spring-cloud-stream-binder-pulsar/docs/current/reference/html/)
- [Apache Pulsar 官方文档](https://pulsar.apache.org/docs/)
- [richie-component-messaging README](../README.md)

---

## ✨ 特性

- ✅ 支持多租户
- ✅ 支持命名空间（Namespace）
- ✅ 支持 Topic 和 Subscription
- ✅ 支持消息保留和过期
- ✅ 高性能、低延迟

