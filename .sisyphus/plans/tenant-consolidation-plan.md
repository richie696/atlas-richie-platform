# 租户内聚实施计划

## 架构决策

| 决策项 | 结论 |
|--------|------|
| JWT tenant claim | `tenantId` (Long)，不兼容旧 token，用户需重新登录 |
| Gateway 是否保留 filter | 是，编译期依赖 tenant 组件 |
| `TenantAware`/`TenantDomain`/`TenantAuditDomain` | 留在 `context` 模块，是被动契约 |
| Tenant 组件包名 | `com.richie.component.tenant` |
| Tenant 组件 artifactId | `atlas-richie-component-tenant` |
| 租户传递方式 | JWT `tenantId` claim + gateway 放入 `X-Tenant-Id` header → 下游各服务拦截器读取 |
| 上下文传递 | `TransmittableThreadLocal<Long>`（Ali TTL），支持线程池传递 |

---

## 总体架构

```
gateway-service (依赖 tenant-component)
  TenantFilter:
    → TenantResolver.resolve(exchange) → Long tenantId
    → 将 tenantId 写入响应头 X-Tenant-Id
    → 下游 Feign/RestClient 自动透传 header

microservice (依赖 tenant-component，自动生效)
  TenantContextInitializer:
    → 从 X-Tenant-Id header 读取 tenantId
    → 存入 TenantContextHolder (TTL<Long>)

  TenantMetaObjectHandler (MyBatis-Plus):
    → insertFill: 从 TenantContextHolder 获取 tenantId，填充 entity.tenantId

  MongoTenantHandler:
    → 从 TenantContextHolder 获取 tenantId，替换旧的 TenantContext (ThreadLocal)

  MFA / Logging / Messaging:
    → 全部改为从 TenantContextHolder 读取 tenantId
```

---

## 阶段划分与依赖关系

```
Phase 1 ────────────────────┐
  Core 组件 (TenantContext   │
  Holder, TenantPrincipal,   │
  TenantResolver SPI)        │
─────────────────────────────┘
          │
          ▼
Phase 2 ────────────────────┐
  JWT/Resolver (TenantJwt    │
  Resolver, claim 处理)      │
─────────────────────────────┘
          │
          ▼
Phase 3 ────────────────────┐
  Contract/Context 清理      │
  (LoginUserPrincipal,       │
   JwtUtils, LoginUserCtx)   │
─────────────────────────────┘
          │
          ▼
Phase 4 ────────────────────┐
  Gateway 重构               │
  (TenantFilter,             │
   IssueTokensFilter,        │
   AuthenticationFilter,     │
   SignatureServiceImpl)     │
─────────────────────────────┘
          │
          ├──────────────────┬───────────────────┬──────────────────┐
          ▼                  ▼                   ▼                  ▼
Phase 5a ────┐    Phase 5b ────┐    Phase 5c ────┐    Phase 5d ────┐
Microservice   MongoDB 迁移     MFA 改造           Logging/          │
拦截器集成      (TenantContext   (TenantContext    Messaging/Web     │
(TenantCtxInit) 旧 → 新)         Holder 接入)      改造              │
───────────────┘ ───────────────┘ ───────────────┘                 │
          │                                                         │
          └─────────────────────────────────────────────────────────┘
          │
          ▼
Phase 6 ────────────────────┐
  DAO/MyBatis-Plus 插件     │
  (TenantInterceptor)        │
─────────────────────────────┘
          │
          ▼
Phase 7 ────────────────────┐
  集成测试 + 验证            │
─────────────────────────────┘
```

---

## Phase 1：Core Tenant Component 基础

### 1.1 创建 TenantPrincipal

**文件**: `atlas-richie-component-tenant/src/main/java/com/richie/component/tenant/model/TenantPrincipal.java`

```java
package com.richie.component.tenant.model;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 租户主体信息
 * 独立于 LoginUserPrincipal，tenant 与 user 是正交概念
 */
@Data
public class TenantPrincipal implements Serializable {
    /** 租户 ID（Long，DB 主键） */
    private Long tenantId;
    /** 租户展示名称（用于日志、审计展示） */
    private String tenantName;
    /** 租户过期时间 */
    private OffsetDateTime expiredTime;
}
```

### 1.2 创建 TenantContextHolder

**文件**: `atlas-richie-component-tenant/src/main/java/com/richie/component/tenant/context/TenantContextHolder.java`

职责：
- 使用 `TransmittableThreadLocal<TenantPrincipal>` 存储当前租户
- 提供 `set(TenantPrincipal)`, `get()`, `getTenantId()`, `getTenantName()`, `clear()`, `require()` 方法
- `getTenantId()` 返回 `Long`
- `require()` 在未设置时抛 `IllegalStateException`

### 1.3 创建 TenantResolver SPI

