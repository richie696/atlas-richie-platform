# Richie Messaging Solace PubSub+ Component

Solace PubSub+ 消息队列依赖管理组件，基于 Spring Cloud Stream Solace Binder。

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
    <artifactId>atlas-richie-component-messaging-solace</artifactId>
</dependency>
```

### 2. 配置 Solace PubSub+

```yaml
spring:
  cloud:
    stream:
      bindings:
        normalProcess-in-0:
          destination: normal-topic
          group: normal-queue
        normalProcess-out-0:
          destination: normal-topic
    solace:
      bindings:
        normalProcess-in-0:
          consumer:
            queue-name: normal-queue
      host: localhost
      msg-vpn: default
      client-username: default
      client-password: default
```

### 3. 使用消息服务

参考 [richie-component-messaging README](../README.md) 了解如何使用 `MessageService` 发送和消费消息。

---

## ⚙️ 配置说明

### Solace PubSub+ Binder 配置

| 配置项                                                  | 说明             | 默认值         |
|------------------------------------------------------|----------------|-------------|
| `spring.cloud.solace.host`                           | Solace 主机地址    | `localhost` |
| `spring.cloud.solace.msg-vpn`                        | Message VPN 名称 | `default`   |
| `spring.cloud.solace.client-username`                | 客户端用户名         | `default`   |
| `spring.cloud.solace.client-password`                | 客户端密码          | `default`   |
| `spring.cloud.solace.bindings.*.consumer.queue-name` | 消费者队列名称        | -           |

### 详细配置

参考 [Spring Cloud Stream Solace Binder 文档](https://docs.solace.com/Developer-Tools/Spring-Cloud-Stream/Spring-Cloud-Stream-Getting-Started.htm)

---

## 📖 参考文档

- [Spring Cloud Stream 文档](https://spring.io/projects/spring-cloud-stream#overview)
- [Spring Cloud Stream Solace Binder 文档](https://docs.solace.com/Developer-Tools/Spring-Cloud-Stream/Spring-Cloud-Stream-Getting-Started.htm)
- [Solace PubSub+ 官方文档](https://docs.solace.com/)
- [richie-component-messaging README](../README.md)

---

## ✨ 特性

- ✅ 支持 Topic 和 Queue
- ✅ 支持消息确认（ACK）
- ✅ 支持死信队列（DLQ）
- ✅ 支持消息优先级
- ✅ 企业级消息中间件
