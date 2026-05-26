# Richie Messaging GCP Pub/Sub Component

Google Cloud Pub/Sub 消息队列依赖管理组件，基于 Spring Cloud GCP Pub/Sub Stream Binder。

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
    <artifactId>atlas-richie-component-messaging-gcp-pubsub</artifactId>
</dependency>
```

### 2. 配置 GCP Pub/Sub

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
    gcp:
      pubsub:
        project-id: your-project-id
        credentials:
          location: classpath:gcp-credentials.json
```

### 3. 使用消息服务

参考 [richie-component-messaging README](../README.md) 了解如何使用 `MessageService` 发送和消费消息。

---

## ⚙️ 配置说明

### GCP Pub/Sub Binder 配置

| 配置项                                               | 说明           | 默认值 |
|---------------------------------------------------|--------------|-----|
| `spring.cloud.gcp.pubsub.project-id`              | GCP 项目ID     | -   |
| `spring.cloud.gcp.pubsub.credentials.location`    | 凭证文件路径       | -   |
| `spring.cloud.gcp.pubsub.credentials.encoded-key` | Base64 编码的凭证 | -   |

### 详细配置

参考 [Spring Cloud GCP Pub/Sub 文档](https://googlecloudplatform.github.io/spring-cloud-gcp/reference/html/index.html#pubsub)

---

## 📖 参考文档

- [Spring Cloud Stream 文档](https://spring.io/projects/spring-cloud-stream#overview)
- [Spring Cloud GCP Pub/Sub 文档](https://googlecloudplatform.github.io/spring-cloud-gcp/reference/html/index.html#pubsub)
- [Google Cloud Pub/Sub 官方文档](https://cloud.google.com/pubsub/docs)
- [richie-component-messaging README](../README.md)

---

## ✨ 特性

- ✅ 支持 Topic 和 Subscription
- ✅ 支持消息确认（ACK）
- ✅ 支持死信主题（Dead Letter Topic）
- ✅ 支持消息过滤
- ✅ 适合 GCP 云环境
