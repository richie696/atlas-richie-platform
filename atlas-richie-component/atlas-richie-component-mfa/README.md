# Atlas Richie MFA Component (atlas-richie-component-mfa)

> Enterprise-grade **multi-factor authentication** component for the Richie Platform. Implements TOTP / HOTP per RFC 6238 / 4226, with optional SMS / email channels and risk-based step-up. Uses a **split architecture**: `mfa-validation` (read-only, zero-DB) runs in the gateway; `mfa-management` (CRUD + Liquibase) runs in the general service.

---

## 📖 Contents

- [📖 Overview](#📖-overview)
  - [What this component is — and what it isn't](#what-this-component-is-—-and-what-it-isnt)
- [✨ Features](#✨-features)
  - [Core capabilities](#core-capabilities)
  - [Design choices](#design-choices)
- [🏗️ Architecture & Module Layout](#🏗️-architecture-&-module-layout)
- [🚀 Quick Start](#🚀-quick-start)
  - [1. Add the dependency](#1-add-the-dependency)
  - [2. Configure](#2-configure)
  - [3. Bind and verify](#3-bind-and-verify)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. Bind / unbind device](#1-bind-/-unbind-device)
  - [2. Code generation](#2-code-generation)
  - [3. Code verification](#3-code-verification)
  - [4. Recovery codes](#4-recovery-codes)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: Why split into validation and management?](#q1-why-split-into-validation-and-management?)
  - [Q2: Can the same MFA secret work across multiple devices?](#q2-can-the-same-mfa-secret-work-across-multiple-devices?)
  - [Q3: How does tenant detection work?](#q3-how-does-tenant-detection-work?)
  - [Q4: Can I use my own SMS provider?](#q4-can-i-use-my-own-sms-provider?)
  - [Q5: Are recovery codes one-time use?](#q5-are-recovery-codes-one-time-use?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-mfa` |
| **Category** | Identity & access — multi-factor authentication |
| **Hard dependencies** | `atlas-richie-context`, `atlas-richie-component-cache` (read-only for validation), Liquibase + DB (for management) |
| **Architecture** | **Split**: `mfa-validation` (gateway-side, no DB) + `mfa-management` (general-service side, DB) |

### `What` this component is — and what it isn't

| ✅ It gives you | ❌ It does not give you |
|-----------------|------------------------|
| TOTP / HOTP per RFC 6238 / 4226 | A user table — you provide `userId`, the component handles the rest |
| SMS / email second-factor channels | An OAuth server (combine with `atlas-richie-component-oauth`) |
| Recovery codes | Biometric / WebAuthn (planned) |
| Risk-based step-up policy | Tenant management UI |
| Tenant-aware (optional) — auto-detects `platform.gateway.tenant.enable` | A drop-in replacement for a hard-token YubiKey |

## ✨ Features

### `Core` capabilities

- ✅ **TOTP / HOTP** — standard RFC 6238 / RFC 4226.
- ✅ **Multiple channels** — TOTP app, SMS, email.
- ✅ **Recovery codes** — single-use backup codes per device.
- ✅ **Risk-based step-up** — opt-in policy hook to trigger MFA on sensitive operations.
- ✅ **Tenant-aware** — opt-in; auto-detects gateway tenant config.
- ✅ **Zero-DB validation module** — read-only against `GlobalCache`, no schema coupling.

### `Design` choices

- ✅ **Split architecture** — validation in gateway (stateless, fast, no DB); management in general service (writes, Liquibase).
- ✅ **User-agnostic** — only consumes your `userId` (String), never touches your user table.
- ✅ **Tenant opt-in** — `enableTenant=false` by default; auto-reads `platform.gateway.tenant.enable` if not set explicitly.

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-mfa        (parent POM)
├── atlas-richie-component-mfa-validation    (gateway-side, no DB)
│   ├── MfaValidateService     — verify code, generate, recovery
│   └── MfaValidatorStrategy   — SPI for new channels
└── atlas-richie-component-mfa-management    (general-service side)
    ├── MfaBindService         — bind / unbind device
    ├── MfaRecoveryCodeService — recovery code generation & validation
    └── config/Liquibase       — DDL management
```

## 🚀 Quick Start

### 1) `Add` the dependency

```xml
<!-- In the gateway service (validation) -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-mfa-validation</artifactId>
</dependency>

<!-- In the general service (management) -->
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-mfa-management</artifactId>
</dependency>
```

### 2) `Configure`

```yaml
platform:
  component:
    mfa:
      enabled: true
      enable-tenant: false          # auto-detects platform.gateway.tenant.enable if absent
      channel: TOTP                 # TOTP | SMS | EMAIL
      code-length: 6
      code-ttl-seconds: 30
      recovery-codes: 10
      rate-limit:
        max-attempts: 5
        lockout-seconds: 300
```

### 3) `Bind` and verify

```java
@Service
@RequiredArgsConstructor
public class LoginService {

    private final MfaBindService bindService;
    private final MfaValidateService validateService;

    public void bindTotp(String tenantId, String userId, String secret) {
        bindService.bindDevice(tenantId, userId, "TOTP", secret);
    }

    public boolean verify(String tenantId, String userId, String code) {
        return validateService.verify(tenantId, userId, code, "TOTP");
    }
}
```

## 🔧 Core Capabilities

### 1) `Bind` / unbind device

```java
MfaBindRequest request = new MfaBindRequest();
request.setUserId(userId);
request.setTenantId(tenantId);
request.setDeviceType("TOTP");
request.setSecret(secret);  // base32-encoded

bindService.bindDevice(tenantId, userId, "TOTP", secret);

// Later, unbind
bindService.unbindDevice(tenantId, userId, deviceId);
```

### 2) `Code` generation

```java
String secret = validateService.generateSecret();      // base32
String qrUri = validateService.generateOtpAuthUri(userId, secret);  // otpauth://totp/...
```

### 3) `Code` verification

```java
boolean ok = validateService.verify(tenantId, userId, "123456", "TOTP");
```

### 4) `Recovery` codes

```java
List<String> codes = recoveryCodeService.generate(userId);   // 10 single-use codes
boolean ok = recoveryCodeService.consume(userId, "ABCD-1234");
```

## ⚙️ Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Master switch |
| `enable-tenant` | boolean | auto | Auto-detects `platform.gateway.tenant.enable` |
| `channel` | enum | `TOTP` | Default channel: `TOTP` / `SMS` / `EMAIL` |
| `code-length` | int | `6` | OTP code length |
| `code-ttl-seconds` | int | `30` | Code validity window |
| `recovery-codes` | int | `10` | Number of recovery codes per device |
| `rate-limit.max-attempts` | int | `5` | Lockout after N failed attempts |
| `rate-limit.lockout-seconds` | int | `300` | Lockout duration |

## 🎯 Best Practices

1. **Keep validation in gateway, management in general service** — never co-locate them.
2. **Always set `recovery-codes >= 8`** — users will lose access codes.
3. **Set `lockout-seconds` proportional to your risk** — 5 minutes is a sane default.
4. **Use TOTP over SMS for primary channel** — SMS is vulnerable to SIM swap.
5. **Tenant consistency** — `enable-tenant` MUST be identical in validation and management modules.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **No WebAuthn / FIDO2 yet** | Can't replace hardware tokens | Use as 2nd factor alongside password |
| **No built-in SMS provider** | You integrate Twilio / Aliyun yourself | Implement `SmsSender` SPI |
| **Synchronous code verify only** | Latency on every login | Cache recent codes in Redis (TTL ≤ 30s) |
| **Liquibase DDL bundled with management** | Upgrade requires careful review | Test on staging first |

## ❓ FAQ

### `Q1` — `Why` split into validation and management?

Validation is stateless and runs in the hot path (gateway) — it must be fast and dependency-free. Management is stateful and runs in the general service where Liquibase + DB live.

### `Q2` — `Can` the same `MFA` secret work across multiple devices?

No — each device binds a unique secret. A user can have N devices (e.g., phone + tablet), each with its own secret.

### `Q3` — `How` does tenant detection work?

`enable-tenant=false` (default). If `platform.gateway.tenant.enable=true`, MFA auto-enables tenant mode. Both modules must agree.

### `Q4` — `Can` `I` use my own `SMS` provider?

Yes — implement the `SmsSender` SPI and register it as a `@Component`.

### `Q5` — `Are` recovery codes one-time use?

Yes — each code is consumed on first use and cannot be reused.

## 📚 Further Reading

- **Parent component** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **OAuth integration** — [`../atlas-richie-component-oauth/README.md`](../atlas-richie-component-oauth/README.md)
- **Tenant component** — [`../atlas-richie-component-tenant/README.md`](../atlas-richie-component-tenant/README.md)
- **Cache (Redis)** — used by validation module
- External: [RFC 6238 (TOTP)](https://datatracker.ietf.org/doc/html/rfc6238) · [RFC 4226 (HOTP)](https://datatracker.ietf.org/doc/html/rfc4226)

---

**atlas-richie-component-mfa** 🚀