**文件**: `atlas-richie-component-tenant/src/main/java/com/richie/component/tenant/resolver/TenantResolver.java`

```java
package com.richie.component.tenant.resolver;

import com.richie.component.tenant.model.TenantPrincipal;

/**
 * 租户解析器 SPI
 * 由各接入方（gateway、auth service）实现
 */
@FunctionalInterface
public interface TenantResolver {
    /**
     * 解析当前请求/线程的租户信息
     * @return TenantPrincipal，null 表示无租户上下文
     */
    TenantPrincipal resolve();
}
```

### 1.4 创建 TenantJwtResolver（默认实现）

**文件**: `atlas-richie-component-tenant/src/main/java/com/richie/component/tenant/resolver/impl/TenantJwtResolver.java`

职责：
- 从 JWT token 中读取 `tenantId` claim (Long) 和 `tenantExpiredTime` claim
- 组装为 `TenantPrincipal` 返回
- 与 JwtUtils 无依赖，直接使用 JWT 解析库（jjwt 等）操作 claims
- 注意：当前项目的 JWT 是自定义格式（非标准 jjwt），需要适配已有 JWT 逻辑

### 1.5 创建 TenantAutoConfiguration

**文件**: `atlas-richie-component-tenant/src/main/java/com/richie/component/tenant/config/TenantAutoConfiguration.java`

职责：
- 注册 `TenantContextHolder` 为 Spring Bean
- 注册 `TenantJwtResolver`（如果没有自定义 TenantResolver）
- 注册 `TenantContextInitializer`（拦截器，读取 X-Tenant-Id header → TenantContextHolder）

### 1.6 创建 TenantContextInitializer（Web 拦截器）

**文件**: `atlas-richie-component-tenant/src/main/java/com/richie/component/tenant/web/TenantContextInitializer.java`

职责：
- 实现 `HandlerInterceptor` 或类似机制
- preHandle: 从 `X-Tenant-Id` header 读取 tenantId (String) → 转换为 Long → 设置到 `TenantContextHolder`
- afterCompletion: 调用 `TenantContextHolder.clear()`
- 如果 header 为空，不设置（允许非租户场景）

### 1.7 AutoConfiguration imports & pom.xml

- `pom.xml`: 依赖 `atlas-richie-component-dao`（获取 MyBatis-Plus 依赖）
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
  ```
  com.richie.component.tenant.config.TenantAutoConfiguration
  ```

### 1.8 测试

- `TenantContextHolderTest`: 单线程 set/get/clear，跨线程 TTL 传播
- `TenantJwtResolverTest`: Token 解析 tenantId claim
- `TenantContextInitializerTest`: Header → Context 映射

---

## Phase 2：Contract/Context 清理

### 2.1 LoginUserPrincipal 瘦身

**文件**: `atlas-richie-contract/.../LoginUserPrincipal.java`

- 删除 `tenantCode` 字段（含 Javadoc）
- 删除 `tenantExpiredTime` 字段
- 注意：`@Data` 会再生 `equals()`/`hashCode()`/`toString()`，确认无子类依赖这些字段

### 2.2 JwtUtils 删除 tenant 方法

**文件**: `atlas-richie-context/.../JwtUtils.java`

删除方法：
- `getTenantCode(String token)` → 整方法删除
- `getTenantExpiredTime(String token)` → 整方法删除
- `getTenantCodeByToken(HttpServletRequest)` → 整方法删除
- `verify(String token, String tenantCode, String username, String secret)` → 删除 tenantCode 相关的重载

保留：
- `verify(String token, String secret)` → 无 tenant 参数的基础校验继续保留
- `getUsername(String token)` → 保留
- `getArgument(String token, String key)` → 保留
- `getExpiredTime(String token)` → 保留
- `generateJwtToken(String username, String secret, long expiredTime)` → 保留（去掉 tenant 参数的重载）
- `generateJwtToken(LoginUserPrincipal userVO, String secret, long expiredTime)` → 保留（但 LoginUserPrincipal 去掉了 tenantCode，签名不变，内部去掉 tenantCode 的写入）

### 2.3 LoginUserContextHolder 删除 tenant 方法

**文件**: `atlas-richie-context/.../LoginUserContextHolder.java`

- 删除 `getTenantCode()` 方法
- 删除 `getTenantId()` 方法
- 保留 `setUserInfo()`、`getUserInfo()`、`setToken()`、`getToken()`、`clear()`
- 方法签名不变（使用泛型 `T extends LoginUserPrincipal`），LoginUserPrincipal 只是少了字段

### 2.4 GlobalConstants 调整

**文件**: `atlas-richie-contract/.../GlobalConstants.java`

- 保留 `X_TENANT_CODE_TOKEN`（兼容旧 header，但逐步下线）
- 新增 `String X_TENANT_ID = "x-rd-request-tenantid"`（新标准 header）

