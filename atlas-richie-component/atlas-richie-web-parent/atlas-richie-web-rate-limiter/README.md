# atlas-richie-web-rate-limiter

可选的 Servlet 业务限流模块。它依赖 `atlas-richie-cache` 的 `LimiterOps`，使用 Redis 原子固定窗口计数；
因此 `atlas-richie-web-core` 本身不再携带 Cache 依赖或为每个用户/路由创建 JVM 本地限流器。

## 引入与配置

```xml
<dependency>
    <groupId>cn.richie696.component</groupId>
    <artifactId>atlas-richie-web-rate-limiter</artifactId>
</dependency>
```

```yaml
platform:
  component:
    web:
      filter:
        key-header: X-Client-Id
      rate-limit:
        enabled: true
        key-prefix: richie:web:rate-limit
        window-seconds: 1
        require-gateway-identity: true
        routes:
          - pattern: /api/v1/orders/**
            permits-per-second: 5
```

只匹配 `routes` 的接口会被限流。配额 key 为 `key-prefix:clientKey:routePattern`，因此多个 Pod 共享同一 Redis 计数，
且不会在每个 Pod 中创建按用户增长的本地桶或调度线程。

默认要求 `X-Forwarded-From-Gateway`，并由 `X-Client-Id` 取得用户 key。Gateway 必须在完成 JWT 校验后覆盖写入
`X-Client-Id` / `X-User-Id`，业务服务必须仅通过内网 Service 暴露；否则客户端可伪造这些内部 header。对不经过
Gateway 的受控内部调用，才可显式设置 `require-gateway-identity: false` 并提供自己的可信身份传递方式。

`window-seconds` 是固定窗口长度。Redis key 首次成功计数时设置 TTL，后续请求不会续期。
