# Richie Messaging RocketMQ Component

Apache RocketMQ 消息队列依赖管理组件，基于 Spring Cloud Stream RocketMQ Binder。

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
    <artifactId>atlas-richie-component-messaging-rocketmq</artifactId>
</dependency>
```

### 2. 配置 RocketMQ

```yaml
spring:
  cloud:
    stream:
      bindings:
        normalProcess-in-0:
          destination: normal-topic
          group: normal-group
        normalProcess-out-0:
          destination: normal-topic
      rocketmq:
        binder:
          name-server: localhost:9876
          access-key: your-access-key
          secret-key: your-secret-key
```

### 3. 使用消息服务

参考 [richie-component-messaging README](../README.md) 了解如何使用 `MessageService` 发送和消费消息。

---

## ⚙️ 配置说明

### RocketMQ Binder 配置

| 配置项                                               | 说明              | 默认值 |
|---------------------------------------------------|-----------------|-----|
| `spring.cloud.stream.rocketmq.binder.name-server` | Name Server 地址  | -   |
| `spring.cloud.stream.rocketmq.binder.access-key`  | Access Key（阿里云） | -   |
| `spring.cloud.stream.rocketmq.binder.secret-key`  | Secret Key（阿里云） | -   |

### 详细配置

参考 [Spring Cloud Alibaba RocketMQ Binder 文档](https://github.com/alibaba/spring-cloud-alibaba/wiki/RocketMQ-en)

---

## 📖 参考文档

- [Spring Cloud Stream 文档](https://spring.io/projects/spring-cloud-stream#overview)
- [Spring Cloud Alibaba RocketMQ 文档](https://github.com/alibaba/spring-cloud-alibaba/wiki/RocketMQ-en)
- [Apache RocketMQ 官方文档](https://rocketmq.apache.org/docs/)
- [richie-component-messaging README](../README.md)

---

## ✨ 特性

- ✅ 支持事务消息
- ✅ 支持顺序消息
- ✅ 支持延迟消息
- ✅ 支持批量消息
- ✅ 适合阿里云环境
