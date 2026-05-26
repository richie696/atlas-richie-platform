# Richie Messaging AWS SQS Component

Amazon SQS（Simple Queue Service）消息队列依赖管理组件，基于 Spring Cloud Stream SQS Binder。

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
    <artifactId>atlas-richie-component-messaging-sqs</artifactId>
</dependency>
```

### 2. 配置 AWS SQS

```yaml
spring:
  cloud:
    stream:
      bindings:
        normalProcess-in-0:
          destination: normal-queue
          group: normal-consumer-group
        normalProcess-out-0:
          destination: normal-queue
    aws:
      sqs:
        region: us-east-1
        credentials:
          access-key: your-access-key
          secret-key: your-secret-key
```

### 3. 使用消息服务

参考 [richie-component-messaging README](../README.md) 了解如何使用 `MessageService` 发送和消费消息。

---

## ⚙️ 配置说明

### AWS SQS Binder 配置

| 配置项                                           | 说明             | 默认值 |
|-----------------------------------------------|----------------|-----|
| `spring.cloud.aws.sqs.region`                 | AWS 区域         | -   |
| `spring.cloud.aws.sqs.credentials.access-key` | AWS Access Key | -   |
| `spring.cloud.aws.sqs.credentials.secret-key` | AWS Secret Key | -   |
| `spring.cloud.aws.sqs.queue-name`             | SQS Queue 名称   | -   |

### 详细配置

参考 [Spring Cloud Stream SQS Binder 文档](https://github.com/idealo/spring-cloud-stream-binder-aws-sqs)

---

## 📖 参考文档

- [Spring Cloud Stream 文档](https://spring.io/projects/spring-cloud-stream#overview)
- [Spring Cloud Stream SQS Binder 文档](https://github.com/idealo/spring-cloud-stream-binder-aws-sqs)
- [Amazon SQS 官方文档](https://docs.aws.amazon.com/sqs/)
- [richie-component-messaging README](../README.md)

---

## ✨ 特性

- ✅ 支持标准队列和 FIFO 队列
- ✅ 支持消息可见性超时
- ✅ 支持死信队列（DLQ）
- ✅ 支持长轮询
- ✅ 适合 AWS 云环境
