# Atlas Richie i18n Component (atlas-richie-component-i18n)

> **Internationalization (i18n)** component. Bundles `MessageSource`, locale resolvers (header / cookie / session / database), resource bundle reload, and i18n-aware exception messages. One `LocaleResolver` for the whole platform.

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
  - [2. Add message bundles](#2-add-message-bundles)
  - [3. Resolve a message](#3-resolve-a-message)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. Message bundles (multi-locale)](#1-message-bundles-multi-locale)
  - [2. Locale resolution](#2-locale-resolution)
  - [3. Parametrized messages](#3-parametrized-messages)
  - [4. Hot reload](#4-hot-reload)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: How does the resolver chain work?](#q1-how-does-the-resolver-chain-work?)
  - [Q2: Can I add a new locale without redeploying?](#q2-can-i-add-a-new-locale-without-redeploying?)
  - [Q3: How do I handle timezone + locale together?](#q3-how-do-i-handle-timezone-+-locale-together?)
  - [Q4: Can I localize exception messages?](#q4-can-i-localize-exception-messages?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-i18n` |
| **Category** | Localization — multi-language resource bundles |
| **Hard dependencies** | `spring-context` (already in Boot), `atlas-richie-context` |
| **Compatible with** | Java 17+, Spring Boot 4.x |

### `What` this component is — and what it isn't

| ✅ It gives you | ❌ It does not give you |
|-----------------|------------------------|
| Locale resolution (header / cookie / DB) | ICU MessageFormat (use Java's built-in) |
| Hot-reload of message bundles | Right-to-left (RTL) layout (frontend concern) |
| Parametrized messages | Translation management UI (use Crowdin / Lokalise) |
| Fallback locale chain | Time zone / currency formatting (use `NumberFormat` / `DateTimeFormatter`) |

## ✨ Features

### `Core` capabilities

- ✅ **Multi-locale** — any number of locales; default fallback chain.
- ✅ **Multiple resolvers** — header (`Accept-Language`), cookie, session, database.
- ✅ **Parametrized messages** — `{0}`, `{1,date}`, `{1,number,currency}` etc.
- ✅ **Hot reload** — reload bundles every N seconds (dev mode).
- ✅ **Pluggable** — `MessageSource` SPI for custom sources (DB, remote).

### `Design` choices

- ✅ **Spring `MessageSource` under the hood** — no custom abstraction.
- ✅ **UTF-8 properties** — `.properties` files, ASCII or escaped unicode.
- ✅ **Header-based default** — `Accept-Language` per HTTP spec.

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-i18n
├── config/
│   ├── I18nAutoConfiguration
│   └── I18nProperties
├── resolver/
│   ├── HeaderLocaleResolver          ← Accept-Language
│   ├── CookieLocaleResolver          ← COOKIE
│   ├── SessionLocaleResolver
│   └── DbLocaleResolver              ← platform_users.locale column
├── source/
│   ├── ResourceBundleMessageSource  ← default
│   ├── ReloadableMessageSource       ← hot-reload wrapper
│   └── DbMessageSource               ← SPI
└── fallback/
    └── LocaleFallbackChain
```

## 🚀 Quick Start

### 1) `Add` the dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-i18n</artifactId>
</dependency>
```

### 2) `Add` message bundles

```properties
# src/main/resources/i18n/messages.properties (default / English)
greeting=Hello, {0}!
user.notFound=User {0} does not exist
order.created=Order {0} has been created
```

```properties
# src/main/resources/i18n/messages_zh.properties (Chinese)
greeting=你好，{0}！
user.notFound=用户 {0} 不存在
order.created=订单 {0} 已创建
```

```properties
# src/main/resources/i18n/messages_ja.properties (Japanese)
greeting=こんにちは、{0}さん！
user.notFound=ユーザー {0} が見つかりません
order.created=注文 {0} が作成されました
```

### 3) `Resolve` a message

```java
@Service
@RequiredArgsConstructor
public class GreetingService {
    private final MessageSource messageSource;

    public String greet(Locale locale, String name) {
        return messageSource.getMessage("greeting", new Object[]{name}, locale);
    }
}
```

## 🔧 Core Capabilities

### 1) `Message` bundles (multi-locale)

```
src/main/resources/i18n/
├── messages.properties              ← default
├── messages_en.properties
├── messages_en_US.properties        ← region-specific
├── messages_zh.properties
├── messages_zh_CN.properties
└── messages_ja.properties
```

Resolution: `zh_CN` → `zh` → default.

### 2) `Locale` resolution

```yaml
platform:
  component:
    i18n:
      default-locale: en
      supported: [en, zh, ja]
      resolver: header              # header | cookie | session | db | composite
      header-name: Accept-Language
      cookie-name: LOCALE
      cookie-max-age: 2592000
```

### 3) `Parametrized` messages

```properties
# messages.properties
order.summary=Order {0} placed at {1,time,short} for {2,number,currency}
```

```java
messageSource.getMessage("order.summary",
    new Object[]{"O-1", new Date(), new BigDecimal("99.50")},
    Locale.US);
// → "Order O-1 placed at 9:30 AM for $99.50"
```

### 4) `Hot` reload

```yaml
platform:
  component:
    i18n:
      reload-seconds: 30   # 0 = disable (production)
```

Useful in dev: edit messages.properties, save, refresh — no restart.

## ⚙️ Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `default-locale` | String | `en` | Default fallback locale |
| `supported` | List<String> | `[en]` | Whitelist of allowed locales |
| `resolver` | enum | `header` | `header` / `cookie` / `session` / `db` / `composite` |
| `header-name` | String | `Accept-Language` | Header for `header` resolver |
| `cookie-name` | String | `LOCALE` | Cookie name for `cookie` resolver |
| `cookie-max-age` | int | `2592000` | Cookie TTL (s) |
| `reload-seconds` | int | `0` | Hot-reload interval (0 = off) |
| `encoding` | String | `UTF-8` | Properties file encoding |

## 🎯 Best Practices

1. **Default to English** — `messages.properties` should always be English.
2. **Use parametrized messages, not concatenation** — translator-friendly.
3. **Don't hot-reload in production** — set `reload-seconds: 0`.
4. **Validate locale at entry boundary** — controller or filter.
5. **Use `Locale.ROOT` for system messages** — logs, internal errors.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **ResourceBundle caches in JVM** | Slow locale switching | Use `reload-seconds` in dev |
| **No plural / gender support** | English "1 user" vs "2 users" hard | Custom `MessageSource` SPI |
| **No DB-backed messages out of the box** | Translation requires redeploy | Implement `DbMessageSource` SPI |

## ❓ FAQ

### `Q1` — `How` does the resolver chain work?

`composite` resolver tries in order: cookie → header → session → DB → default.

### `Q2` — `Can` `I` add a new locale without redeploying?

If the message bundle file exists at startup, yes. If not, the default is used.

### `Q3` — `How` do `I` handle timezone + locale together?

Use `LocaleContextHolder.setLocale(locale)` + `TimeZone.setDefault(tz)` (request-scoped).

### `Q4` — `Can` `I` localize exception messages?

Yes — see [`atlas-richie-component-web` §2 Global exception handling](../atlas-richie-component-web/README.md#2-global-exception-handling).

## 📚 Further Reading

- **Parent component** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **Web (consumes i18n)** — [`../atlas-richie-component-web/README.md`](../atlas-richie-component-web/README.md)
- External: [Spring MessageSource](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-messagesource) · [ICU MessageFormat](https://unicode-org.github.io/icu/userguide/format_parse/messages.html)

---

**atlas-richie-component-i18n** 🚀
