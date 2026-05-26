# 死信队列服务部署指南

## 概述

死信队列服务是一个独立的微服务，专门负责处理各种死信消息。建议将死信队列处理从主业务服务中分离出来，实现更好的架构解耦和运维管理。

## 架构优势

### 1. **职责分离**

- 主业务服务：专注于正常业务逻辑处理
- 死信队列服务：专门处理异常和死信消息

### 2. **资源隔离**

- 死信处理不会影响正常业务性能
- 可以独立调整死信服务的资源配置

### 3. **监控独立**

- 死信队列有独立的监控指标
- 便于运维人员监控和告警

### 4. **扩展灵活**

- 可以根据死信量独立扩缩容
- 支持多种死信处理策略

## 部署方式

### 方式一：独立JAR包部署

```bash
# 1. 构建死信队列服务
mvn clean package -DskipTests

# 2. 启动死信队列服务
java -jar dlq-service.jar --spring.profiles.active=dlq-service

# 3. 指定配置文件
java -jar dlq-service.jar --spring.config.location=classpath:/application-dlq-service.yml
```

### 方式二：Docker部署

```dockerfile
# Dockerfile
FROM openjdk:21-jre-slim
COPY dlq-service.jar /app/
WORKDIR /app
EXPOSE 8081
CMD ["java", "-jar", "dlq-service.jar", "--spring.profiles.active=dlq-service"]
```

```bash
# 构建和运行
docker build -t dlq-service .
docker run -d --name dlq-service -p 8081:8081 dlq-service
```

### 方式三：Kubernetes部署

```yaml
# dlq-service-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: dlq-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: dlq-service
  template:
    metadata:
      labels:
        app: dlq-service
    spec:
      containers:
      - name: dlq-service
        image: dlq-service:latest
        ports:
        - containerPort: 8081
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "dlq-service"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: dlq-service
spec:
  selector:
    app: dlq-service
  ports:
  - port: 8081
    targetPort: 8081
  type: ClusterIP
```

## 配置说明

### 1. **死信队列策略配置**

```yaml
platform:
  cache:
    redis:
      stream:
        consumers:
          configs:
            # 全局死信队列
            dlq-global:
              stream-key: "dlq:global"
              group: "dlq-global-processors"
              consumer: "dlq-global-consumer"
              auto-ack: true
              concurrency: 3
              error-strategy: skip
              auto-start: true
```

### 2. **监控配置**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

## 监控指标

### 1. **应用健康检查**

```bash
curl http://localhost:8081/dlq-service/actuator/health
```

### 2. **死信队列指标**

```bash
curl http://localhost:8081/dlq-service/actuator/metrics
```

### 3. **Prometheus监控**

```bash
curl http://localhost:8081/dlq-service/actuator/prometheus
```

## 运维建议

### 1. **资源规划**

- **CPU**: 建议 0.5-1 核
- **内存**: 建议 512MB-1GB
- **磁盘**: 建议 10GB（用于日志存储）

### 2. **监控告警**

- 死信消息处理延迟
- 死信消息堆积数量
- 服务健康状态
- 错误率监控

### 3. **日志管理**

- 死信消息详细日志
- 处理结果日志
- 错误堆栈信息

### 4. **扩容策略**

- 根据死信消息量调整副本数
- 根据处理延迟调整并发数
- 根据错误率调整重试策略

## 故障处理

### 1. **死信消息堆积**

- 检查死信处理逻辑是否有问题
- 增加消费者并发数
- 检查Redis连接是否正常

### 2. **处理失败**

- 查看死信消息详细日志
- 检查业务规则是否正确
- 考虑人工介入处理

### 3. **服务不可用**

- 检查服务健康状态
- 查看应用日志
- 检查资源配置是否充足

## 最佳实践

1. **独立部署**: 死信队列服务应该独立部署，不要与主业务服务混合
2. **监控完善**: 建立完善的监控和告警机制
3. **日志详细**: 记录详细的死信处理日志，便于问题排查
4. **自动恢复**: 实现自动重试和恢复机制
5. **人工介入**: 对于无法自动处理的死信，提供人工处理接口
