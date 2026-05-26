# Richie Messaging AWS SNS Component

Amazon SNS（Simple Notification Service）消息队列依赖管理组件，基于 Spring Cloud Stream SNS Binder。

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
    <artifactId>atlas-richie-component-messaging-sns</artifactId>
</dependency>
```

### 2. 配置 AWS SNS

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
    aws:
      sns:
        region: us-east-1
        credentials:
          access-key: your-access-key
          secret-key: your-secret-key
```

### 3. 使用消息服务

参考 [richie-component-messaging README](../README.md) 了解如何使用 `MessageService` 发送和消费消息。

---

## ⚙️ 配置说明

### AWS SNS Binder 配置

| 配置项                                           | 说明             | 默认值 |
|-----------------------------------------------|----------------|-----|
| `spring.cloud.aws.sns.region`                 | AWS 区域         | -   |
| `spring.cloud.aws.sns.credentials.access-key` | AWS Access Key | -   |
| `spring.cloud.aws.sns.credentials.secret-key` | AWS Secret Key | -   |
| `spring.cloud.aws.sns.topic-name`             | SNS Topic 名称   | -   |

### 详细配置

参考 [Spring Cloud Stream SNS Binder 文档](https://github.com/idealo/spring-cloud-stream-binder-aws-sns)

---

## 📖 参考文档

- [Spring Cloud Stream 文档](https://spring.io/projects/spring-cloud-stream#overview)
- [Spring Cloud Stream SNS Binder 文档](https://github.com/idealo/spring-cloud-stream-binder-aws-sns)
- [Amazon SNS 官方文档](https://docs.aws.amazon.com/sns/)
- [richie-component-messaging README](../README.md)

---

## ✨ 特性

- ✅ 支持 Topic 和 Subscription
- ✅ 支持消息过滤
- ✅ 支持多种协议（HTTP、HTTPS、Email、SMS 等）
- ✅ 支持消息属性
- ✅ 适合 AWS 云环境
