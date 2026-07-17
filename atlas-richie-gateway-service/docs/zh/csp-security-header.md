# CSP（Content-Security-Policy）安全响应头配置说明

## 概述

CSP（Content-Security-Policy）是 W3C 定义的 HTTP 响应头安全机制，用于控制浏览器可加载和执行的资源来源，是防御 XSS（跨站脚本攻击）和数据注入攻击的深度防御层。

本网关在**网关层**为所有 API 响应注入 CSP 头。SPA HTML 页面由前端反向代理（Nginx/ALB）直接响应，需额外配置。详见下文"两层架构"。

---

## 两层架构

```
┌─────────────────────────────────────────────────┐
│                   客户端浏览器                      │
└──────────┬──────────────────────────┬────────────┘
           │                          │
           ▼                          ▼
┌──────────────────┐     ┌────────────────────────┐
│  Nginx / ALB     │     │  Richie Gateway        │
│  响应 SPA HTML   │     │  代理 API 请求          │
│  → CSP 头 (需配) │     │  → CspFilter 注入 CSP  │
└──────────────────┘     └───────────┬────────────┘
                                     │
                                     ▼
                            ┌──────────────────┐
                            │  后端服务          │
                            │  Service-A/B/C    │
                            └──────────────────┘
```

| 层 | 负责内容 | CSP 注入方式 | 配置入口 |
|----|---------|-------------|---------|
| **代理层 (Nginx/ALB)** | SPA HTML 页面及静态资源（不经过网关） | Nginx 配置 `add_header Content-Security-Policy` | `proxyCspConfigured` 确认标识 |
| **网关层 (Gateway)** | 所有 API 响应（`/api/**` 等） | `CspFilter` Spring Cloud Gateway 过滤器 | `platform.gateway.csp` 配置 |

> 两层必须同时覆盖，缺一不可。网关的 CSP 只保护 API 响应，不保护由 Nginx 直接响应的 HTML。

---

## 配置方式

### 1. Nacos 配置中心

在 Nacos 中找到 `platform-gateway.yaml`（或 Nacos 中对应 Data ID `platform-gateway.yaml` 的配置），添加以下内容：

```yaml
platform:
  gateway:
    csp:
      # 是否启用 CSP 过滤器（默认：false）
      enable: true

      # CSP 策略字符串，遵循 W3C CSP Level 2 规范
      # 留空或 null 时即使 enable=true 也不会注入 header
      policy: "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self'; frame-src 'self'; frame-ancestors 'none'; base-uri 'self'"

      # 上游代理（Nginx/ALB）是否已配置 CSP 响应头（默认：false）
      # 确认 Nginx/ALB 已配置后设此值为 true 以关闭启动告警
      proxy-csp-configured: true
```

### 2. 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `platform.gateway.csp.enable` | `boolean` | `false` | 是否启用 CSP 过滤器。生产环境建议开启 |
| `platform.gateway.csp.policy` | `String` | `null` | CSP 策略字符串。遵循 W3C CSP Level 2 规范 |
| `platform.gateway.csp.proxy-csp-configured` | `boolean` | `false` | 上游代理是否已配置 CSP。仅用于关闭启动告警，不影响过滤器行为 |

### 3. 快速启用

```yaml
# 最小化启用配置（使用默认策略）
platform:
  gateway:
    csp:
      enable: true
      policy: "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self'; frame-src 'self'; frame-ancestors 'none'; base-uri 'self'"
      proxy-csp-configured: false  # 确认 Nginx/ALB 已配后改为 true
```

---

## 策略参考

### 管理端 SPA 推荐策略（默认值）

```
default-src 'self';
script-src 'self' 'unsafe-inline';
style-src 'self' 'unsafe-inline';
img-src 'self' data: https:;
font-src 'self' data:;
connect-src 'self';
frame-src 'self';
frame-ancestors 'none';
base-uri 'self'
```

### 指令说明

| 指令 | 含义 | 注意事项 |
|------|------|---------|
| `default-src 'self'` | 所有资源默认只允许同源加载 | 兜底策略，未显式指定的资源类型均适用 |
| `script-src 'self' 'unsafe-inline'` | 脚本只允许同源 + 内联 | `unsafe-inline` 是 Angular 构建所需 |
| `style-src 'self' 'unsafe-inline'` | 样式只允许同源 + 内联 | `unsafe-inline` 是 PrimeNG 动��样式必须 |
| `img-src 'self' data: https:` | 图片允许同源 + data URI + HTTPS | HTTPS 是 Grafana 嵌入图片所需 |
| `font-src 'self' data:` | 字体允许同源 + data URI | PrimeIcons 图标字体通过 data: 加载 |
| `connect-src 'self'` | XHR/fetch 只允许同源 | 阻止 API 请求外泄 |
| `frame-src 'self'` | iframe 只允许同源 | Grafana 仪表盘嵌入需放宽为 `frame-src 'self' https://grafana.xxx.com` |
| `frame-ancestors 'none'` | 禁止页面被嵌入 iframe | 防御点击劫持（Clickjacking）。如需要可改为 `'self'` |
| `base-uri 'self'` | 限制 `<base>` 标签为目标同源 | 防御 base-uri 注入攻击 |

### 常见放宽场景

