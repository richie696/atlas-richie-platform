# Richie Messaging AWS Kinesis Component

Amazon Kinesis 数据流依赖管理组件，基于 Spring Cloud Stream Kinesis Binder。

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
    <artifactId>atlas-richie-component-messaging-kinesis</artifactId>
</dependency>
```

### 2. 配置 AWS Kinesis

```yaml
spring:
  cloud:
    stream:
      bindings:
        normalProcess-in-0:
          destination: normal-stream
          group: normal-consumer-group
        normalProcess-out-0:
          destination: normal-stream
    aws:
      kinesis:
        region: us-east-1
        credentials:
          access-key: your-access-key
          secret-key: your-secret-key
```

### 3. 使用消息服务

参考 [richie-component-messaging README](../README.md) 了解如何使用 `MessageService` 发送和消费消息。

---

## ⚙️ 配置说明

### AWS Kinesis Binder 配置

| 配置项                                               | 说明                | 默认值 |
|---------------------------------------------------|-------------------|-----|
| `spring.cloud.aws.kinesis.region`                 | AWS 区域            | -   |
| `spring.cloud.aws.kinesis.credentials.access-key` | AWS Access Key    | -   |
| `spring.cloud.aws.kinesis.credentials.secret-key` | AWS Secret Key    | -   |
| `spring.cloud.aws.kinesis.stream-name`            | Kinesis Stream 名称 | -   |

### 详细配置

参考 [Spring Cloud Stream Kinesis Binder 文档](https://docs.spring.io/spring-cloud-stream-binder-aws-kinesis/docs/current/reference/html/)

---

## 📖 参考文档

- [Spring Cloud Stream 文档](https://spring.io/projects/spring-cloud-stream#overview)
- [Spring Cloud Stream Kinesis Binder 文档](https://docs.spring.io/spring-cloud-stream-binder-aws-kinesis/docs/current/reference/html/)
- [Amazon Kinesis 官方文档](https://docs.aws.amazon.com/kinesis/)
- [richie-component-messaging README](../README.md)

---

## ✨ 特性

- ✅ 支持 Kinesis Data Streams
- ✅ 支持分片（Shard）
- ✅ 支持消费者组
- ✅ 支持记录聚合
- ✅ 适合 AWS 云环境
