# Richie MFA Component

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://github.com/richie-platform/richie-component-mfa/blob/main/LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.0-green.svg)](https://github.com/richie-platform/richie-component-mfa/releases)

## 📖 简介

Richie MFA组件是一个企业级多因子认证解决方案，基于RFC 6238/4226标准实现TOTP/HOTP算法，为企业应用提供高性能、高安全性的身份验证能力。

**重要架构说明**：MFA组件采用**分离式架构设计**，拆分为两个独立的模块：

- **richie-component-mfa-validation**：验证模块，部署在 `richie-gateway-service` 中
  - **职责**：MFA验证逻辑，只读GlobalCache（richie-component-cache），**零数据库依赖**
  - **特点**：轻量级、高性能、毫秒级响应
  
- **richie-component-mfa-management**：管理模块，部署在 `richie-general-service` 中
  - **职责**：MFA管理功能（绑定、解绑、状态管理等），操作数据库
  - **特点**：完整的CRUD操作，使用Liquibase管理DDL

## 🎯 设计原则

### 独立性与解耦

**MFA组件完全独立于业务系统的User表结构**：

- ✅ **只关联User表主键ID**：MFA组件只需要业务系统User表的主键ID（`userId`），不维护任何用户信息
- ✅ **不依赖User表结构**：不同业务系统的User表结构可以完全不同，MFA组件都能适配
- ✅ **零侵入集成**：业务系统无需为MFA修改已有的User模块代码
- ✅ **灵活适配**：支持String或Long类型的User表主键（统一转换为String存储）

### 租户功能可选

**MFA组件支持可配置的租户功能，并自动读取网关配置**：

- ✅ **租户可选**：通过 `enableTenant` 配置项控制是否启用租户功能（默认 `false`）
- ✅ **自动读取网关配置**：如果未显式配置，自动读取 `platform.gateway.tenant.enable`（推荐方式）
- ✅ **无租户系统**：如果系统没有租户概念，`tenantId` 可以为 `null`，数据库 `tenant_id` 字段为 `NULL`
- ✅ **有租户系统**：如果系统有租户概念，启用租户后 `tenantId` 必须提供
- ⚠️ **配置一致性**：两个模块（validation和management）的 `enableTenant` 配置必须保持一致

**示例（无租户系统）**：
```java
// 无租户系统：tenantId可以为null
MfaBindRequest request = new MfaBindRequest();
request.setUserId(user.getId().toString());
request.setTenantId(null);  // 或省略
request.setDeviceType("TOTP");

mfaBindService.bindDevice(null, request.getUserId(), request.getDeviceType());
```

**示例（有租户系统）**：
```java
// 有租户系统：tenantId必须提供
MfaBindRequest request = new MfaBindRequest();
request.setUserId(user.getId().toString());
request.setTenantId(user.getTenantId());  // 必须提供
request.setDeviceType("TOTP");

mfaBindService.bindDevice(request.getTenantId(), request.getUserId(), request.getDeviceType());
```

## ✨ 核心特性

### 🔐 多重认证方式
- **TOTP认证**：基于时间的一次性密码（Time-based One-Time Password）
- **HOTP认证**：基于事件的一次性密码（HMAC-based One-Time Password）
- **短信验证码**：支持主流短信服务商集成
- **邮箱验证码**：SMTP邮件验证支持

### 🛡️ 安全保障
- **密钥加密存储**：KMS/HSM管理，AES-256-GCM加密
- **防重放攻击**：验证码使用记录，TTL控制
- **审计日志完整性**：数字签名，防篡改
- **防暴力破解**：智能失败次数限制

### ⚡ 高性能设计
- **GlobalCache缓存**：热点数据高速访问（richie-component-cache）
- **架构分离**：验证与管理完全分离，网关零数据库依赖
- **异步处理**：非阻塞操作提升性能

### 🎯 易用性强
- **Spring Boot Starter**：一键集成
- **RESTful API**：标准化接口设计（管理模块）
- **自动配置**：开箱即用
- **详细文档**：完善的使用指南

## 🚀 快速开始

### 1. 添加依赖

#### 网关服务（验证模块）

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-mfa-validation</artifactId>
</dependency>
```

#### 通用服务（管理模块）

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-mfa-management</artifactId>
</dependency>
```

### 2. 配置文件

#### 网关服务配置

```yaml
# 网关服务配置
mfa:
  validation:
    enabled: true  # 启用MFA验证
    # enable-tenant: false  # 可选：显式配置，如果不配置则自动读取 platform.gateway.tenant.enable
    totp:
      time-window: 30
      window-size: 1
      code-length: 6
    security:
      max-attempts: 5
      lock-duration-seconds: 300

# 网关租户配置（MFA组件会自动读取）
platform:
  gateway:
    tenant:
      enable: false  # 如果MFA未显式配置enable-tenant，会自动读取此配置

# 注意：enableTenant配置必须与management模块保持一致！

# 平台缓存组件配置（richie-component-cache）
platform:
  component:
    cache:
      # 缓存连接配置（由richie-component-cache管理）
```

#### 通用服务配置

```yaml
# 通用服务配置
platform:
  component:
    mfa:
      management:
        enabled: true  # 启用MFA管理功能
        # enable-tenant: false  # 可选：显式配置，如果不配置则自动读取 platform.gateway.tenant.enable
        controller-enabled: true  # 启用Controller（默认）
        api-prefix: /api/mfa

# 网关租户配置（MFA组件会自动读取）
platform:
  gateway:
    tenant:
      enable: false  # 如果MFA未显式配置enable-tenant，会自动读取此配置
    sms:
      enabled: true
      provider: aliyun
      accessKeyId: ${SMS_ACCESS_KEY_ID}
      accessKeySecret: ${SMS_ACCESS_KEY_SECRET}
    email:
      enabled: true
      smtpHost: smtp.example.com
      smtpPort: 465

# 平台Liquibase配置
platform:
  component:
    liquibase:
      enable: true  # 启用Liquibase迁移

# 平台缓存组件配置（richie-component-cache）
platform:
  component:
    cache:
      # 缓存连接配置（由richie-component-cache管理）
```

### 3. 启动服务

启动服务后：
- **网关服务**：MFA验证过滤器自动生效
- **通用服务**：数据库表自动创建（Liquibase），API自动注册

## 📚 文档

- **MFA 管理内置控制器 API 文档（Apifox）**：[MFA 管理接口 - platform](https://6n26bnszvc.apifox.cn/) — 绑定、激活、解绑、可信设备、设为主管理设备、备份码验证等接口定义与示例

详细的设计文档请参考 [docs](./docs/) 目录：

- [MFA组件完整设计方案.md](./docs/MFA组件完整设计方案.md) - 完整设计方案
- [MFA组件时序图设计.md](./docs/MFA组件时序图设计.md) - 时序图设计
- [MFA组件架构图设计.md](./docs/MFA组件架构图设计.md) - 架构图设计
- [ETCD+Vault部署方案.md](./docs/ETCD+Vault部署方案.md) - ETCD+Vault 部署方案

## 🔗 相关标准

- [RFC 6238](https://tools.ietf.org/html/rfc6238) - TOTP: Time-Based One-Time Password Algorithm
- [RFC 4226](https://tools.ietf.org/html/rfc4226) - HOTP: An HMAC-Based One-Time Password Algorithm
- [NIST 800-63B](https://pages.nist.gov/800-63-3/sp800-63b.html) - Digital Identity Guidelines
- [OWASP MFA Guide](https://owasp.org/www-community/Multi-Factor_Authentication) - Multi-Factor Authentication Guide

---

*最后更新：2026年1月15日*
