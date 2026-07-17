# CSP (Content-Security-Policy) Security Header Configuration

## Overview

Content-Security-Policy (CSP) is a W3C-standardized HTTP response header mechanism that controls which resources the browser can load and execute. It serves as a critical defense-in-depth layer against XSS (Cross-Site Scripting) and data injection attacks.

This gateway injects CSP headers into **all API responses** at the gateway layer. SPA HTML pages are served directly by the front-end reverse proxy (Nginx/ALB) and require separate configuration. See "Two-Layer Architecture" below.

---

## Two-Layer Architecture

```
┌─────────────────────────────────────────────────┐
│                 Client Browser                    │
└──────────┬──────────────────────────┬────────────┘
           │                          │
           ▼                          ▼
┌──────────────────┐     ┌────────────────────────┐
│  Nginx / ALB     │     │  Richie Gateway        │
│  Serves SPA HTML │     │  Proxies API requests  │
│  → CSP header    │     │  → CspFilter injects   │
│    (manual cfg)  │     │    CSP header           │
└──────────────────┘     └───────────┬────────────┘
                                     │
                                     ▼
                            ┌──────────────────┐
                            │  Backend Services │
                            │  Service-A/B/C    │
                            └──────────────────┘
```

| Layer | Content | CSP Injection | Config Entry |
|-------|---------|---------------|--------------|
| **Proxy (Nginx/ALB)** | SPA HTML & static assets (bypass gateway) | Nginx `add_header Content-Security-Policy` | `proxyCspConfigured` confirmation flag |
| **Gateway** | All API responses (`/api/**` etc.) | `CspFilter` Spring Cloud Gateway filter | `platform.gateway.csp` config |

> Both layers must be covered — neither alone is sufficient. The gateway CSP protects only API responses, not HTML served directly by Nginx.

---

## Configuration

### 1. Via Nacos Configuration Center

Edit `platform-gateway.yaml` in Nacos:

```yaml
platform:
  gateway:
    csp:
      # Enable CSP filter (default: false)
      enable: true

      # CSP policy string, W3C CSP Level 2 compliant
      # When null or empty, no header is injected even if enable=true
      policy: "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self'; frame-src 'self'; frame-ancestors 'none'; base-uri 'self'"

      # Whether the upstream proxy (Nginx/ALB) already has CSP configured (default: false)
      # Set to true after confirming Nginx/ALB CSP is in place to silence startup warnings
      proxy-csp-configured: true
```

### 2. Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `platform.gateway.csp.enable` | `boolean` | `false` | Enable CSP filter. Recommended `true` for production |
| `platform.gateway.csp.policy` | `String` | `null` | CSP policy string, W3C CSP Level 2 compliant |
| `platform.gateway.csp.proxy-csp-configured` | `boolean` | `false` | Whether Nginx/ALB has CSP configured. Only silences startup warnings |

### 3. Quick Start

```yaml
platform:
  gateway:
    csp:
      enable: true
      policy: "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self'; frame-src 'self'; frame-ancestors 'none'; base-uri 'self'"
      proxy-csp-configured: false
```

---

## Policy Reference

### Recommended Default for Admin SPA

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

### Directive Notes

| Directive | Meaning | Notes |
|-----------|---------|-------|
| `default-src 'self'` | Fallback for unspecified resource types | Same-origin only by default |
| `script-src 'self' 'unsafe-inline'` | Scripts: same-origin + inline | `unsafe-inline` required by Angular's runtime |
| `style-src 'self' 'unsafe-inline'` | Styles: same-origin + inline | `unsafe-inline` required by PrimeNG dynamic styles |
| `img-src 'self' data: https:` | Images: same-origin, data URI, HTTPS | HTTPS needed for embedded Grafana images |
| `font-src 'self' data:` | Fonts: same-origin + data URI | PrimeIcons loaded via data: URIs |
| `connect-src 'self'` | XHR/fetch: same-origin only | Prevents API data exfiltration |
| `frame-src 'self'` | iframes: same-origin only | Relax for Grafana embedding: `frame-src 'self' https://grafana.example.com` |
| `frame-ancestors 'none'` | Clickjacking protection | Change to `'self'` if embedding is needed |
| `base-uri 'self'` | Restrict `<base>` to same-origin | Prevents base-uri injection attacks |