#### 嵌入 Grafana 仪表盘

```yaml
policy: "...; frame-src 'self' https://grafana.example.com; ..."
```

#### 嵌入第三方工具

```yaml
policy: "...; frame-src 'self' https://grafana.example.com https://sentry.example.com; ..."
```

#### 需要加载外部 CDN 资源

```yaml
policy: "...; script-src 'self' 'unsafe-inline' https://cdn.example.com; ..."
```

---

## 启动自检

当 `enable=true` 但 `proxyCspConfigured=false` 时，`CspFilter` 的 `@PostConstruct` 方法会在启动时输出 **WARN** 级别日志：

```
WARN  CspFilter - CSP 过滤器已启用 (platform.gateway.csp.enable=true)，
但 proxyCspConfigured=false。SPA HTML 页面由 Nginx/ALB 直接响应（不经过网关），
需要在其配置中额外设置 Content-Security-Policy 响应头。确认配置后请设置
platform.gateway.csp.proxy-csp-configured=true 以关闭此警告。
```

此自检不影响正常运行，仅作为运维提醒。

---

## 部署检查清单

部署前逐项确认：

- [ ] `platform.gateway.csp.enable=true` —— 网关层已启用 CSP
- [ ] `platform.gateway.csp.policy` 已配置有效策略 —— 非空字符串
- [ ] `platform.gateway.csp.proxy-csp-configured=true` —— 确认 Nginx/ALB 已配
- [ ] Nginx/ALB 已添加 `add_header Content-Security-Policy "..." always;` —— HTML 页面覆盖
- [ ] 上线后打开浏览器 DevTools → Network → 查看任意 API 响应头含 `content-security-policy`
- [ ] 上线后打开浏览器 DevTools → Network → 查看 HTML 页面响应头含 `content-security-policy`
- [ ] 对 `report-uri` / `report-to` 已考虑是否需要上报违规（详见"监控与上报"）

---

## 监控与上报

CSP 支持通过 `report-uri` 或 `report-to` 指令收集违规报告。生产环境建议开启：

```yaml
policy: "default-src 'self'; ...; report-uri /api/security/csp/report"
```

网关作为一个独立服务，建议在后端实现一个 CSP 报告接收端点（`POST /api/security/csp/report`），将违规上报写入日志或发送至 Sentry/Grafana。

### 仅报告不拦截（评估模式）

在首次上线或调整策略时，建议使用 `Content-Security-Policy-Report-Only` 头进行灰度评估：

```yaml
policy: "...; report-uri /api/security/csp/report"
```

修改 `CspFilter` 的 `doFilter` 方法，将 header name 从 `Content-Security-Policy` 改为 `Content-Security-Policy-Report-Only`，确认无违规后再切换为强制模式。

---

## 技术实现

- **配置类**：`CspFilterConfig`（位于 `atlas-richie-gateway-service` 模块 `config/` 包）
  - 注解：`@ConfigurationProperties(prefix = "platform.gateway.csp")` + `@RefreshScope`
  - Nacos 动态刷新，不支持运行时切换
- **过滤器类**：`CspFilter`（位于 `atlas-richie-gateway-service` 模块 `filter/common/security/` 包）
  - 继承：`AbstractBaseFilter`（`GlobalFilter` + `Ordered` 的封装基类）
  - 排序：`FilterOrder.CSP_FILTER` → order `-850`，`getBusinessLayer()` 返回"基础设施层"，`getPositionInLayer()` 返回 `3`
  - 行为：当 `enable=true` 且 `policy` 非空时，调用 `exchange.getResponse().getHeaders().set("Content-Security-Policy", policy)`
  - 启用判断：`enableVerifyFilter()` 返回 `config.getCsp().isEnable()`
  - 启动自检：`@PostConstruct checkProxyCspConfig()` —— 输出 WARN 日志提醒 SPA HTML 层的 CSP 配置
- **过滤器顺序**：`FilterOrder` 枚举类中 `CSP_FILTER(-850, "CSP 安全头过滤器")`
  - 处于基础设施层（Layer 3），在所有业务过滤器之前执行
- **动态刷新**：配置通过 `@RefreshScope` 支持 Nacos 动态刷新，修改后无需重启

---

## 注意事项

1. **两层覆盖，缺一不可**：CSP 策略必须在网关和 Nginx/ALB 两侧同时部署，才能完整覆盖 API 和 HTML 的响应安全
2. **慎用 `unsafe-inline`**：Angular 和 PrimeNG 的运行时需要内联脚本和样式，生产环境如有更严格的安全要求，可考虑使用 nonce 或 hash 策略代替
3. **`report-uri` 端点保护**：CSP 报告端点会接收浏览器 POST 数据，应做好限流和鉴权，避免被滥用
4. **灰度上线**：首次上线使用 `Content-Security-Policy-Report-Only` 模式（CspFilter.doFilter 中将 header name 改为该值），观察一周无违规后再切换为强制模式
5. **Grafana 嵌入**：如管理端需嵌入 Grafana 仪表盘，必须将 `frame-src` 放宽为 `frame-src 'self' https://grafana.example.com`
6. **策略不可过于严格**：CSP 策略过严可能导致管理端功能异常，调整后务必回归测试主要业务流
