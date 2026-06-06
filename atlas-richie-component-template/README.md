# Richie Component Template

## 📖 概述

**Richie Component Template** 是Richie技术中台组件的示例工程集合，提供了各个组件的完整使用示例和最佳实践。这些示例工程可以帮助开发人员快速理解和使用各个组件的功能。

## 🎯 设计目的

- **学习参考**：提供完整的使用示例，帮助开发人员快速上手
- **最佳实践**：展示组件的推荐使用方式和配置方法
- **功能演示**：演示组件的核心功能和特性
- **测试验证**：可用于验证组件的功能和性能

## 📦 示例工程列表

### 核心基础设施示例

#### sample-cache
**缓存组件示例**，演示 Redis 缓存的各种使用场景。

- **功能演示**：
  - KV 存储操作
  - Hash、List、Set、ZSet 操作
  - 分布式锁使用
  - 缓存预热机制
  - 批量操作

- **运行方式**：
```bash
cd sample-cache
mvn spring-boot:run
```

#### sample-cache-stream-dlq
**Redis Stream 死信队列示例**，演示 Redis Stream 消息队列的死信队列处理。

- **功能演示**：
  - Redis Stream 消息消费
  - 死信队列处理
  - 消息重试机制
  - 失败消息处理

- **运行方式**：
```bash
cd sample-cache-stream-dlq
mvn spring-boot:run
```

#### sample-http
**HTTP 客户端组件示例**，演示 OkHttp 和 HttpClient5 的使用。

- **子模块**：
  - `sample-http-okhttp` - OkHttp 客户端示例
  - `sample-http-httpclient5` - HttpClient5 客户端示例

- **功能演示**：
  - 同步/异步请求
  - 文件上传/下载
  - SOAP/XML 请求
  - 请求拦截器

- **运行方式**：
```bash
cd sample-http/sample-http-okhttp
mvn spring-boot:run
```

#### sample-threadpool
**线程池组件示例**，演示动态线程池的使用。

- **功能演示**：
  - Dynamic-TP 动态线程池配置
  - 线程池监控
  - 自定义异步线程池
  - 线程池参数动态调整

- **运行方式**：
```bash
cd sample-threadpool
mvn spring-boot:run
```

#### sample-logging
**日志组件示例**，演示访问日志和生命周期回调的使用。

- **功能演示**：
  - 访问日志记录（@AccessLog 注解）
  - 日志生命周期回调函数
    - BeforeLogCallback：日志记录前回调
    - AfterLogCallback：日志记录后回调
    - BeforePersistCallback：持久化前回调（脱敏示例）
    - OnErrorCallback：异常处理回调
  - YAML 配置生命周期回调（Class 和 Bean 两种方式）
  - 日志持久化配置

- **运行方式**：
```bash
cd sample-logging
mvn spring-boot:run
```

- **API 示例**：
  - `GET /api/logging/info` - 普通查询接口
  - `POST /api/logging/create` - 创建操作（持久化）
  - `POST /api/logging/login` - 登录接口（触发脱敏回调）
  - `GET /api/logging/error?type=illegal` - 异常处理测试

### 消息队列示例

#### sample-messaging
**消息队列组件示例**，演示各种消息队列的使用。

- **子模块**：
  - `sample-messaging-core` - 核心消息处理逻辑
  - `sample-messaging-kafka` - Kafka 示例
  - `sample-messaging-rabbitmq` - RabbitMQ 示例
  - `sample-messaging-rocketmq` - RocketMQ 示例
  - `sample-messaging-pulsar` - Pulsar 示例
  - `sample-messaging-servicebus` - Azure Service Bus 示例

- **功能演示**：
  - 消息发送和接收
  - 消息幂等性处理
  - 消息重试机制
  - 延迟消息
  - 消息头传递

- **运行方式**：
```bash
# 启动 Kafka 示例
cd sample-messaging/sample-messaging-kafka
mvn spring-boot:run

# 启动 RabbitMQ 示例
cd sample-messaging/sample-messaging-rabbitmq
mvn spring-boot:run
```