### Common Relaxations

#### Embedding Grafana

```yaml
policy: "...; frame-src 'self' https://grafana.example.com; ..."
```

#### Embedding Multiple Tools

```yaml
policy: "...; frame-src 'self' https://grafana.example.com https://sentry.example.com; ..."
```

#### External CDN Scripts

```yaml
policy: "...; script-src 'self' 'unsafe-inline' https://cdn.example.com; ..."
```

---

## Startup Self-Check

When `enable=true` but `proxyCspConfigured=false`, the `CspFilter` `@PostConstruct` method logs a **WARN** at startup:

```
WARN  CspFilter - CSP filter enabled (platform.gateway.csp.enable=true),
but proxyCspConfigured=false. SPA HTML pages are served directly by
Nginx/ALB (not through the gateway) and need a separate
Content-Security-Policy header. After confirming the proxy configuration,
set platform.gateway.csp.proxy-csp-configured=true to suppress this warning.
```

This check does not affect runtime behavior.

---

## Deployment Checklist

- [ ] `platform.gateway.csp.enable=true` — Gateway layer CSP enabled
- [ ] `platform.gateway.csp.policy` configured with a valid non-empty policy
- [ ] `platform.gateway.csp.proxy-csp-configured=true` — Nginx/ALB CSP confirmed
- [ ] Nginx/ALB has `add_header Content-Security-Policy "..." always;` — HTML page coverage
- [ ] Post-deployment: Browser DevTools → Network → verify API response `content-security-policy` header
- [ ] Post-deployment: Browser DevTools → Network → verify HTML page `content-security-policy` header
- [ ] Consider `report-uri` / `report-to` for violation reporting (see "Monitoring")

---

## Monitoring & Reporting

CSP supports violation reporting via `report-uri` or `report-to`. Production deployments should enable it:

```yaml
policy: "default-src 'self'; ...; report-uri /api/security/csp/report"
```

Implement a CSP report endpoint (`POST /api/security/csp/report`) in a backend service, logging violations or forwarding them to Sentry/Grafana.

### Report-Only Mode

For initial rollout or policy changes, use `Content-Security-Policy-Report-Only` to evaluate without blocking:

Modify `CspFilter.doFilter()` to use `Content-Security-Policy-Report-Only` as the header name. After confirming no violations, switch to enforcement mode.

---

## Technical Implementation

- **Configuration Class**: `CspFilterConfig` in `atlas-richie-gateway-service` module `config/` package
  - Annotations: `@ConfigurationProperties(prefix = "platform.gateway.csp")` + `@RefreshScope`
  - Nacos dynamic refresh (modifications take effect without restart)
- **Filter Class**: `CspFilter` in `atlas-richie-gateway-service` module `filter/common/security/` package
  - Extends: `AbstractBaseFilter`
  - Order: `FilterOrder.CSP_FILTER` → `-850`, layer: "基础设施层" (Infrastructure Layer), position: `3`
  - Behavior: injects `Content-Security-Policy` header when `enable=true` and `policy` is non-blank
  - Enable gate: `enableVerifyFilter()` returns `config.getCsp().isEnable()`
  - Self-check: `@PostConstruct checkProxyCspConfig()` — WARN log if proxy not yet configured
- **Dynamic Refresh**: Supported via `@RefreshScope` — no restart required

---

## Important Notes

1. **Both layers required**: CSP must be deployed at both Gateway and Nginx/ALB to fully cover API and HTML responses
2. **`unsafe-inline` trade-off**: Required by Angular/PrimeNG runtime. For stricter security, consider nonce- or hash-based policies
3. **`report-uri` endpoint protection**: The CSP report endpoint receives browser POST data — implement rate limiting and authentication
4. **Gradual rollout**: Use `Content-Security-Policy-Report-Only` mode first, observe for one week, then switch to enforcement
5. **Grafana embedding**: If embedding Grafana dashboards, relax `frame-src` to `frame-src 'self' https://grafana.example.com`
6. **Policy testing**: Overly strict CSP may break admin UI features — always regression-test core business flows after policy changes
