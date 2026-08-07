# Atlas Richie OAuth 2.1组件 (atlas-richie-oauth-parent)

OAuth 2.1 鉴权组件，提供 Token 端点、客户端管理、Scope 解析等能力，支持 OAuth 2.1 标准（RFC 9000 系列），满足 MCP Server/Client
鉴权需求。

> **目标读者**：业务服务开发者、网关服务维护者。如果你想知道"这个组件能帮我解决什么问题、怎么用"，这是你要的文档。
> **组件定位**：本工程提供可复用的 OAuth 协议内核和适配能力，不是带登录页、管理后台和业务数据库的独立 Authorization Server。三方职责边界见 [OAuth 平台架构与职责边界设计](docs/zh/oauth-platform-architecture.md)。
>
> **组件内部设计**：见 [docs/zh/oauth-component-design.md](docs/zh/oauth-component-design.md)（[English](docs/en/oauth-component-design.md)）。

---

## 📖 目录

- [🎯 模块概览](#🎯-模块概览)
    - [它能做什么和不能做什么](#它能做什么和不能做什么)
    - [设计选择](#设计选择)
- [🚀 快速开始（oauth-core）](#🚀-快速开始（oauth-core）)
    - [1. 添加依赖](#1-添加依赖)
    - [2. 配置](#2-配置)
    - [3. 写代码](#3-写代码)
- [🔧 核心功能（oauth-core）](#🔧-核心功能（oauth-core）)
    - [1. TokenEndpoint — Token 全生命周期管理](#1-tokenendpoint-—-token-全生命周期管理)
    - [2. ClientRegistry — 客户端注册表](#2-clientregistry-—-客户端注册表)
    - [3. ScopeResolver — Scope 路径解析](#3-scoperesolver-—-scope-路径解析)
    - [4. TokenStore SPI — 自定义存储实现](#4-tokenstore-spi-—-自定义存储实现)
- [⚙️ 完整配置参考](#⚙️-完整配置参考)
    - [每日签发次数限制规则](#每日签发次数限制规则)
- [🏗️ MCP 集成说明](#🏗️-mcp-集成说明)
- [🎯 最佳实践](#🎯-最佳实践)
    - [1. Token 密钥管理](#1-token-密钥管理)
    - [2. IP 白名单](#2-ip-白名单)
    - [3. Scope 精细化控制](#3-scope-精细化控制)
    - [4. 异常处理](#4-异常处理)
- [⚠️ 已知限制](#⚠️-已知限制)
- [❓ 常见问题](#❓-常见问题)
    - [1. Token 签发失败，提示"客户端不存在"](#1-token-签发失败，提示客户端不存在)
    - [2. refresh_token 刷新提示"刷新令牌绑定 IP 不匹配"](#2-refreshtoken-刷新提示刷新令牌绑定-ip-不匹配)
    - [3. 如何自定义 Token 存储？](#3-如何自定义-token-存储？)
    - [4. access_token 和 refresh_token 的区别？](#4-accesstoken-和-refreshtoken-的区别？)
    - [5. oauth-authz 和 oauth-dcr 什么时候可用？](#5-oauth-authz-和-oauth-dcr-什么时候可用？)
- [📚 相关文档](#📚-相关文档)

---

## 🎯 模块概览

```
atlas-richie-oauth-parent/
├── atlas-richie-oauth-contract           # 协议 DTO、错误码、跨模块契约
├── atlas-richie-oauth-cache              # OAuth 缓存、锁、重放状态抽象
├── atlas-richie-oauth-core               # Token、Client、Scope 核心
├── atlas-richie-oauth-authz              # 授权码+PKCE
├── atlas-richie-oauth-oidc               # OIDC Provider：ID Token、UserInfo、Discovery
├── atlas-richie-oauth-dcr                # 动态客户端注册
├── atlas-richie-oauth-client              # OAuth/OIDC Metadata、Token、introspection、UserInfo 客户端
├── atlas-richie-oauth-resource-server    # JWT/JWKS、introspection 资源校验
├── atlas-richie-oauth-spring-boot-starter# Spring Boot 自动装配
├── atlas-richie-oauth-gateway-adapter    # WebFlux/Gateway 适配器
└── atlas-richie-oauth-test               # OAuth 测试工具与测试夹具
```

| 模块          | 状态       | 说明                                                      |
|---------------|------------|-----------------------------------------------------------|
| `oauth-core`  | **已实现** | Token 生命周期、客户端认证/仓储、Scope/Resource、刷新令牌轮换与重放检测、设备授权领域服务 |
| `oauth-authz` | **当前实现** | Authorization Code、PKCE、AS Metadata；当前实现仍带 Servlet/Session 适配边界 |
| `oauth-oidc` | **已实现协议能力** | ID Token、openid/nonce、UserInfo Claims 过滤、Discovery、query/form_post/hybrid 响应契约、Logout 校验；用户/MFA/HTTP Controller 由 AS 服务负责 |
| `oauth-dcr`   | **当前实现** | Dynamic Client Registration（RFC 7591）领域服务；HTTP 入口和持久化由 AS 服务负责 |
| `oauth-contract` | **已实现** | 标准请求/响应、错误码、主体和跨模块协议契约 |
| `oauth-cache` | **已实现** | 单进程缓存、组件 Cache 适配、锁和缓存 Key 边界 |
| `oauth-client` | **已实现** | OAuth Metadata、Token、introspection 和 OIDC Discovery/UserInfo 标准客户端 |
| `oauth-resource-server` | **已实现** | JWT/JWKS 校验、introspection fallback 和短缓存 |
| `oauth-spring-boot-starter` | **已实现** | Resource Server、Cache、Gateway Adapter 条件装配 |
| `oauth-gateway-adapter` | **已实现** | Bearer 提取、主体透传和 WebFlux Filter 外观 |
| `oauth-test` | **已实现** | OAuth Server/Gateway 可复用的测试夹具、黑盒 HTTP、RSA/JWKS、断言和 Redis 集成测试基类 |

### 它能做什么和不能做什么

| ✅ 能做什么                                             | ❌ 不能做什么                               |
|---------------------------------------------------------|---------------------------------------------|
| 提供 Token、授权码、PKCE、DCR、Device Authorization 等可复用协议能力 | 独立部署的登录页、管理后台和完整 AS 运行时 |
| Token 校验、内省、撤销和存储 SPI                        | SAML 2.0 / WS-Federation                  |
| Redis/Cache 适配和可替换的 Client/Token 存储边界        | LDAP / Active Directory 的具体接入实现        |
| 为自建 AS、PaaS AS、Gateway、MCP 提供统一适配基础       | 用户数据库、授权同意页面和业务资源路由        |

### 设计选择

- ✅ **协议能力可复用** — 通过领域服务、SPI 和 Adapter 供独立 AS 与资源服务器使用
- ✅ **存储可替换** — 可接入 `atlas-richie-component-cache`，但不替代 AS 的权威数据库
- ✅ **支持 JWT/JWKS 资源校验方向** — 具体密钥托管和轮换由 OAuth Service 负责
- ✅ **兼容自建和 PaaS AS** — 通过标准 Metadata、Token、Introspection、Revoke、JWKS 端点适配
- ✅ **OIDC 可选扩展** — `oauth-oidc` 提供 Provider 协议和 SPI，不强制 M2M OAuth 客户端引入用户身份能力
- ✅ **OIDC Logout 通道** — 提供 Front-Channel iframe、Backchannel Logout Token 和投递 SPI，HTTP 投递由 OAuth Service 注入
- ✅ **RFC 8628 Device Authorization Grant** — 设备码签发、用户码审批、轮询间隔/`slow_down`、一次性兑换
- ✅ **可轮换签名与 JWKS** — HMAC/RSA signer、typed custom claims、`kid` 和 JWK 发布 SPI
- ✅ **DPoP 资源绑定** — 可选启用 RFC 9449 proof 校验、`ath`、`cnf.jkt`、nonce 和分布式 `jti` 防重放

---

## 🚀 快速开始（oauth-core）

### 1) 添加依赖

```xml
<dependency>
    <groupId>cn.richie696.component</groupId>
    <artifactId>atlas-richie-oauth-core</artifactId>
</dependency>
```

### 2) 配置

```yaml
platform:
  component:
    oauth:
      enabled: true
      # Token 签发密钥（推荐 32 位随机字符串）
      tokenSecret: "your-32-char-secret-key-here-!"
      # access_token 默认有效期（小时）
      defaultTokenValidDuration: 2
      # refresh_token 默认有效期（小时，默认 720 = 30 天）
      defaultRefreshTokenValidDuration: 720
      # 签发新令牌时是否作废旧令牌（默认 false）
      revokePreviousTokensOnIssue: false
      # 是否启用每日签发次数限制（默认 true）
      enableDailyIssueLimit: true
```

### 3) 写代码

组件自动装配后，直接注入即可：

```java
import cn.richie696.component.oauth.core.TokenEndpoint;
import cn.richie696.component.oauth.core.ClientRegistry;
import model.cn.richie696.component.oauth.core.TokenResponse;

// 注入 TokenEndpoint
@Autowired
private TokenEndpoint tokenEndpoint;

// 1. 签发 Token（client_credentials 模式）
TokenResponse token = tokenEndpoint.generateToken(clientId, clientSecret, clientIp);

// 2. 刷新 Token
TokenResponse newToken = tokenEndpoint.refreshToken(refreshToken, clientIp);

// 3. 验证 Token
ClientConfig config = tokenEndpoint.verifyAccessToken(accessToken);

// 4. 内省 Token
TokenIntrospection introspection = tokenEndpoint.introspectToken(accessToken);

// 5. 撤销 Token
tokenEndpoint.revokeToken(token, tokenTypeHint);
```

---

## 🔧 核心功能（oauth-core）

### 1) `TokenEndpoint` — `Token` 全生命周期管理

#### 1.1 签发 `Token`（client_credentials）

```java
TokenResponse response = tokenEndpoint.generateToken(clientId, clientSecret, clientIp);
```

返回：

```java
TokenResponse {
    accessToken: "eyJhbGciOiJIUzI1NiJ9...",  // JWT
    tokenType: "Bearer",
    expiresIn: 7200,                            // 秒
    refreshToken: "Xxx..."                      // 仅第一次签发时有值
}
```

**签发流程**：

1. 验证 `clientSecret`（时序安全比较）
2. 检查客户端是否启用
3. 检查每日签发次数限制（可选）
4. 作废旧令牌（可选）
5. 生成 JWT access_token + 随机 refresh_token
6. 存储 refresh_token 到 Redis（绑定 IP）

#### 1.2 刷新 `Token`

```java
TokenResponse response = tokenEndpoint.refreshToken(refreshToken, clientIp);
```

**刷新流程**：

1. 加分布式锁防止并发刷新（锁 key = `refresh-token-lock:{token}`）
2. 验证 refresh_token 存在且未过期
3. 验证 IP 绑定（如果配置了 IP 白名单）
4. 物理删除旧 refresh_token
   同时保留短期 consumed-marker，用于识别旧 refresh_token 重放并触发异常计数/审计
5. 生成新 access_token + refresh_token
6. 重新存储新 refresh_token

签发和刷新流程支持 RFC 8707 `resource` 参数，并将资源绑定到 access token 的 `aud`；授权码会保存授权阶段的 resource，兑换时禁止切换到其他资源。

#### 1.3 验证 `Token`

```java
ClientConfig config = tokenEndpoint.verifyAccessToken(accessToken);
if (config == null) {
    // token 无效（签名错误 / 已过期 / 已在黑名单）
}
```

**验证步骤**：

1. JWT 签名验证（HMAC256）
2. 检查黑名单（`access-token-blacklist:{token}`）
3. 检查过期时间

#### 1.4 内省 `Token`

```java
TokenIntrospection result = tokenEndpoint.introspectToken(accessToken);
if (result.isActive()) {
    String clientId = result.getClientId();
    String scope = result.getScope();
}
```

#### 1.5 撤销 `Token`

```java
// 撤销 refresh_token（物理删除）
tokenEndpoint.revokeToken(refreshToken, "refresh_token");

// 撤销 access_token（加入黑名单，过期自动清理）
tokenEndpoint.revokeToken(accessToken, "access_token");

// 不指定 type，组件自动判断（有无 "." → access_token / refresh_token）
tokenEndpoint.revokeToken(token, null);
```

### 2) `ClientRegistry` — 客户端注册表

> 客户端配置以 Redis Hash 存储，key = `third-party-client:{clientId}`

#### 2.1 读取客户端配置

```java
// 单字段
Boolean enabled = clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED);

// 批量字段
Map<ClientConfig.Field, Object> fields = clientRegistry.getClientConfig(
    clientId,
    ClientConfig.Field.ENABLED,
    ClientConfig.Field.SCOPES
);

// 检查客户端是否有效
boolean valid = clientRegistry.isClientValid(clientId);

// 验证密钥
boolean match = clientRegistry.verifyClientSecret(clientId, clientSecret);
```

#### 2.2 注册测试客户端

```java
// 仅供测试/演示用，生成随机 clientId + clientSecret
ClientConfig testClient = clientRegistry.registerTestClient("my-app");
System.out.println("clientId: " + testClient.getClientId());
System.out.println("clientSecret: " + testClient.getClientSecret());
```

#### 2.3 `ClientConfig` 数据结构

| Field                          | 类型           | 说明                         |
|--------------------------------|----------------|------------------------------|
| `CLIENT_ID`                    | String         | 客户端 ID                    |
| `CLIENT_SECRET`                | String         | 客户端密钥                   |
| `CLIENT_NAME`                  | String         | 客户端名称                   |
| `ENABLED`                      | Boolean        | 是否启用                     |
| `SCOPES`                       | List\<String\> | 授权范围列表                 |
| `TOKEN_VALID_DURATION`         | Integer        | token 有效期（小时）         |
| `REFRESH_TOKEN_VALID_DURATION` | Integer        | refresh_token 有效期（小时） |
| `RATE_LIMIT`                   | Integer        | 速率限制                     |
| `IP_WHITELIST`                 | List\<String\> | IP 白名单                    |

### 3) `ScopeResolver` — `Scope` 路径解析

#### 3.1 获取接口所需 `Scope`

```java
@Autowired
private ScopeResolver scopeResolver;

// 根据路径和方法获取所需 scopes（AntPath 匹配）
List<String> required = scopeResolver.getRequiredScopes("/api/order/create", "POST");

// 验证 token scopes 是否满足要求（OR 逻辑：满足其一即可）
boolean ok = scopeResolver.verifyScope(tokenScopes, required);

// 从 JWT 中解析 scope claim
Set<String> tokenScopes = scopeResolver.extractScopesFromToken(accessToken);
```

### 4) `TokenStore` `SPI` — 自定义存储实现

组件内置 `DefaultTokenStore`（Redis 实现），如需替换为 JDBC 等其他存储：

```java
// 1. 实现 TokenStore 接口
public class MyTokenStore implements TokenStore {
    // ... 实现所有方法
}

// 2. 在 resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 中注册
// 或通过 @Bean 覆盖默认实现
@Bean
public TokenStore tokenStore() {
    return new MyTokenStore();
}
```

---

### 5) Device Authorization Grant（RFC 8628）

组件负责设备码生命周期和轮询状态，不负责设备登录页面。OAuth Service 在用户完成登录/MFA/同意后调用 `approve(userCode, subject)`；客户端通过 `TokenEndpoint.exchangeDeviceCode(...)` 兑换令牌。轮询过快会返回 `slow_down`，授权成功后的设备码只能消费一次。

### 6) 签名器、JWKS 与 Claims

生产 AS 应注入 `AccessTokenSigner`（推荐 RSA）和 `JwkSetProvider`，将 `keys()` 输出暴露为标准 JWKS，并使用稳定的 `kid` 做密钥轮换。租户、角色等扩展声明通过 `AccessTokenClaimsCustomizer` 注入；组件会拒绝覆盖 `iss/sub/aud/exp/iat/nbf/jti/scope/client_id` 等保留声明。

### 7) 分布式缓存边界

`oauth-cache` 的 `OAuthCache` 是 OAuth 核心唯一缓存端口；`GlobalCacheOAuthCache` 负责接入 `atlas-richie-component-cache`，`InMemoryOAuthCache` 只适合单实例/测试。客户端配置、授权码、刷新令牌和重放标记由 OAuth Service 选择权威持久化策略，Gateway 只通过 Resource Server 组件使用其运行时缓存能力。

Resource Server Starter 支持三种模式：只配置 `jwk-set-uri` 为 JWT-only；只配置 `introspection-uri` 且保持 `introspection-fallback=true`（默认）为 introspection-only；同时配置两者为 JWT + introspection fallback 的 hybrid 模式。两者都未配置时会 fail-closed。

## ⚙️ 完整配置参考

配置前缀：`platform.component.oauth`

| 配置项                             | 类型    | 默认值  | 说明                               |
|------------------------------------|---------|---------|------------------------------------|
| `enabled`                          | boolean | `false` | 是否启用 OAuth 2.1 组件            |
| `tokenSecret`                      | String  | —       | Token 签发密钥（必填，推荐 32 位） |
| `defaultTokenValidDuration`        | Integer | `2`     | access_token 默认有效期（小时）    |
| `defaultRefreshTokenValidDuration` | Integer | `720`   | refresh_token 默认有效期（小时）   |
| `revokePreviousTokensOnIssue`      | boolean | `false` | 签发新令牌时作废旧令牌             |
| `enableDailyIssueLimit`            | boolean | `true`  | 启用每日签发次数限制               |

### 每日签发次数限制规则

```
maxIssuesPerDay = base + 2
base = max(24 / tokenValidDuration, 1)
```

| tokenValidDuration | base | maxIssuesPerDay |
|--------------------|------|-----------------|
| 1 小时             | 24   | 26              |
| 2 小时             | 12   | 14              |
| 4 小时             | 6    | 8               |
| 8 小时             | 3    | 5               |
| 24 小时            | 1    | 3               |

---

## 🏗️ MCP 集成说明

OAuth
组件设计支持 [Model Context Protocol (MCP)](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization)
鉴权场景：

| MCP 角色             | OAuth 2.1 角色                  | 使用模块                 |
|----------------------|---------------------------------|--------------------------|
| MCP Server           | Protected Resource Server       | oauth-core + 网关 Filter |
| MCP Client           | OAuth Client                    | oauth-client（目标模块） |
| Authorization Server | Token Endpoint + Authz Endpoint | 独立 oauth-service + 组件 |

**当前已支持**：MCP Server 端作为 Resource Server，验证来自 MCP Client 的 Bearer Token。

---

## 🎯 最佳实践

### 1) `Token` 密钥管理

- 生产环境务必使用 32 位以上随机字符串
- 密钥不要硬编码，使用环境变量或密文管理：
  ```yaml
  platform:
    component:
      oauth:
        tokenSecret: ${OAUTH_TOKEN_SECRET}
  ```

### 2) `IP` 白名单

客户端注册时可配置 `IP_WHITELIST`，`refresh_token` 会验证请求 IP 是否在白名单内：

```java
ClientConfig config = ClientConfig.builder()
    .clientId(clientId)
    .clientSecret(clientSecret)
    .enabled(true)
    .ipWhitelist(List.of("10.0.0.0/8", "192.168.1.1"))
    .build();
```

### 3) `Scope` 精细化控制

```java
ClientConfig config = ClientConfig.builder()
    .clientId(clientId)
    .clientSecret(clientSecret)
    .enabled(true)
    .scopes(List.of("read:order", "write:order", "read:product"))
    .build();
```

网关 Filter 会根据请求路径 + HTTP 方法匹配接口所需的 scope：

```yaml
# 网关接口 scope 配置示例
gateway:
  scope:
    "/api/order/create":
      method: "POST"
      scopes: ["write:order"]
    "/api/order/*":
      method: "GET"
      scopes: ["read:order"]
```

### 4) 异常处理

组件使用统一异常类型：

```java

import exception.cn.richie696.contract.BusinessException;

try{
        tokenEndpoint.generateToken(clientId, clientSecret, ip);
}catch(
BusinessException e){
String errorCode = e.getCode();
    if(OAuth2Constants.ERROR_INVALID_CLIENT.

equals(errorCode)){
        // 客户端认证失败
        }else if(OAuth2Constants.ERROR_RATE_LIMIT_EXCEEDED.

equals(errorCode)){
        // 超过每日签发限制
        }
        }
```

---

## ⚠️ 已知限制

| 限制                               | 影响                      | 解决方式                                       |
|------------------------------------|---------------------------|------------------------------------------------|
| **OIDC 用户数据不内置**            | 组件不拥有用户数据库      | OAuth Service 注入 `OidcUserInfoProvider`      |
| **无内置 IdP**                     | 需要自行接入用户存储      | 实现 `UserDetailsService`                      |
| **不支持 SAML 2.0**                | 不支持 SAML 联合认证      | 使用独立 SAML IdP                              |
| **Refresh token 旋转不可关闭**     | 遵循 RFC 6749 §10.4       | 实现自定义 `OAuth2TokenGenerator`              |
| **授权码和 DCR 需要 AS 服务承载** | 组件提供领域能力，但不提供完整登录/管理/持久化运行时 | 使用独立 `atlas-richie-oauth-service`，或通过标准协议接入 PaaS AS |

## ❓ 常见问题

### 1) `Token` 签发失败，提示"客户端不存在"

客户端配置未录入 Redis。使用 `ClientRegistry.registerTestClient()` 快速注册，或手动写入 Redis：

```java
// 测试注册
ClientConfig testClient = clientRegistry.registerTestClient("my-app");
```

### 2) refresh_token 刷新提示"刷新令牌绑定 `IP` 不匹配"

`refresh_token` 默认绑定了签发时的客户端 IP。如果客户端 IP 动态变化（NAT/代理），建议关闭 IP 绑定检查或配置固定出口 IP。

### 3) 如何自定义 `Token` 存储？

实现 `TokenStore` SPI 接口，并通过 `@Bean`
覆盖默认实现。详见 [TokenStore SPI 扩展设计](docs/zh/oauth-component-design.md#25-tokenstore-spi-扩展设计)。

### 4) access_token 和 refresh_token 的区别？

|          | Access Token        | Refresh Token            |
|----------|---------------------|--------------------------|
| 格式     | JWT（自包含）       | 随机字符串（需存储验证） |
| 有效期   | 短期（默认 2 小时） | 长期（默认 30 天）       |
| 验证方式 | JWT 签名 + 黑名单   | Redis 物理存储验证       |
| 撤销方式 | 加入黑名单          | 物理删除                 |
| IP 绑定  | 可选                | 可选                     |

### 5) oauth-authz 和 oauth-dcr 什么时候可用？

当前组件已经包含对应的领域实现，但生产使用仍需要独立 AS 服务提供 HTTP 端点、用户登录、授权同意和持久化：

- **oauth-authz**：Authorization Code + PKCE、AS Metadata
- **oauth-dcr**：Dynamic Client Registration (RFC 7591)、Client Metadata

具体拆分、迁移和验收标准见 [OAuth 平台架构与职责边界设计](docs/zh/oauth-platform-architecture.md)。

---

## 📚 相关文档

| 文档                                                    | 说明                                                   |
|---------------------------------------------------------|--------------------------------------------------------|
| [系统设计文档 (zh)](docs/zh/oauth-component-design.md)  | 完整架构设计、模块划分、时序图（中文）                 |
| [System Design (en)](docs/en/oauth-component-design.md) | Full architecture, module breakdown, sequence diagrams |
| [OAuth 平台架构与职责边界](docs/zh/oauth-platform-architecture.md) | 组件、独立 AS、Gateway 的职责边界和迁移基线 |
| atlas-richie-gateway-service                            | 网关服务（组件消费者）                                 |
| atlas-richie-component                                  | 组件库总览                                             |
