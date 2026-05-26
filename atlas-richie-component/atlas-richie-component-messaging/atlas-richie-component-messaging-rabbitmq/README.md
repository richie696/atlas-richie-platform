# Richie Messaging RabbitMQ Component

RabbitMQ 消息队列依赖管理组件，基于 Spring Cloud Stream RabbitMQ Binder。

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
    <artifactId>atlas-richie-component-messaging-rabbitmq</artifactId>
</dependency>
```

### 2. 配置 RabbitMQ

```yaml
spring:
  cloud:
    stream:
      bindings:
        normalProcess-in-0:
          destination: normal-exchange
          group: normal-queue
        normalProcess-out-0:
          destination: normal-exchange
      rabbit:
        bindings:
          normalProcess-in-0:
            consumer:
              binding-routing-key: normal-routing-key
          normalProcess-out-0:
            producer:
              routing-key-expression: headers['routingKey']
        connectionFactory:
          host: localhost
          port: 5672
          username: guest
          password: guest
```

### 3. 使用消息服务

参考 [richie-component-messaging README](../README.md) 了解如何使用 `MessageService` 发送和消费消息。

---

## ⚙️ 配置说明

### RabbitMQ Binder 配置

| 配置项                                                                     | 说明          | 默认值         |
|-------------------------------------------------------------------------|-------------|-------------|
| `spring.cloud.stream.rabbit.bindings.*.consumer.binding-routing-key`    | 消费者路由键      | -           |
| `spring.cloud.stream.rabbit.bindings.*.producer.routing-key-expression` | 生产者路由键表达式   | -           |
| `spring.cloud.stream.rabbit.connectionFactory.host`                     | RabbitMQ 主机 | `localhost` |
| `spring.cloud.stream.rabbit.connectionFactory.port`                     | RabbitMQ 端口 | `5672`      |
| `spring.cloud.stream.rabbit.connectionFactory.username`                 | 用户名         | `guest`     |
| `spring.cloud.stream.rabbit.connectionFactory.password`                 | 密码          | `guest`     |

### 详细配置

参考 [Spring Cloud Stream RabbitMQ Binder 文档](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream-binder-rabbit.html)

---

## 📖 参考文档

- [Spring Cloud Stream 文档](https://spring.io/projects/spring-cloud-stream#overview)
- [Spring Cloud Stream RabbitMQ Binder 文档](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream-binder-rabbit.html)
- [RabbitMQ 官方文档](https://www.rabbitmq.com/documentation.html)
- [richie-component-messaging README](../README.md)

---

## ✨ 特性

- ✅ 支持 Exchange、Queue、Routing Key
- ✅ 支持消息确认（ACK）
- ✅ 支持死信队列（DLQ）
- ✅ 支持延迟消息
- ✅ 企业级消息队列
