# Richie Messaging Kafka Component

Apache Kafka 消息队列依赖管理组件，基于 Spring Cloud Stream Kafka Binder。

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
    <artifactId>atlas-richie-component-messaging-kafka</artifactId>
</dependency>
```

### 2. 配置 Kafka

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
      kafka:
        binder:
          brokers: localhost:9092
          # 其他 Kafka 配置
          configuration:
            security:
              protocol: SASL_PLAINTEXT
            sasl:
              mechanism: PLAIN
```

### 3. 使用消息服务

参考 [richie-component-messaging README](../README.md) 了解如何使用 `MessageService` 发送和消费消息。

---

## ⚙️ 配置说明

### Kafka Binder 配置

| 配置项                                                | 说明                | 默认值              |
|----------------------------------------------------|-------------------|------------------|
| `spring.cloud.stream.kafka.binder.brokers`         | Kafka Broker 地址   | `localhost:9092` |
| `spring.cloud.stream.kafka.binder.zkNodes`         | Zookeeper 地址（旧版本） | -                |
| `spring.cloud.stream.kafka.binder.configuration.*` | Kafka 客户端配置       | -                |

### 详细配置

参考 [Spring Cloud Stream Kafka Binder 文档](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream-binder-kafka.html)

---

## 📖 参考文档

- [Spring Cloud Stream 文档](https://spring.io/projects/spring-cloud-stream#overview)
- [Spring Cloud Stream Kafka Binder 文档](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream-binder-kafka.html)
- [Apache Kafka 官方文档](https://kafka.apache.org/documentation/)
- [richie-component-messaging README](../README.md)

---

## ✨ 特性

- ✅ 支持 Kafka Streams
- ✅ 支持事务消息
- ✅ 支持消费者组
- ✅ 支持分区和副本
- ✅ 高性能、高吞吐量
