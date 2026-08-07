# Atlas Richie OAuth 2.1 Component (atlas-richie-oauth-parent)

> **OAuth 2.1 authorization protocol** component. Provides token endpoint, client management, scope management, dynamic
> client registration (DCR), PKCE, Device Authorization Grant, OIDC contracts, and the implemented grant types (`authorization_code`, `client_credentials`,
> `refresh_token`).
> with [RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749) / [RFC 8252](https://datatracker.ietf.org/doc/html/rfc8252) / [RFC 8414](https://datatracker.ietf.org/doc/html/rfc8414).
>
> **Deep dive**: Full design
> document — [docs/en/oauth-component-design.md](docs/en/oauth-component-design.md)（[中文](docs/zh/oauth-component-design.md)）

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
    - [3. Register a client](#3-register-a-client)
    - [4. Request a token](#4-request-a-token)
- [🔧 Core Capabilities](#🔧-core-capabilities)
    - [1. Authorization Code + PKCE](#1-authorization-code-+-pkce)
    - [2. Client Credentials](#2-client-credentials)
    - [3. Refresh Token](#3-refresh-token)
    - [4. Dynamic Client Registration](#4-dynamic-client-registration)
    - [5. Scope management](#5-scope-management)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
    - [Q1: How is this different from
      `spring-security-oauth2-authorization-server`?](#q1-how-is-this-different-from-spring-security-oauth2-authorization-server?)
    - [Q2: Can I plug in my own token store?](#q2-can-i-plug-in-my-own-token-store?)
    - [Q3: Is PKCE mandatory?](#q3-is-pkce-mandatory?)
    - [Q4: How do I add custom claims to JWT?](#q4-how-do-i-add-custom-claims-to-jwt?)
    - [Q5: Does this support OIDC?](#q5-does-this-support-oidc?)
- [📚 Further Reading](#📚-further-reading)

---

## 📖 Overview

| Item                  | Value                                                                                                                          |
|-----------------------|--------------------------------------------------------------------------------------------------------------------------------|
| **Artifact**          | `cn.richie696.component:atlas-richie-oauth-parent`                                                                          |
| **Category**          | Identity & access — reusable OAuth 2.1 authorization protocol core                                                            |
| **Hard dependencies** | `atlas-richie-context`, DB (clients / tokens / grants), Redis (rate-limit, cache)                                              |
| **Standards**         | RFC 6749, RFC 7636 (PKCE), RFC 8414, RFC 8707 (resource indicator), RFC 9068 (JWT access tokens), RFC 7009 (revocation) |

### `What` this component is — and what it isn't

| ✅ It gives you                                                   | ❌ It does not give you                                       |
|-------------------------------------------------------------------|---------------------------------------------------------------|
| Reusable OAuth 2.1 authorization-server protocol core           | User database, login UI and MFA                             |
| Token endpoint, introspection endpoint, revocation endpoint       | SAML 2.0 / WS-Federation (not planned)                        |
| Dynamic Client Registration (DCR)                                 | LDAP / Active Directory integration                           |
| Optional OIDC Provider extension (`oauth-oidc`)                  | SAML 2.0 / WS-Federation (not planned)                       |
| PKCE for public clients (mobile / SPA), Device Authorization     | User login, MFA and consent UI                              |
| Multi-tenant (compatible with `atlas-richie-tenant-parent`)    | Built-in identity provider (IdP) — you provide the user store |

## ✨ Features

### `Core` capabilities

- ✅ **Implemented grant types** — `authorization_code`, `client_credentials`, and `refresh_token`.
- ✅ **Device Authorization Grant** (`device_code`, RFC 8628) — device/user code issuance, approval, polling interval and `slow_down`, one-time exchange.
- ✅ **PKCE** (RFC 7636) — for public clients (mobile / SPA).
- ✅ **DCR** (RFC 7591) — programmatic client registration.
- ✅ **JWT access tokens** (RFC 9068) — with `iss`, `aud`, `exp`, `nbf`, `iat`, `jti`, `sub`, `scope`, `client_id`.
- ✅ **Token introspection** (RFC 7662) and **revocation** (RFC 7009).
- ✅ **Scope-based authorization** — arbitrary scopes with optional policies.
- ✅ **Multi-tenant claim extension point** — inject trusted tenant claims through `AccessTokenClaimsCustomizer`.
- ✅ **OIDC response contracts** — Discovery, `query`/`form_post`, configurable Hybrid response types, ID Token/UserInfo and logout contracts; HTTP delivery is injected by the service.
- ✅ **Client authentication** — `client_secret_basic`, `client_secret_post` and public-client `none` are validated by one core service.
- ✅ **Key publication** — RSA signer publishes `kid`-based JWKS through `JwkSetProvider`; signing key rotation remains service-owned.
- ✅ **DPoP resource binding** — opt-in RFC 9449 proof validation with `ath`, `cnf.jkt`, nonce and distributed `jti` replay storage.

### `Design` choices

- ✅ **Framework-neutral core** — the service layer can expose these capabilities through Spring MVC/WebFlux or another HTTP stack.
- ✅ **Storage-agnostic** — pluggable `TokenStore`, `ClientRepository`, `AuthorizationCodeStore` and `OAuthCache` ports.
- ✅ **Stateless access tokens** — JWT means no DB lookup per request.
- ✅ **Stateless refresh tokens** — opaque + JWK rotation.

## 🏗️ Architecture & Module Layout

```
atlas-richie-oauth-parent
├── config/
│   ├── OAuthAutoConfiguration
│   ├── OAuthProperties
│   ├── AuthorizationServerSettings
│   └── ClientSettings / TokenSettings
├── endpoint/
│   ├── OAuth2TokenEndpoint
│   ├── OAuth2RevocationEndpoint
│   ├── OAuth2IntrospectionEndpoint
│   └── OIDC endpoints are exposed by oauth-service using oauth-oidc
├── client/
│   ├── ClientRegistrationService     ← DCR
│   └── ClientRepository
├── scope/
│   └── ScopeService
├── token/
│   ├── JwtTokenCustomizer
│   └── TokenStore (JDBC | Redis)
└── jwk/
    └── JwkSource                      ← signing keys (RSA / EC)
```

## 🚀 Quick Start

### 1) `Add` the dependency

```xml
<dependency>
    <groupId>cn.richie696.component</groupId>
    <artifactId>atlas-richie-oauth-parent</artifactId>
</dependency>
```

### 2) `Configure`

```yaml
platform:
  component:
    oauth:
      issuer: https://auth.example.com
      token:
        access-token-ttl-seconds: 3600
        refresh-token-ttl-seconds: 2592000
        format: self-contained   # JWT (stateless)
      client:
        default-scopes: openid, profile
      dcr:
        enabled: true
        require-pkce: true       # force PKCE for public clients
      jwk:
        rotation-period-days: 90
```

### 3) `Register` a client

```yaml
# Static registration (application.yml)
platform:
  component:
    oauth:
      clients:
        - client-id: web-app
          client-secret: "{noop}secret"
          grant-types: [authorization_code, refresh_token]
          redirect-uris: [https://app.example.com/callback]
          scopes: [openid, profile, email]
          require-pkce: true
```

### 4) `Request` a token

```bash
# authorization_code + PKCE
curl -X POST https://auth.example.com/oauth2/token   -d "grant_type=authorization_code"   -d "code=..."   -d "code_verifier=..."   -d "client_id=web-app"   -d "redirect_uri=https://app.example.com/callback"

# client_credentials
curl -X POST https://auth.example.com/oauth2/token   -u "service:secret"   -d "grant_type=client_credentials"   -d "scope=read:users"
```

## 🔧 Core Capabilities

### 1) `Authorization` `Code` + `PKCE`

Recommended for web apps, mobile apps, SPAs.

```java
// Authorization request (browser)
String authUrl = authorizationEndpoint
    .authorize(builder -> builder
        .clientId("web-app")
        .scope("openid", "profile")
        .codeChallenge(pkce.codeVerifier())        // PKCE
        .codeChallengeMethod("S256")
        .redirectUri("https://app.example.com/callback")
        .state(state)
        .build());
```

### 2) `Client` `Credentials`

For service-to-service auth.

```bash
curl -u "billing-svc:secret"   -d "grant_type=client_credentials"   -d "scope=invoice:read"   https://auth.example.com/oauth2/token
```

### 3) `Refresh` `Token`

```bash
curl -X POST https://auth.example.com/oauth2/token   -d "grant_type=refresh_token"   -d "refresh_token=..."   -d "client_id=web-app"
```

### 4) `Dynamic` `Client` `Registration`

```bash
curl -X POST https://auth.example.com/connect/register   -H "Content-Type: application/json"   -d '{
    "client_name": "Mobile App",
    "redirect_uris": ["myapp://callback"],
    "grant_types": ["authorization_code", "refresh_token"],
    "token_endpoint_auth_method": "none",          # PKCE
    "scope": "openid profile"
  }'
```

### 5) `Scope` management

```java
@PreAuthorize("hasAuthority('SCOPE_invoice:read')")
@GetMapping("/invoices/{id}")
public Invoice get(@PathVariable String id) { ... }
```

### 6) Device Authorization Grant (RFC 8628)

The component owns device-code lifecycle, approval state, polling interval and one-time exchange. The standalone
OAuth Service owns login/MFA/consent and calls `DeviceAuthorizationService.approve(userCode, subject)` after the user
confirms the device. Clients exchange through `TokenEndpoint.exchangeDeviceCode(...)`; excessive polling returns
`slow_down`.

### 7) Signing, JWKS and claims

Inject an `AccessTokenSigner` (RSA is recommended in production) and expose `JwkSetProvider.keys()` as the standard
JWKS endpoint. Use a stable `kid` during key rotation. Tenant and role claims belong in
`AccessTokenClaimsCustomizer`; reserved protocol claims cannot be overwritten.

### 8) Resource Server modes

The Spring Boot starter supports three explicit modes: configure only `jwk-set-uri` for JWT-only validation; configure
only `introspection-uri` for introspection-only validation (with `introspection-fallback=true`, the default); configure
both for hybrid validation, where local JWT verification is attempted before introspection fallback. If neither is
configured, the authenticator remains fail-closed and production configuration should reject the setup.

## ⚙️ Configuration Reference

| Property                          | Type         | Default             | Description                                    |
|-----------------------------------|--------------|---------------------|------------------------------------------------|
| `issuer`                          | String       | –                   | OAuth issuer URL (public, used as `iss` claim) |
| `token.access-token-ttl-seconds`  | long         | `3600`              | Access token TTL                               |
| `token.refresh-token-ttl-seconds` | long         | `2592000`           | Refresh token TTL (30 days)                    |
| `token.format`                    | enum         | `self-contained`    | `self-contained` (JWT) or `reference` (opaque) |
| `client.default-scopes`           | List<String> | `[openid, profile]` | Default scopes for new clients                 |
| `dcr.enabled`                     | boolean      | `true`              | Allow Dynamic Client Registration              |
| `dcr.require-pkce`                | boolean      | `true`              | Force PKCE for public clients                  |
| `jwk.rotation-period-days`        | int          | `90`                | JWK rotation period                            |

## 🎯 Best Practices

1. **Always use PKCE for public clients** (mobile / SPA). Set `dcr.require-pkce=true`.
2. **Prefer short-lived access tokens + refresh tokens** — set `access-token-ttl-seconds=900` (15 min).
3. **Use JWT access tokens** (`format: self-contained`) — saves a DB lookup per request.
4. **Rotate signing keys every 90 days** — keep old JWKs published until all tokens expire.
5. **Configure allowed grant types per client** — never enable `password` for first-party public clients.

## ⚠️ Known Limitations

| Limitation                             | Impact                              | Workaround                              |
|----------------------------------------|-------------------------------------|-----------------------------------------|
| **OIDC user data is not built in**     | Component does not own user database | Inject `OidcUserInfoProvider` from AS    |
| **No built-in IdP**                    | You wire your own user store        | Implement `UserDetailsService`          |
| **No SAML 2.0**                        | SAML-only federations not supported | Use a separate SAML IdP                 |
| **Refresh token rotation not opt-out** | Per RFC 6749 §10.4                  | Implement custom `OAuth2TokenGenerator` |

## ❓ FAQ

### Q1 — How is this different from `spring-security-oauth2-authorization-server`?

This component is framework-neutral and provides the reusable protocol core, storage ports, DCR, scope policies,
JWT customizer and platform-aligned configuration. A standalone OAuth Service owns the HTTP runtime and user flows.

### `Q2` — `Can` `I` plug in my own token store?

Yes — implement the component's `TokenStore`, `ClientRepository`, `AuthorizationCodeStore` or `OAuthCache` SPI,
depending on which state you want to externalize.

### `Q3` — `Is` `PKCE` mandatory?

For public clients, yes (`dcr.require-pkce=true`). For confidential clients, optional but recommended.

### `Q4` — `How` do `I` add custom claims to `JWT`?

```java
@Bean
public AccessTokenClaimsCustomizer accessTokenClaimsCustomizer() {
    return (clientId, client, scopes, resource) -> Map.of("tenant_id", trustedTenantContext.currentTenant());
}
```

### `Q5` — `Does` this support `OIDC`?

Yes, through the optional `atlas-richie-oauth-oidc` module. It provides ID Token, `openid`/`nonce`
validation, scope-filtered UserInfo, Discovery Metadata, RP-Initiated Logout validation and Front/Backchannel Logout contracts. The standalone OAuth Service
must provide the user store, login/MFA flow, consent UI and HTTP endpoints.

## 📚 Further Reading

- **Design Document** — [`docs/en/oauth-component-design.md`](docs/en/oauth-component-design.md)
  ([中文](docs/zh/oauth-component-design.md))
- **Parent component** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **MFA** — [`../atlas-richie-mfa-parent/README.md`](../atlas-richie-mfa-parent/README.md)
- **Tenant** — [`../atlas-richie-tenant-parent/README.md`](../atlas-richie-tenant-parent/README.md)
-
External: [OAuth 2.1 (draft)](https://datatracker.ietf.org/doc/draft-ietf-oauth-v2-1/) · [Spring Authorization Server](https://spring.io/projects/spring-authorization-server)

---

**atlas-richie-oauth-parent** 🚀
