# Atlas Richie OAuth 2.1组件 (atlas-richie-component-oauth)

OAuth 2.1 鉴权组件，提供 Token 端点、客户端管理、Scope 解析等能力，支持 OAuth 2.1 标准（RFC 9000 系列），满足 MCP Server/Client 鉴权需求。

> **目标读者**：业务服务开发者、网关服务维护者。如果你想知道"这个组件能帮我解决什么问题、怎么用"，这是你要的文档。
> **深度设计**：OAuth 组件的完整设计思路见 [docs/oauth-component-design.md](./docs/oauth-component-design.md)。

---

## 📖 目录

- [🎯 模块概览](#🎯-模块概览)
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
- [❓ 常见问题](#❓-常见问题)
  - [1. Token 签发失败，提示"客户端不存在"](#1-token-签发失败，提示客户端不存在)
  - [2. refresh_token 刷新提示"刷新令牌绑定 IP 不匹配"](#2-refreshtoken-刷新提示刷新令牌绑定-ip-不匹配)
  - [3. 如何自定义 Token 存储？](#3-如何自定义-token-存储？)
  - [4. access_token 和 refresh_token 的区别？](#4-accesstoken-和-refreshtoken-的区别？)
  - [5. oauth-authz 和 oauth-dcr 什么时候可用？](#5-oauth-authz-和-oauth-dcr-什么时候可用？)
- [📚 相关文档](#📚-相关文档)
- [📎 🗺️ Roadmap](#📎-🗺️-roadmap)
---

## 🎯 模块概览

```
atlas-richie-component-oauth/
├── atlas-richie-component-oauth-core    # OAuth2.1 核心（已实现）
├── atlas-richie-component-oauth-authz   # 授权码+PKCE 模块（规划中）
└── atlas-richie-component-oauth-dcr     # 动态客户端注册模块（规划中）
```

| 模块 | 状态 | 说明 |
|------|------|------|
| `oauth-core` | **已实现** | Token 端点、客户端注册表、Scope 解析、Token 存储（Redis） |
| `oauth-authz` | 规划中 | Authorization Code、PKCE、AS Metadata |
| `oauth-dcr` | 规划中 | Dynamic Client Registration (RFC 7591) |

---

## 🚀 快速开始（oauth-core）

### 1) 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-oauth-core</artifactId>
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
import com.richie.component.oauth.core.TokenEndpoint;
import com.richie.component.oauth.core.ClientRegistry;
import com.richie.component.oauth.core.model.TokenResponse;

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
5. 生成新 access_token + refresh_token
6. 重新存储新 refresh_token

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

| Field | 类型 | 说明 |
|-------|------|------|
| `CLIENT_ID` | String | 客户端 ID |
| `CLIENT_SECRET` | String | 客户端密钥 |
| `CLIENT_NAME` | String | 客户端名称 |
| `ENABLED` | Boolean | 是否启用 |
| `SCOPES` | List\<String\> | 授权范围列表 |
| `TOKEN_VALID_DURATION` | Integer | token 有效期（小时） |
| `REFRESH_TOKEN_VALID_DURATION` | Integer | refresh_token 有效期（小时） |
| `RATE_LIMIT` | Integer | 速率限制 |
| `IP_WHITELIST` | List\<String\> | IP 白名单 |

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

## ⚙️ 完整配置参考

配置前缀：`platform.component.oauth`

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 是否启用 OAuth 2.1 组件 |
| `tokenSecret` | String | — | Token 签发密钥（必填，推荐 32 位） |
| `defaultTokenValidDuration` | Integer | `2` | access_token 默认有效期（小时） |
| `defaultRefreshTokenValidDuration` | Integer | `720` | refresh_token 默认有效期（小时） |
| `revokePreviousTokensOnIssue` | boolean | `false` | 签发新令牌时作废旧令牌 |
| `enableDailyIssueLimit` | boolean | `true` | 启用每日签发次数限制 |

### 每日签发次数限制规则

```
maxIssuesPerDay = base + 2
base = max(24 / tokenValidDuration, 1)
```

| tokenValidDuration | base | maxIssuesPerDay |
|-------------------|------|-----------------|
| 1 小时 | 24 | 26 |
| 2 小时 | 12 | 14 |
| 4 小时 | 6 | 8 |
| 8 小时 | 3 | 5 |
| 24 小时 | 1 | 3 |

---

## 🏗️ MCP 集成说明

OAuth 组件设计支持 [Model Context Protocol (MCP)](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization) 鉴权场景：

| MCP 角色 | OAuth 2.1 角色 | 使用模块 |
|----------|---------------|----------|
| MCP Server | Protected Resource Server | oauth-core + 网关 Filter |
| MCP Client | OAuth Client | oauth-authz（规划中） |
| Authorization Server | Token Endpoint + Authz Endpoint | oauth-core + oauth-authz |

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
import com.richie.contract.gateway.model.OAuth2Constants;
import com.richie.contract.exception.BusinessException;

try {
    tokenEndpoint.generateToken(clientId, clientSecret, ip);
} catch (BusinessException e) {
    String errorCode = e.getCode();
    if (OAuth2Constants.ERROR_INVALID_CLIENT.equals(errorCode)) {
        // 客户端认证失败
    } else if (OAuth2Constants.ERROR_RATE_LIMIT_EXCEEDED.equals(errorCode)) {
        // 超过每日签发限制
    }
}
```

---

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

实现 `TokenStore` SPI 接口，并通过 `@Bean` 覆盖默认实现。详见 [TokenStore SPI 扩展设计](./docs/oauth-component-design.md#25-tokenstore-spi-扩展设计)。

### 4) access_token 和 refresh_token 的区别？

| | Access Token | Refresh Token |
|---|---|---|
| 格式 | JWT（自包含） | 随机字符串（需存储验证） |
| 有效期 | 短期（默认 2 小时） | 长期（默认 30 天） |
| 验证方式 | JWT 签名 + 黑名单 | Redis 物理存储验证 |
| 撤销方式 | 加入黑名单 | 物理删除 |
| IP 绑定 | 可选 | 可选 |

### 5) oauth-authz 和 oauth-dcr 什么时候可用？

目前处于规划阶段，预计支持：
- **oauth-authz**：Authorization Code + PKCE（OAuth 2.1 强制）、AS Metadata
- **oauth-dcr**：Dynamic Client Registration (RFC 7591)、Client Metadata

具体实现时间取决于业务需求优先级。

---

## 📚 相关文档

| 文档 | 说明 |
|------|------|
| [系统设计文档](./docs/oauth-component-design.md) | 完整架构设计、模块划分、时序图 |
| atlas-richie-gateway-service | 网关服务（组件消费者） |
| atlas-richie-component | 组件库总览 |

---

## 📎 🗺️ Roadmap

| 版本 | 模块 | 功能 |
|------|------|------|
| 1.0 | oauth-core | client_credentials + refresh_token + JWT Token |
| 规划中 | oauth-authz | Authorization Code Grant + PKCE + AS Metadata |
| 规划中 | oauth-dcr | Dynamic Client Registration (RFC 7591) |
| 规划中 | oauth-core（扩展） | authorization_code grant type 支持、Resource Parameter (RFC 8707) |
