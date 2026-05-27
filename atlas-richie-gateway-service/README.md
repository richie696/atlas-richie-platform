# Richie Gateway Service

## 📖 概述

**Richie Gateway Service** 是Richie技术中台的通用 API 网关服务，基于 Spring Cloud Gateway 构建，提供统一的鉴权认证、请求路由、限流熔断、安全防护等能力。网关采用配置化设计，通过配置文件即可适配不同项目的需求，实现一个网关服务适用于所有项目。

## 📋 目录

- [概述](#-概述)
- [详细文档](#-详细文档)
- [核心功能](#-核心功能)
  - [国际化支持](#6-国际化支持)
    - [支持的语言列表](#支持的语言列表)
  - [全局异常处理](#7-全局异常处理)
    - [异常处理机制](#异常处理机制)
    - [错误排查方法](#错误排查方法)
    - [支持的HTTP状态码](#支持的http状态码)
- [架构设计](#-架构设计)
- [快速开始](#-快速开始)
- [配置说明](#-配置说明)
- [高级功能](#-高级功能)
- [版本历史](#-版本历史)
- [监控和日志](#-监控和日志)
- [开发指南](#-开发指南)

## 📚 详细文档

> 💡 **提示**：本文档提供快速入门和基础配置说明。如需深入了解网关的详细设计、架构原理、高级配置和最佳实践，请查看以下详细文档：

- 📖 **[网关设计文档](./docs/网关设计文档.md)** - 完整的设计文档，包含：
  - 快速了解（核心特性、技术栈）
  - 部署架构（ECS 部署、K8S 部署）
  - 过滤器架构（过滤器链、执行顺序）
  - 核心功能详解（ECC 加密、防重复提交、认证授权、多租户、灰度发布、熔断限流、国际化）
  - 配置指南（详细配置说明和示例）
  - 客户端集成（各语言客户端 SDK 和示例）
  - JVM 优化配置
  - 附录（常见问题、故障排查）

- 📊 **[网关熔断器架构图](./docs/网关熔断器架构图.md)** - Sentinel 限流熔断架构详细设计图（包含流量控制、熔断、降级、Fallback 处理链）


## 🎯 核心功能

### 1. 统一鉴权认证

- ✅ **JWT Token 认证**：基于 JWT 的令牌认证机制
- ✅ **Token 自动续期**：在到期前指定时间段内自动续期
- ✅ **Token 黑名单**：支持 Token 黑名单管理
- ✅ **SSO 单点登录**：支持单点登录和重复登录检测
- ✅ **接口授权**：支持接口级别的授权验证

### 2. 请求路由和转发

- ✅ **动态路由**：基于 Nacos 服务发现的动态路由
- ✅ **负载均衡**：集成 Spring Cloud LoadBalancer
- ✅ **路由规则**：灵活的路径匹配和路由规则
- ✅ **灰度发布**：支持金丝雀发布和灰度路由

### 3. 限流、熔断和降级

- ✅ **Sentinel 集成**：基于 Alibaba Sentinel 的限流和熔断
- ✅ **流控规则**：支持 QPS、线程数等流控规则
- ✅ **降级规则**：支持慢调用比例、异常比例降级
- ✅ **规则动态配置**：基于 Nacos 的规则动态配置

### 4. 安全防护

- ✅ **API 安全过滤**：防止恶意请求和攻击
- ✅ **IP 封禁**：支持 IP 封禁和永久封禁
- ✅ **防重复提交**：基于 Redis 的防重复提交机制
- ✅ **ECC+AES-GCM 加密**：支持端到端加密通信

### 5. 多租户支持

- ✅ **租户隔离**：基于 Token 的租户信息验证
- ✅ **租户过期检查**：自动检查租户是否过期
- ✅ **租户数据隔离**：确保租户数据安全

### 6. 国际化支持

- ✅ **多语言支持**：支持 35 种语言的错误提示和消息
- ✅ **国际化同步**：与业务系统国际化同步
- ✅ **自动语言识别**：根据请求头 `Accept-Language` 或 `X-RD-Request-Language` 自动选择语言
- ✅ **环境适配**：开发/测试环境返回详细错误信息，生产环境返回封装后的错误信息（含错误ID）

#### 支持的语言列表

**亚洲语言（12种）**：
- 中文简体 (zh_CN)
- 中文繁体 (zh_TW)
- 日语 (ja_JP)
- 韩语 (ko_KR)
- 泰语 (th_TH)
- 越南语 (vi_VN)
- 印尼语 (id_ID)
- 马来语 (ms_MY)
- 印地语 (hi_IN)
- 阿拉伯语 (ar_SA)
- 乌尔都语 (ur_PK)
- 孟加拉语 (bn_BD)

**欧洲语言（19种）**：
- 英语 (en_US)
- 德语 (de_DE)
- 法语 (fr_FR)
- 意大利语 (it_IT)
- 西班牙语（西班牙）(es_ES)
- 葡萄牙语（葡萄牙）(pt_PT)
- 俄语 (ru_RU)
- 荷兰语 (nl_NL)
- 波兰语 (pl_PL)
- 土耳其语 (tr_TR)
- 瑞典语 (sv_SE)
- 挪威语 (nb_NO)
- 丹麦语 (da_DK)
- 芬兰语 (fi_FI)
- 捷克语 (cs_CZ)
- 匈牙利语 (hu_HU)
- 罗马尼亚语 (ro_RO)
- 希腊语 (el_GR)
- 乌克兰语 (uk_UA)

**美洲语言（2种）**：
- 西班牙语（墨西哥）(es_MX)
- 葡萄牙语（巴西）(pt_BR)

**中东语言（2种）**：
- 希伯来语 (he_IL)
- 波斯语 (fa_IR)

**总计：35 种语言**

### 7. 全局异常处理

- ✅ **统一异常处理**：捕获所有下游服务的异常，统一处理和响应
- ✅ **环境适配**：开发/测试环境返回详细错误信息，生产环境返回封装后的错误信息
- ✅ **错误ID追踪**：生产环境自动生成唯一错误ID，便于问题排查
- ✅ **国际化支持**：错误消息支持多语言，根据请求头自动选择语言
- ✅ **详细日志记录**：生产环境记录完整错误信息到日志（包含错误ID、堆栈跟踪等）

#### 异常处理机制

网关通过 `GlobalErrorWebExceptionHandler` 统一捕获所有异常，并根据环境返回不同的错误信息：

**开发/测试环境**：
- 返回详细的异常信息，包括：
  - 错误信息
  - 异常类型
  - 完整堆栈跟踪
- 便于开发调试和问题定位

**生产环境**：
- 返回封装后的通用错误信息，包含唯一错误ID
- 格式：`{错误消息}。错误ID: {16位错误ID}`
- 示例：
  - 中文：`系统内部错误，请联系管理员。错误ID: a1b2c3d4e5f6g7h8`
  - 英文：`Internal system error, please contact administrator. Error ID: a1b2c3d4e5f6g7h8`

#### 错误排查方法

**步骤1：获取错误ID**
- 从错误响应中提取错误ID（16位十六进制字符串）
- 错误响应格式：
```json
{
  "code": "500",
  "msg": "系统内部错误，请联系管理员。错误ID: a1b2c3d4e5f6g7h8"
}
```

**步骤2：搜索日志**
- 在网关日志中搜索错误ID：`错误ID: a1b2c3d4e5f6g7h8`
- 日志包含以下信息：
  - 错误ID
  - HTTP状态码
  - 请求路径
  - 错误信息
  - 异常类型
  - 异常类
  - 完整堆栈跟踪

**步骤3：分析问题**
- 根据日志中的堆栈跟踪定位具体错误位置
- 检查请求路径和参数
- 分析异常类型和错误信息

**日志示例**：
```
ERROR Gateway错误详情 - 错误ID: a1b2c3d4e5f6g7h8
HTTP状态码: 500
请求路径: /api/users
错误信息: NullPointerException
异常类型: java.lang.NullPointerException
异常类: java.lang.NullPointerException
堆栈跟踪:
	at com.example.UserService.getUser(UserService.java:123)
	at com.example.UserController.getUser(UserController.java:45)
	...
```

#### 支持的HTTP状态码

网关支持以下HTTP状态码的错误处理策略：

| HTTP状态码 | 错误消息Key | 说明 |
|-----------|------------|------|
| 400 | ERROR_BAD_REQUEST | 请求参数错误 |
| 401 | ERROR_UNAUTHORIZED | 未授权，请先登录 |
| 403 | ERROR_FORBIDDEN | 访问被拒绝 |
| 404 | ERROR_NOT_FOUND | 资源未找到 |
| 405 | ERROR_METHOD_NOT_ALLOWED | 请求方法不允许 |
| 500 | ERROR_INTERNAL_SERVER | 服务器内部错误 |
| 502 | ERROR_BAD_GATEWAY | 网关错误 |
| 503 | ERROR_SERVICE_UNAVAILABLE | 服务不可用 |
| 504 | ERROR_GATEWAY_TIMEOUT | 网关超时 |
| 其他 | ERROR_INTERNAL | 系统内部错误 |

### 8. 其他功能

- ✅ **CORS 跨域**：支持跨域请求配置
- ✅ **请求日志**：完整的请求访问日志

## 🏗️ 架构设计

### 过滤器链

网关采用过滤器链设计，按顺序执行以下过滤器：

```mermaid
graph LR
    A[请求] --> B[I18nFilter<br/>国际化]
    B --> C[EccCryptoFilter<br/>加密解密]
    C --> D[SecurityFilter<br/>安全过滤]
    D --> E[DuplicateSubmitFilter<br/>防重复提交]
    E --> F[TenantFilter<br/>租户验证]
    F --> G[AuthenticationFilter<br/>认证]
    G --> H[SsoFilter<br/>单点登录]
    H --> I[InterfaceAuthFilter<br/>接口授权]
    I --> J[IssueTokensFilter<br/>令牌签发]
    J --> K[CanaryLoadBalancerFilter<br/>灰度路由]
    K --> L[后端服务]
```

### 核心组件

- **GatewayConfig**：网关配置管理
- **AbstractBaseFilter**：过滤器基类
- **FilterOrder**：过滤器执行顺序定义
- **JwtUtils**：JWT 工具类
- **NetworkUtils**：网络工具类

## 🚀 快速开始

### 1. 环境要求

- JDK 25+ (支持 Oracle JDK、OpenJDK、GraalVM、Azul Zulu 等)
- Maven 3.9.0+
- Redis 6.0+（用于缓存和防重复提交）
- Nacos 2.0+（用于服务发现和配置管理）
- Sentinel Dashboard（用于限流规则管理，可选）

### 2. 配置

#### 创建配置文件

创建 `application.yml` 文件（UTF-8 编码）：

```yaml
spring:
  main:
    allow-circular-references: true
    allow-bean-definition-overriding: true
  mvc:
    pathmatch:
      matching-strategy: ant_path_matcher
  cloud:
    # Nacos 配置
    nacos:
      # 服务发现配置
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: default
      # 配置中心配置
      config:
        server-addr: 127.0.0.1:8848
        namespace: default
        name: richie-gateway-service.yaml
        group: platform
        file-extension: yaml
        refresh-enabled: true
        enable-remote-sync-config: true
```

### 3. Nacos 配置中心配置

在 Nacos 配置中心创建 `richie-gateway-service.yaml` 配置文件：

```yaml
spring:
  cloud:
    # 限流服务配置
    sentinel:
      # Sentinel 控制台配置
      transport:
        dashboard: localhost:8719
        port: 8719
        heartbeat-interval-ms: 20000
      # Sentinel Nacos 数据源配置
      datasource:
        # 流控规则数据源配置
        flow:
          nacos:
            server-addr: ${spring.cloud.nacos.discovery.server-addr}
            data-id: ${spring.application.name}-flow-rules
            groupId: platform
            data-type: json
            rule-type: flow
        # 降级规则数据源配置
        degrade:
          nacos:
            server-addr: ${spring.cloud.nacos.discovery.server-addr}
            data-id: ${spring.application.name}-degrade-rules
            groupId: platform
            data-type: json
            rule-type: degrade
    
    # 网关路由配置
    gateway:
      # 跨域配置（生产环境建议注释，通过前置 HTTP 服务处理）
      globalcors:
        cors-configurations:
          '[/**]':
            allowedHeaders: "*"
            allowedOriginPatterns: "*"
            allowCredentials: true
            allowedMethods: "*"
      
      # 路由规则
      routes:
        # 门户服务路由
        - id: portal-service-outer
          uri: lb://portal-service
          predicates:
            - Path=/gateway/**,/portal/**,/tenant/**
        
        # 鉴权服务路由
        - id: kds-config-service-outer
          uri: lb://kds-config-service
          predicates:
            - Path=/api/sys/auth/**

# Redis 配置
spring:
  cache:
    redis:
      cache-null-values: false
  data:
    redis:
      server-type: standalone
      client-name: richie-gateway-cache
      client-type: lettuce
      host: 127.0.0.1
      password: 123456
      port: 6379
      database: 0
      timeout: 2000
      connect-timeout: 5000
      ssl:
        enabled: false
      lettuce:
        pool:
          max-active: 10
          max-idle: 5
          min-idle: 2
          max-wait: 10000
          time-between-eviction-runs: 300000

# 日志配置
logging:
  config: classpath:logback-spring.xml
  level:
    com.richie.gateway.GatewayApplication: info

# 平台配置
platform:
  gateway:
    # 网关使用部门
    department: platform
    # 访问记录保存路径
    visit-record-path: "platform:gateway:visit:"
    
    # 金丝雀发布配置
    deploy:
      # 是否启用金丝雀发布（默认：false）
      enable: true
      # 金丝雀版本测试条件类型（默认：NONE）
      # 可选值：
      #   - NONE（不启用）
      #   - ID（需要配合 id-list 配置）
      #   - VERSION（需要配合目标服务的 canary-category）
      canary-category: ID
      # 参与金丝雀版本测试的ID列表
      id-list:
        - '22121'
        - '22123'
        - '22125'
    
    # 令牌配置
    token:
      # 过期时间（单位：小时）
      expire-time: 1
      # 令牌续签时长（单位：分钟）
      # 在到期前指定分钟内请求系统时会触发续签操作
      renew-time: 10
      # JWT 交互令牌密钥
      secret: GTvNm6KZaENr1SZ6Ib
      # 黑名单令牌缓存路径
      blacklist-path: "platform:gateway:token:"
      # 登录页面路径（执行令牌签发的页面）
      login-uri-list:
        - /gateway/login
      # 忽略页面路径（正则匹配）
      ignore-uri-list:
        - (\/gateway).+
        - ^(\/menu-out\/getStoreCategories)$
    
    # API 接口安全过滤器配置
    security:
      # 启用开关
      enable: true
      # 过滤规则（默认：封禁IP）
      # 可选值：banned_ip、custom_http_status、redirect
      rule: banned_ip
      # 安全阈值（默认：120次）
      security-threshold: 120
      # 检测时间间隔单位（默认：分钟）
      security-time-interval-unit: minutes
      # 检测时间间隔值（默认：1）
      security-time-interval-value: 1
      # 封禁配置
      banned:
        # 永久封禁（默认：false）
        permanent: true
        # 永久封禁缓存路径
        permanent-path: "platform:gateway:security:permanent"
        # 非永久封禁时的封禁时间值（默认：1）
        security-block-time: 1
        # 非永久封禁时的封禁时间单位（默认：分钟）
        security-block-time-unit: minutes
      # 自定义返回错误信息
      custom-return:
        status: bad_gateway
        error-message: "我就是玩儿，你打我啊！"
      # 重定向配置
      redirect:
        security-redirect-uri: "/subway/loginPage?redirect=dashboard"
```

### 4. 启动网关

#### Docker 方式

使用 Docker 或 Docker Compose 挂载目录到容器内的 `/opt/app/` 目录，然后启动容器。

#### JAR 包方式

将 `application.yml` 文件存放到 JAR 包同目录下，执行：

```bash
java -jar richie-gateway-service-xxx-RELEASE.jar --spring.config.location=application.yml
```

#### K8S 方式

待定

## 📚 配置说明

### 金丝雀发布配置

#### 简单模式

在客户端请求头中加入：

```
X-Canary-Env: true
```

#### 高级模式

在客户端请求头中加入：

```
X-Canary-Category: ID          # 或 VERSION
X-Canary-Version: v2            # 当 X-Canary-Category = VERSION 时
X-Canary-Id: 22123              # 当 X-Canary-Category = ID 时
```

**注意：** 当 `canary-category: ID` 时，Gateway 会自动从请求中提取门店ID，无需手动设置 `X-Canary-Id`。如需手动指定，可直接设置 `X-Canary-Id` 请求头（优先级最高）。

### Token 配置

- **expire-time**：Token 过期时间（小时）
- **renew-time**：Token 续签时长（分钟），在到期前指定分钟内请求会触发续签
- **secret**：JWT 密钥，建议使用强随机字符串
- **blacklist-path**：Token 黑名单在 Redis 中的路径前缀
- **login-uri-list**：登录页面路径列表，这些路径会签发 Token
- **ignore-uri-list**：忽略 Token 验证的路径列表（正则匹配）

### 安全过滤器配置

- **rule**：过滤规则类型
  - `banned_ip`：封禁 IP
  - `custom_http_status`：自定义 HTTP 状态码
  - `redirect`：重定向到指定页面
- **security-threshold**：安全阈值，超过此值触发安全规则
- **security-time-interval-unit**：检测时间间隔单位（minutes/hours/days）
- **security-time-interval-value**：检测时间间隔值

### 路由配置

网关路由配置遵循 Spring Cloud Gateway 的路由规则：

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: service-name
          uri: lb://service-name
          predicates:
            - Path=/api/service/**
          filters:
            - StripPrefix=1
```

## 🔧 高级功能

### 1. 防重复提交

网关支持基于 Redis 的防重复提交机制，防止客户端重复提交请求。

### 2. ECC+AES-GCM 加密

支持端到端加密通信，确保数据传输安全。

### 3. 灰度发布

支持基于 ID 或版本的灰度发布，可以指定特定用户或版本使用新版本服务。

#### 按门店维度自动灰度

Gateway 支持**自动从请求中提取门店ID（storeId）**，无需客户端手动设置 `X-Canary-Id` 请求头。

**自动提取优先级：**
1. 请求头 `X-Canary-Id`（已存在则直接使用）
2. 请求头 `x-rd-request-shopcode`（门店编码）
3. JWT Token 中的 `storeId` 或 `shopCode` 字段
4. 路径参数中的 storeId（如 `/api/store/123/xxx`）
5. 查询参数 `storeId`（如 `?storeId=123`）

**配置示例：**

```yaml
platform:
  gateway:
    deploy:
      enable: true
      canary-category: ID
      id-list:
        - '1001'  # 门店ID 1
        - '1002'  # 门店ID 2
        - '1003'  # 门店ID 3
```

**详细文档：** 请参考 [灰度发布最佳实践-按门店维度.md](./docs/灰度发布最佳实践-按门店维度.md)

### 4. 限流和熔断

基于 Sentinel 的限流和熔断功能，保护后端服务不被过载。

## 📋 版本历史

### 4.5.0（2025年06月30日）

- 升级到 Spring Boot 3.5.0
- 新增防重复提交功能
- 新增 ECC+AES-GCM 加密通信功能
- 新增微服务限流、熔断和降级功能
- 梳理网关过滤器能力，对过滤器进行分层
- 修复一些已知问题

### 4.4.0（2025年05月10日）

- 升级到 Spring Boot 3.4.6
- 增强的安全性功能升级
- 一些已知的 BUG 修复
- 新增网关侧全局异常处理功能

### 4.3.0-RELEASE（2024年7月10日）

- 更新技术平台版本到 4.3.0
- 增加网关国际化与业务系统的同步
- 增加 SSO 功能支持

### 4.2.0-RELEASE（2024年5月15日）

- 更新技术平台版本到 4.2.0
- 增加多租户支持

### 3.0.0-RELEASE（2023年9月21日）

- Token 令牌在到期前指定时间段内支持自动续期
- 升级到 Spring Boot 3.0

### 2.2.1-RELEASE（2023年8月9日）

- 重构网关过滤器和服务器安全策略，使其更加灵活
- 调整过滤器顺序，解决因为过滤器顺序导致的 BUG
- 使用 JDK 25 的语法重构网关代码

### 2.2.0-RELEASE（2023年8月4日）

- 初始版本创建（包含功能：网关鉴权、网关令牌办法、服务器安全拦截）

## 🔍 监控和日志

### 日志配置

网关使用 Logback 进行日志管理，日志配置文件：`logback-spring.xml`

### 健康检查

通过 Spring Boot Actuator 提供健康检查端点：

```bash
curl http://localhost:9500/actuator/health
```

### 访问日志

网关会记录所有请求的访问日志，包括：

- 请求路径
- 请求方法
- 请求头信息
- 响应状态码
- 响应时间

## 🛠️ 开发指南

### 添加自定义过滤器

1. 继承 `AbstractBaseFilter` 类
2. 实现 `doFilter` 和 `enableVerifyFilter` 方法
3. 在 `FilterOrder` 中定义过滤器执行顺序
4. 注册为 Spring Bean

### 扩展路由规则

在 Nacos 配置中心添加新的路由规则即可，无需重启网关。

## 🔗 相关文档

### 详细设计文档

- 📖 **[网关设计文档](./docs/网关设计文档.md)** - 完整的设计文档，包含部署架构、过滤器架构、核心功能详解、配置指南、客户端集成等
- 📊 **[网关熔断器架构图](./docs/网关熔断器架构图.md)** - Sentinel 限流熔断架构详细设计图

### 平台文档

- [Richie Platform README](../README.md) - 平台总览
- [Richie Base Platform](../richie-base/README.md) - 基础包文档
- [Richie Component Platform](../richie-component/README.md) - 组件库文档

## 🤝 贡献指南

欢迎贡献代码和文档！请遵循以下规范：

1. **代码规范**：遵循项目代码风格
2. **文档规范**：提供完整的注释和使用说明
3. **测试规范**：提供单元测试和集成测试
4. **提交规范**：遵循 Conventional Commits 规范

## 📞 联系方式

- **维护者**：王锦阳
- **邮箱**：richie696@icloud.com

---

**Richie Gateway Service** - 通用 API 网关服务 🚀