### MQTT 示例

#### sample-mqtt-client
**MQTT 客户端示例**，演示 MQTT 客户端的连接和消息收发。

- **功能演示**：
  - MQTT 客户端连接
  - 消息发布和订阅
  - QoS 级别使用
  - 自动重连
  - 网络质量监控

- **运行方式**：
```bash
cd sample-mqtt-client
mvn spring-boot:run
```

#### sample-mqtt-server
**MQTT 服务器示例**，演示 MQTT 服务器的搭建和配置。

- **功能演示**：
  - MQTT 服务器配置
  - 客户端管理
  - 消息路由
  - 连接状态监控

- **运行方式**：
```bash
cd sample-mqtt-server
mvn spring-boot:run
```

### 存储示例

#### sample-storage
**对象存储组件示例**，演示各种对象存储的使用。

- **子模块**：
  - `sample-storage-local` - 本地存储示例
  - `sample-storage-s3` - AWS S3 示例
  - `sample-storage-oss` - 阿里云 OSS 示例
  - `sample-storage-cos` - 腾讯云 COS 示例
  - `sample-storage-obs` - 华为云 OBS 示例
  - `sample-storage-minio` - MinIO 示例
  - `sample-storage-ks3` - 金山云 KS3 示例
  - `sample-storage-tos` - 火山引擎 TOS 示例
  - `sample-storage-azure` - Azure Blob 示例
  - `sample-storage-sftp` - SFTP 示例
  - `sample-storage-smb` - SMB 示例

- **功能演示**：
  - 文件上传/下载
  - 文件存在性检查
  - 断点续传
  - 存储类型配置

- **运行方式**：
```bash
# 启动本地存储示例
cd sample-storage/sample-storage-local
mvn spring-boot:run

# 启动 OSS 示例
cd sample-storage/sample-storage-oss
mvn spring-boot:run
```

### 向量数据库示例

#### sample-vector
**向量数据库组件示例**，演示各种向量数据库的使用。

- **子模块**：
  - `sample-vector-core` - 核心向量处理逻辑
  - `sample-vector-redis` - Redis 向量数据库示例
  - `sample-vector-milvus` - Milvus 示例
  - `sample-vector-mongodb-atlas` - MongoDB Atlas 示例
  - `sample-vector-postgresql` - PostgreSQL (pgvector) 示例
  - `sample-vector-qdrant` - Qdrant 示例
  - `sample-vector-neo4j` - Neo4j 示例
  - `sample-vector-elasticsearch` - Elasticsearch 向量示例
  - `sample-vector-weaviate` - Weaviate 示例

- **功能演示**：
  - 文档向量化
  - 向量存储和检索
  - 相似度搜索
  - 多向量数据库切换

- **运行方式**：
```bash
# 启动 Redis 向量数据库示例
cd sample-vector/sample-vector-redis
mvn spring-boot:run

# 启动 Milvus 示例
cd sample-vector/sample-vector-milvus
mvn spring-boot:run
```

### AI 示例

#### sample-ai
**AI 组件示例**，演示统一 AI 模型调用的使用。

- **功能演示**：
  - 多模型配置（OpenAI、DeepSeek、智谱AI、Anthropic、Ollama）
  - 模型动态切换
  - 同步/异步调用
  - 流式响应

- **运行方式**：
```bash
cd sample-ai
mvn spring-boot:run
```

### MongoDB 示例

#### sample-mongodb
**MongoDB 组件示例**，演示 MongoDB 的使用。

- **功能演示**：
  - MongoDB 连接配置
  - 文档 CRUD 操作
  - 聚合查询
  - 索引管理

- **运行方式**：
```bash
cd sample-mongodb
mvn spring-boot:run
```

### 日志组件示例

#### sample-logging
**日志组件示例**，演示访问日志和生命周期回调的使用。