### 2.5 编译验证

- `mvn compile` 确认所有引用 LoginUserPrincipal 的模块能通过（字段被删了，但没有子类直接访问 `.tenantCode` 的话，编译不会挂——Lombok 生成的 getter/setter 在编译期不存在，删除字段不会编译报错，但运行时调用方会挂）
- 搜索所有 `.getTenantCode()`、`.getTenantExpiredTime()`、`.setTenantCode()`、`.setTenantExpiredTime()` 的调用，标记为需要在 Phase 4/5 处理

---

## Phase 3：Gateway 重构

### 3.1 TenantFilter 改造

**文件**: `atlas-richie-gateway-service/.../TenantFilter.java`

当前：
```java
String tenantCode = JwtUtils.getTenantCode(token);
OffsetDateTime tenantExpiredTime = JwtUtils.getTenantExpiredTime(token);
```

改为：
```java
TenantResolver resolver = ... // 注入 TenantJwtResolver
TenantPrincipal tenant = resolver.resolve();
if (tenant == null || tenant.getTenantId() == null) { reject; }
// 校验 tenant.getExpiredTime()
// 将 tenantId 写入响应头，供下游各服务使用
exchange.getResponse().getHeaders().set("X-Tenant-Id", String.valueOf(tenant.getTenantId()));
```

注意：Reactive（WebFlux）环境中的 ThreadLocal 问题。Gateway 基于 Spring Cloud Gateway（Reactive），不能直接用 `TransmittableThreadLocal`。解决方案：
- 在 Gateway 层面不设置 ThreadLocal，只做校验和 header 透传
- 真正的 Context 设置在**下游服务**的 Servlet 拦截器中完成

### 3.2 IssueTokensFilter 改造

**文件**: `atlas-richie-gateway-service/.../IssueTokensFilter.java`

当前：
- `String tenantId = userVO.getTenantCode();` → 删除
- MFA 校验时传入 `tenantId` → 改为从别处获取（如登录响应 header）
- SSO key `key = "%s-".formatted(userVO.getTenantCode());` → 删除 tenantCode 拼接

改动点：
- 不再从 `LoginUserPrincipal` 读取 tenantCode
- MFA 流程所需的 tenantId 改为从登录服务响应 header 获取，或由 auth 服务在响应体中返回
- `storeUserInfoToCache` 的 cache key 不再包含 tenantCode（或改为由 tenant 组件提供统一的 key 构建）
- JWT 签发调用 `JwtUtils.generateJwtToken(userVO, secret, expiredTime)` → 内部不再写入 tenantCode claim

### 3.3 AuthenticationFilter 改造

**文件**: `atlas-richie-gateway-service/.../AuthenticationFilter.java`

当前：
```java
String tenantCode = JwtUtils.getTenantCode(token);
key = tenantCode + "-" + username;
```

改为：
```java
// SSO cache key 不再使用 tenantCode
key = username;
// 或使用 tenant 组件提供的 KeyBuilder
```

### 3.4 SignatureServiceImpl 改造

**文件**: `atlas-richie-gateway-service/.../SignatureServiceImpl.java`

当前：
- `createSignature(ApiResult<LoginUserPrincipal> result)`：使用 `JwtUtils.generateJwtToken(data, secret, expiredTime)`，内部写入了 userVO.getTenantCode() → JWT claim
- `logout()` 中 `tenantId = JwtUtils.getTenantCode(accessToken)` → 删除
- `notifyTenantExpired(String tenantCode)` → 签改为 Long tenantId

---

## Phase 4：Microservice 集成

### 4.1 TenantContextInitializer 注册

- 在 tenant 组件的 `TenantAutoConfiguration` 中注册 `TenantContextInitializer`（HandlerInterceptor）
- 从 `X-Tenant-Id` header 读取 tenantId
- 设置到 `TenantContextHolder`
- 仅 Servlet 环境生效（`@ConditionalOnWebApplication(type = SERVLET)`）

### 4.2 HeaderAspectInterceptor 调整

**文件**: `atlas-richie-component-microservice/.../HeaderAspectInterceptor.java`

- `postHandle()` 中的 `X_TENANT_CODE_TOKEN` 写回响应→**保留**（兼容旧 header）
- 新增 `X_TENANT_ID` 的写回响应逻辑

---

## Phase 5：MongoDB 迁移

### 5.1 删除旧的 TenantContext，全部改为 TenantContextHolder

**文件**: `atlas-richie-component-mongodb/.../TenantContext.java`

- 直接**删除**此文件（无人在用，无需兼容）

### 5.2 TenantHandler 改造

**文件**: `atlas-richie-component-mongodb/.../TenantHandler.java`

