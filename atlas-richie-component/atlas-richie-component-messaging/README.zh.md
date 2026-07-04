# Atlas Richie Messaging组件 (atlas-richie-component-messaging)

基于 Spring Cloud Stream 的统一消息队列组件，提供抽象的消息发送和消费接口，支持多种消息队列（Kafka、RabbitMQ、RocketMQ、Kinesis、Pub/Sub 等）。

## 📖 目录

- [✨ 功能特性](#✨-功能特性)
  - [核心能力](#核心能力)
  - [高级特性](#高级特性)
- [🚀 快速开始](#🚀-快速开始)
  - [1. 添加依赖](#1-添加依赖)
  - [2. 配置消息队列](#2-配置消息队列)
  - [3. 发送消息](#3-发送消息)
  - [4. 消费消息](#4-消费消息)
- [🔧 核心功能](#🔧-核心功能)
  - [1. 消息发送](#1-消息发送)
  - [2. 消息消费](#2-消息消费)
  - [3. 幂等去重](#3-幂等去重)
  - [4. 消息重试](#4-消息重试)
  - [5. 请求头传递](#5-请求头传递)
- [📎 📦 支持的 MQ 类型](#📎-📦-支持的-mq-类型)
- [⚙️ 配置说明](#⚙️-配置说明)
  - [基础配置](#基础配置)
  - [Spring Cloud Stream 配置](#spring-cloud-stream-配置)
- [🎯 最佳实践](#🎯-最佳实践)
  - [1. Topic 别名管理](#1-topic-别名管理)
  - [2. 消息事件定义](#2-消息事件定义)
  - [3. 消费者注册](#3-消费者注册)
  - [4. 错误处理](#4-错误处理)
  - [5. 幂等去重](#5-幂等去重)
  - [6. 多 Binder 场景](#6-多-binder-场景)
- [❓ 常见问题](#❓-常见问题)
  - [Q1: 如何选择 MQ 类型？](#q1-如何选择-mq-类型？)
  - [Q2: 消息处理失败会怎样？](#q2-消息处理失败会怎样？)
  - [Q3: 如何实现消息幂等？](#q3-如何实现消息幂等？)
  - [Q4: 延迟消息如何实现？](#q4-延迟消息如何实现？)
  - [Q5: 如何传递自定义请求头？](#q5-如何传递自定义请求头？)
  - [Q6: 消息消费是同步还是异步？](#q6-消息消费是同步还是异步？)
- [📎 📝 总结](#📎-📝-总结)
---

## ✨ 功能特性

### 核心能力

- ✅ **统一消息接口**：提供 `MessageService` 统一接口，屏蔽底层 MQ 差异
- ✅ **多 MQ 支持**：支持 Kafka、RabbitMQ、RocketMQ、Kinesis、GCP Pub/Sub、Azure Event Hubs、AWS SQS/SNS 等
- ✅ **消息消费**：基于 Spring Cloud Stream Function 的消费模式
- ✅ **延迟消息**：支持延迟消息发送
- ✅ **幂等去重**：支持消息幂等去重（基于 Memory 或 Redis）
- ✅ **消息重试**：支持消息处理失败后的自动重试
- ✅ **多 Binder 支持**：支持同时使用多个 MQ 服务

### 高级特性

- ✅ **消息事件封装**：`MessageEvent` 统一封装消息内容
- ✅ **请求头传递**：自动传递请求头信息（语言、时区、租户等）
- ✅ **消息冻结机制**：防止消息在处理过程中被修改
- ✅ **类型安全**：支持类型安全的消息序列化/反序列化

---

## 🚀 快速开始

### 1) 添加依赖

```xml
<!-- 基础消息组件 -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-messaging-core</artifactId>
</dependency>

<!-- 选择具体的 MQ 实现（以 Kafka 为例） -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-messaging-kafka</artifactId>
</dependency>

<!-- 如果需要使用 Redis 进行幂等去重 -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>
```

### 2) 配置消息队列

```yaml
# application.yml
spring:
  cloud:
    stream:
      # 幂等去重使用的数据缓存源（memory 或 redis）
      datasource: memory
      # 消息处理失败后的最大重试次数
      max-retries: 3
      # Kafka 配置（以 Kafka 为例）
      bindings:
        normalProcess-in-0:
          destination: normal-topic
          group: normal-group
        normalProcess-out-0:
          destination: normal-topic
        delayProcess-in-0:
          destination: delay-topic
          group: delay-group
        delayProcess-out-0:
          destination: delay-topic
      kafka:
        binder:
          brokers: localhost:9092
```

### 3) 发送消息

```java
import service.com.richie.component.messaging.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    
    @Autowired
    private MessageService messageService;
    
    /**
     * 发送普通消息
     */
    public void sendOrderCreated(OrderCreatedEvent event) {
        messageService.sendMessage("order-created", event);
    }
    
    /**
     * 发送延迟消息（5秒后）
     */
    public void sendDelayedNotification(NotificationEvent event) {
        messageService.sendDelayMessage("notification", event, 5000L);
    }
    
    /**
     * 多 Binder 场景：指定 Binder 名称
     */
    public void sendToSpecificBinder(Event event) {
        messageService.sendMessage("topic-alias", "binder-name", event);
    }
}
```

### 4) 消费消息

```java
import consumer.com.richie.component.messaging.BaseConsumer;
import event.com.richie.component.messaging.MessageEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.function.Function;

@Component
public class OrderConsumer {
    
    @Autowired
    private BaseConsumer baseConsumer;
    
    @PostConstruct
    public void registerConsumer() {
        // 注册消费者回调函数
        baseConsumer.registerConsumer("order-created", this::handleOrderCreated);
    }
    
    /**
     * 处理订单创建消息
     * 
     * @param event 消息事件
     * @return true：处理成功，false：处理失败（将触发重试）
     */
    private Boolean handleOrderCreated(MessageEvent event) {
        try {
            // 获取消息体
            OrderCreatedEvent orderEvent = event.getBody(OrderCreatedEvent.class);
            
            // 处理业务逻辑
            orderService.processOrder(orderEvent);
            
            return true;  // 处理成功
        } catch (Exception e) {
            log.error("处理订单创建消息失败", e);
            return false;  // 处理失败，将触发重试
        }
    }
}
```

---

## 🔧 核心功能

### 1) 消息发送

#### 普通消息发送

```java
// 方式一：使用 Topic 别名
messageService.sendMessage("order-created", orderEvent);

// 方式二：指定 Binder 名称（多 MQ 场景）
messageService.sendMessage("order-created", "kafka-binder", orderEvent);

// 方式三：自定义 Content-Type
messageService.sendMessage(
    "order-created",
    orderEvent,
    MimeTypeUtils.APPLICATION_JSON
);
```

#### 延迟消息发送

```java
// 延迟 5 秒
messageService.sendDelayMessage("notification", notificationEvent, 5000L);

// 延迟消息 + 指定 Binder
messageService.sendDelayMessage(
    "notification",
    "rabbitmq-binder",
    notificationEvent,
    5000L
);
```

### 2) 消息消费

#### 注册消费者

```java
@PostConstruct
public void registerConsumers() {
    // 注册普通消息消费者
    baseConsumer.registerConsumer("order-created", this::handleOrderCreated);
    baseConsumer.registerConsumer("order-cancelled", this::handleOrderCancelled);
}
```

#### 消息处理函数

```java
private Boolean handleOrderCreated(MessageEvent event) {
    try {
        // 获取消息体
        OrderCreatedEvent orderEvent = event.getBody(OrderCreatedEvent.class);
        
        // 处理业务逻辑
        processOrder(orderEvent);
        
        return true;  // 处理成功
    } catch (Exception e) {
        log.error("处理消息失败", e);
        return false;  // 处理失败，触发重试
    }
}
```

#### 消息事件属性

```java
// 获取消息ID
String messageId = event.getMessageId();

// 获取主题别名
String topic = event.getTopic();

// 获取消息体（简单对象）
OrderEvent orderEvent = event.getBody(OrderEvent.class);

// 获取消息体（复杂对象，使用 TypeReference）
List<OrderEvent> orders = event.getBody(new TypeReference<List<OrderEvent>>(){});

// 获取发送时间
long sendTime = event.getSendTime();

// 获取接收时间
long receiveTime = event.getReceiveTime();

// 获取延迟时间
long delayTime = event.getDelayTime();

// 获取重试次数
int retryCount = event.getRetryCount();

// 判断是否是延迟消息
boolean isDelay = event.isDelay();
```

### 3) 幂等去重

组件支持消息幂等去重，防止重复处理：

```yaml
spring:
  cloud:
    stream:
      # 使用 Redis 进行幂等去重（推荐生产环境）
      datasource: redis
      # 或使用内存进行幂等去重（仅单实例场景）
      # datasource: memory
```

**工作原理**：
- 消息处理前检查是否已处理过（基于消息ID）
- 如果已处理，直接跳过
- 如果未处理，保存处理记录（2分钟过期）
- 处理成功后，保留处理记录

### 4) 消息重试

组件支持消息处理失败后的自动重试：

```yaml
spring:
  cloud:
    stream:
      max-retries: 3  # 最大重试次数
```

**重试机制**：
- 消息处理函数返回 `false` 时触发重试
- 达到最大重试次数后，消息将被丢弃
- 重试次数记录在 `MessageEvent.retryCount` 中

### 5) 请求头传递

组件自动传递以下请求头信息：

- `X-Time-Format-Pattern`：时间格式
- `X-Currency-Format-Pattern`：货币格式
- `X-Rd-Request-Timezone`：时区
- `X-Rd-Request-Language`：语言
- `X-Rd-Request-Shop-Code`：店铺代码
- `X-Tenant-Code-Token`：租户代码

这些请求头会在消息消费时自动设置到 `HeaderContextHolder` 中。

---

## 📎 📦 支持的 MQ 类型

组件支持以下消息队列（需要添加对应的依赖）：

| MQ 类型                 | 依赖                                      | 说明              |
|-----------------------|-----------------------------------------|-----------------|
| **Apache Kafka**      | `richie-component-messaging-kafka`      | 高性能分布式消息队列      |
| **RabbitMQ**          | `richie-component-messaging-rabbitmq`   | 企业级消息队列         |
| **Apache RocketMQ**   | `richie-component-messaging-rocketmq`   | 阿里云 RocketMQ    |
| **Amazon Kinesis**    | `richie-component-messaging-kinesis`    | AWS Kinesis 数据流 |
| **Google Pub/Sub**    | `richie-component-messaging-gcp-pubsub` | GCP 发布订阅        |
| **Azure Event Hubs**  | `richie-component-messaging-eventhubs`  | Azure 事件中心      |
| **Azure Service Bus** | `richie-component-messaging-servicebus` | Azure 服务总线      |
| **AWS SQS**           | `richie-component-messaging-sqs`        | AWS 简单队列服务      |
| **AWS SNS**           | `richie-component-messaging-sns`        | AWS 简单通知服务      |
| **Apache Pulsar**     | `richie-component-messaging-pulsar`     | Apache Pulsar   |
| **Solace PubSub+**    | `richie-component-messaging-solace`     | Solace 发布订阅     |

📖 **各 MQ 的具体配置请参考对应的子组件 README**

---

## ⚙️ 配置说明

### 基础配置

| 配置项                               | 类型      | 默认值      | 说明                            |
|-----------------------------------|---------|----------|-------------------------------|
| `spring.cloud.stream.datasource`  | String  | `memory` | 幂等去重使用的数据缓存源：`memory`、`redis` |
| `spring.cloud.stream.max-retries` | Integer | `3`      | 消息处理失败后的最大重试次数                |

### `Spring` `Cloud` `Stream` 配置

Spring Cloud Stream 的配置取决于具体的 MQ 实现。以 Kafka 为例：

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
```

📖 **详细配置请参考**：[Spring Cloud Stream 文档](https://spring.io/projects/spring-cloud-stream#overview)

---

## 🎯 最佳实践

### 1) `Topic` 别名管理

使用枚举管理 Topic 别名，避免硬编码：

```java
public enum TopicAlias {
    ORDER_CREATED("order-created"),
    ORDER_CANCELLED("order-cancelled"),
    NOTIFICATION("notification");
    
    private final String alias;
    
    TopicAlias(String alias) {
        this.alias = alias;
    }
    
    public String getAlias() {
        return alias;
    }
}

// 使用
messageService.sendMessage(TopicAlias.ORDER_CREATED.getAlias(), event);
```

### 2) 消息事件定义

定义清晰的消息事件类：

```java
@Data
public class OrderCreatedEvent implements Serializable {
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private LocalDateTime createTime;
}
```

### 3) 消费者注册

在 `@PostConstruct` 方法中注册所有消费者：

```java
@Component
public class OrderConsumer {
    
    @Autowired
    private BaseConsumer baseConsumer;
    
    @PostConstruct
    public void registerConsumers() {
        baseConsumer.registerConsumer(TopicAlias.ORDER_CREATED.getAlias(), this::handleOrderCreated);
        baseConsumer.registerConsumer(TopicAlias.ORDER_CANCELLED.getAlias(), this::handleOrderCancelled);
    }
    
    private Boolean handleOrderCreated(MessageEvent event) {
        // ...
    }
}
```

### 4) 错误处理

完善的错误处理和日志记录：

```java
private Boolean handleOrderCreated(MessageEvent event) {
    try {
        OrderCreatedEvent orderEvent = event.getBody(OrderCreatedEvent.class);
        
        // 参数校验
        if (orderEvent == null || orderEvent.getOrderId() == null) {
            log.warn("消息体无效，跳过处理: {}", event);
            return true;  // 无效消息，不重试
        }
        
        // 业务处理
        orderService.processOrder(orderEvent);
        
        return true;
    } catch (BusinessException e) {
        // 业务异常，不重试
        log.error("业务处理失败，不重试: {}", event, e);
        return true;
    } catch (Exception e) {
        // 系统异常，触发重试
        log.error("系统异常，将触发重试: {}", event, e);
        return false;
    }
}
```

### 5) 幂等去重

生产环境使用 Redis 进行幂等去重：

```yaml
spring:
  cloud:
    stream:
      datasource: redis  # 使用 Redis 进行幂等去重
```

### 6) 多 `Binder` 场景

当需要同时使用多个 MQ 时，指定 Binder 名称：

```yaml
spring:
  cloud:
    stream:
      binders:
        kafka-binder:
          type: kafka
          environment:
            spring:
              kafka:
                bootstrap-servers: localhost:9092
        rabbitmq-binder:
          type: rabbit
          environment:
            spring:
              rabbitmq:
                host: localhost
                port: 5672
```

```java
// 发送到 Kafka
messageService.sendMessage("topic", "kafka-binder", event);

// 发送到 RabbitMQ
messageService.sendMessage("topic", "rabbitmq-binder", event);
```

---

## ❓ 常见问题

### `Q1` — 如何选择 `MQ` 类型？

**A:** 
- **Kafka**：适合高吞吐量、日志收集、流处理场景
- **RabbitMQ**：适合企业级应用、复杂路由场景
- **RocketMQ**：适合阿里云环境、事务消息场景
- **AWS SQS/SNS**：适合 AWS 环境
- **Azure Service Bus**：适合 Azure 环境

### `Q2` — 消息处理失败会怎样？

**A:** 
- 消息处理函数返回 `false` 时，会触发重试
- 达到最大重试次数后，消息将被丢弃
- 建议在消息处理函数中记录失败日志，便于排查问题

### `Q3` — 如何实现消息幂等？

**A:** 
- 组件已内置幂等去重机制（基于消息ID）
- 生产环境建议使用 Redis 进行幂等去重
- 业务层面也可以实现额外的幂等逻辑

### `Q4` — 延迟消息如何实现？

**A:** 
- 使用 `sendDelayMessage` 方法发送延迟消息
- 延迟时间以毫秒为单位
- 具体实现取决于 MQ 类型（部分 MQ 可能不支持延迟消息）

### `Q5` — 如何传递自定义请求头？

**A:** 
- 组件自动传递标准请求头
- 如需传递自定义请求头，需要在发送消息前设置到 `HeaderContextHolder` 中

### `Q6` — 消息消费是同步还是异步？

**A:** 
- Spring Cloud Stream 默认使用异步消费
- 消息处理函数在独立的线程中执行
- 可以通过配置调整并发数

---

## 📎 📝 总结

Richie Messaging Component 提供了统一的消息队列抽象，支持多种 MQ 实现，简化了消息发送和消费的开发工作。通过合理使用组件提供的功能，可以构建可靠、高性能的消息驱动应用。

**关键要点**：

1. **统一接口**：使用 `MessageService` 统一接口，屏蔽底层 MQ 差异
2. **类型安全**：使用枚举管理 Topic 别名，定义清晰的消息事件类
3. **错误处理**：完善的错误处理和日志记录，区分业务异常和系统异常
4. **幂等去重**：生产环境使用 Redis 进行幂等去重
5. **多 Binder**：支持同时使用多个 MQ 服务

📖 **各 MQ 的具体配置和特性请参考对应的子组件 README**
