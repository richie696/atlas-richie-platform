# atlas-richie-oauth-test

OAuth Server、Gateway 和 OAuth 组件共用的测试支撑模块。该模块只用于测试代码，生产服务不应以运行时依赖引入。

## 提供的能力

| 类别 | 入口 | 用途 |
|---|---|---|
| 测试数据 | `OAuthTestFixtures` | Client、OAuth/OIDC Authorization Code + PKCE、Token Request 的标准夹具 |
| HTTP 参数 | `OAuthTestHttp` | OAuth query/form 参数构造、回调参数解析、Bearer Header |
| 黑盒 HTTP | `OAuthTestHttpClient` | 不自动跟随重定向的 authorize/token/introspect/revoke 请求 |
| 断言 | `OAuthTestAssertions` | Token、introspection、授权回调、OAuth error 的无框架断言 |
| 签名密钥 | `OAuthTestKeyMaterial` | 临时 RSA 密钥、JWT、Static JWK Source、JWKS JSON、PEM |
| DPoP 测试材料 | `OAuthTestDpopMaterial` | ES256 公钥、`cnf.jkt`、`ath` 和 proof 生成 |
| 端点约定 | `OAuthTestEndpoints` | 默认标准端点路径，也允许 AS 按自身路由重建 |
| Redis 集成 | `AbstractOAuthServerRedisIntegrationTest` | Testcontainers/外部 Redis、Spring 属性注入、`it:*` 数据清理 |

## OAuth Server 集成测试用法

服务工程只需要依赖测试作用域：

```xml
<dependency>
    <groupId>cn.richie696.component</groupId>
    <artifactId>atlas-richie-oauth-test</artifactId>
    <scope>test</scope>
</dependency>
```

黑盒 HTTP 测试：

```java
var fixture = OAuthTestFixtures.defaultAuthorizationCodeRequest("read");
var client = new OAuthTestHttpClient(URI.create("http://localhost:8080"));

var response = client.authorize("/oauth2/authorize", fixture.request());
// OAuth Server 登录/同意完成后，检查 response.headers().firstValue("location")
```

Redis + Spring 集成测试：

```java
@SpringBootTest
class OAuthAuthorizationServerIT extends AbstractOAuthServerRedisIntegrationTest {
}
```

该基类不会声明 `@SpringBootTest`，因为 OAuth Server 的启动类属于独立服务工程，组件不能反向依赖服务。默认优先使用 Redis Testcontainers；没有 Docker 时测试会自动跳过。若使用已有 Redis，可设置：

```text
OAUTH_IT_USE_EXTERNAL=true
REDIS_IT_HOST=127.0.0.1
REDIS_IT_PORT=6379
```

## 测试边界

本模块不包含 OAuth Server 的 Controller、用户数据库、MFA、管理后台或业务数据初始化。它只提供协议测试所需的稳定测试材料；服务工程仍需自行编写登录、同意页、客户端管理、MFA 和持久化的集成用例。