- 删除对 `TenantContext` 的全部引用
- 改为调用 `TenantContextHolder.getTenantId()`（Long）
- 填入 Query/insert 时 Long → String 转换（MongoDB 内部仍存 String）

### 5.3 QueryBuilder/DeleteBuilder 调整

- 删除对 `TenantContext` 的 import 和调用
- 改为调用 `TenantContextHolder.getTenantId()`

---

## Phase 6：MFA 改造

### 6.1 MfaTenantSupport 改造

**文件**: `atlas-richie-component-mfa/.../MfaTenantSupport.java`

- 新增读取 `TenantContextHolder.getTenantId()` 获取当前租户
- `isTenantEnabled()` 逻辑不变（仍读取配置）

### 6.2 MFA Cache Key 改造

- 所有 cache key 中 `tenantId` 改为 `TenantContextHolder.getTenantId()`（返回 Long，toString 后拼入 key）
- MFA 方法签名中将 `String tenantId` 改为 `Long tenantId`

### 6.3 MFA Entity 改造

- `mfa_user_info.tenant_id` 字段类型改为 `BIGINT`
- 对应 entity 字段改为 `Long tenantId`

---

## Phase 7：Logging/Messaging/Web 改造

### 7.1 AccessLogAspect

**文件**: `atlas-richie-component-logging/.../AccessLogAspect.java`

- 原来从 JWT 直接解析 tenantCode → 改为从 `TenantContextHolder.getTenantId()` 获取
- 两种场景：
  1. 请求过程中 → `TenantContextHolder.getTenantId()` 已有值
  2. 特殊场景（如从响应报文反查） → 改为业务层自行提供

### 7.2 MessageServiceImpl

**文件**: `atlas-richie-component-messaging/.../MessageServiceImpl.java`

- `getMessage()` header 中的 `X_TENANT_CODE_TOKEN` 改为从 `TenantContextHolder` 获取 `tenantId`，写入 `X-Tenant-Id` header
- 兼容：同时保留 `X_TENANT_CODE_TOKEN`（旧消费者不受影响）

### 7.3 AbstractBaseConsumer

- 读取 `X-Tenant-Id` header → `TenantContextHolder.set()`

### 7.4 WebMvcConfiguration

**文件**: `atlas-richie-component-web/.../WebMvcConfiguration.java`

- 删除 `JwtUtils.getTenantCode(token)` 在时区 key 中的使用
- 改为从 `TenantContextHolder.getTenantId()` 获取租户信息

---

## Phase 8：DAO/MyBatis-Plus 租户拦截器

### 8.1 TenantMetaObjectHandler

**文件**: `atlas-richie-component-tenant/.../handler/TenantMetaObjectHandler.java`

- 实现 `MetaObjectHandler`
- `insertFill`: 检测 entity 是否实现 `TenantAware` → 是则从 `TenantContextHolder.getTenantId()` 填充 `setTenantId()`
- 仅在 classpath 上有 MyBatis-Plus 时生效（`@ConditionalOnClass`）

### 8.2 MyBatis-Plus 租户插件（可选）

如果要做到查询时自动拼接 `WHERE tenant_id = ?` 条件，需要 MyBatis-Plus 的 `TenantLineInnerInterceptor`。

但当前 DAO 组件没有这个功能，建议后续根据实际需求决定是否增加。当前阶段先做到自动填充（insert），不做查询过滤。

---

## Phase 9：集成测试与验证

### 9.1 验证清单

- [ ] `TenantContextHolder` 单线程 set/get/clear 正确
- [ ] `TenantContextHolder` 跨线程（`CompletableFuture`, `@Async`）传递正确
- [ ] `TenantJwtResolver` 正确解析 JWT 中的 `tenantId` claim
- [ ] Gateway `TenantFilter` 正确校验 tenantId 并设置 `X-Tenant-Id` header
- [ ] `TenantContextInitializer` 正确读取 header 并设置 `TenantContextHolder`
- [ ] MongoDB `TenantHandler` 从新 `TenantContextHolder` 获取 tenant
- [ ] MFA cache key 使用 `Long tenantId`
- [ ] AccessLogAspect 不从 JWT 直接读取 tenant
- [ ] 所有模块 `mvn compile` 通过
- [ ] 所有现有测试通过

### 9.2 兼容性注意

- **旧 JWT Token 全部失效**：用户需要重新登录获取新 token
- **旧 header `X-TENANT-CODE-TOKEN`**：保留解析逻辑（向后兼容），但值为 Long 字符串
- **MongoDB 旧 TenantContext 代码**：标记 `@Deprecated`，保留一个发布周期

### 9.3 回滚方案

回滚到 `tenantCode` 基线：
1. `git revert` 全部 tenant 相关 commit
2. 数据库回滚（MFA tenant_id 类型改回 VARCHAR）
3. 通知用户重新登录（token 格式回退）