- **功能演示**：
  - 访问日志记录（@AccessLog 注解）
  - 日志生命周期回调函数
    - BeforeLogCallback：日志记录前回调
    - AfterLogCallback：日志记录后回调
    - BeforePersistCallback：持久化前回调（脱敏示例）
    - OnErrorCallback：异常处理回调
  - YAML 配置生命周期回调（Class 和 Bean 两种方式）
  - 日志持久化配置

- **运行方式**：
```bash
cd sample-logging
mvn spring-boot:run
```

- **API 示例**：
  - `GET /api/logging/info` - 普通查询接口
  - `POST /api/logging/create` - 创建操作（持久化）
  - `POST /api/logging/login` - 登录接口（触发脱敏回调）
  - `GET /api/logging/error?type=illegal` - 异常处理测试

## 🚀 快速开始

### 1. 环境要求

- JDK 25+ (支持 Oracle JDK、OpenJDK、GraalVM、Azul Zulu 等)
- Maven 3.9.0+
- Redis（部分示例需要）
- 相应的中间件（Kafka、RabbitMQ、Elasticsearch 等，根据示例需要）

### 2. 配置说明

每个示例工程都包含 `application.yml` 配置文件，需要根据实际情况修改：

- **Redis 配置**：修改 Redis 连接信息
- **消息队列配置**：修改消息队列连接信息
- **对象存储配置**：修改存储服务的认证信息
- **数据库配置**：修改数据库连接信息

### 3. 运行示例

```bash
# 进入示例目录
cd sample-cache

# 运行示例
mvn spring-boot:run

# 或使用 IDE 直接运行主类
```

### 4. 访问示例

大部分示例都提供了 REST API，可以通过以下方式访问：

- **Swagger UI**：`http://localhost:{port}/swagger-ui.html`
- **Actuator**：`http://localhost:{port}/actuator`
- **API 端点**：根据示例文档中的说明

## 📚 示例说明

### 示例结构

每个示例工程通常包含：

- **Controller**：REST API 控制器，提供 HTTP 接口
- **Service**：业务逻辑层，演示组件的使用
- **Configuration**：配置类，展示组件的配置方式
- **application.yml**：配置文件，包含组件的配置示例

### 代码示例

示例代码通常包含：

- **基础使用**：最简单的使用方式
- **高级特性**：组件的高级功能
- **最佳实践**：推荐的使用方式
- **错误处理**：异常处理和错误恢复

## 🔧 配置示例

### Redis 配置

```yaml
platform:
  component:
    cache:
      redis:
        host: localhost
        port: 6379
        password: 
        database: 0
```

### 消息队列配置

```yaml
platform:
  component:
    messaging:
      datasource: redis
      max-retries: 3
```

### 对象存储配置

```yaml
platform:
  component:
    storage:
      object:
        engine: ALIYUN_OSS
        endpoint: oss-cn-hangzhou.aliyuncs.com
        accessKeyId: your-key
        accessKeySecret: your-secret
        bucketName: my-bucket
```

## 📖 相关文档

- [Richie Component Platform](../richie-component/README.md) - 组件库文档
- [Richie Base Platform](../richie-base/README.md) - 基础包文档

## ⚠️ 注意事项

1. **配置信息**：示例中的配置信息需要根据实际情况修改
2. **依赖服务**：部分示例需要启动相应的中间件服务
3. **API 密钥**：使用云服务时需要配置正确的 API 密钥
4. **网络环境**：部分示例需要访问外网服务

## 🤝 贡献指南

欢迎贡献新的示例工程或改进现有示例！

1. **新增示例**：在 `richie-component-template` 下创建新的示例模块
2. **完善文档**：为示例添加详细的说明文档
3. **代码规范**：遵循项目的代码规范
4. **测试验证**：确保示例可以正常运行

## 🔗 相关链接

- [Richie技术中台](https://docs.richie696.cn/)
- [问题反馈](richie696@icloud.com)
- [功能建议](richie696@icloud.com)

---

**Richie Component Template** - 组件使用示例和最佳实践 🚀

