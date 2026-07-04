# Atlas Richie Liquibase Component (atlas-richie-component-liquibase)

> **Liquibase** database migration component. Pre-configured Spring Boot autoconfig, multi-DB support (MySQL / PostgreSQL / Oracle / DM / Kingbase / MSSQL), changelog generation, runtime validation, and per-DB context isolation. Plugs into any business service.

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
  - [2. Add changelog file](#2-add-changelog-file)
  - [3. Configure](#3-configure)
  - [4. Run app](#4-run-app)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. Spring Boot autoconfig](#1-spring-boot-autoconfig)
  - [2. Multi-DB support](#2-multi-db-support)
  - [3. Changelog formats](#3-changelog-formats)
  - [4. Pre-startup validation](#4-pre-startup-validation)
  - [5. Skip on test profile](#5-skip-on-test-profile)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: Why does the app fail to start with "changelog parse error"?](#q1-why-does-the-app-fail-to-start-with-changelog-parse-error?)
  - [Q2: How do I add a multi-tenant migration?](#q2-how-do-i-add-a-multi-tenant-migration?)
  - [Q3: Can I disable Liquibase for unit tests?](#q3-can-i-disable-liquibase-for-unit-tests?)
  - [Q4: How do I coordinate changes across services?](#q4-how-do-i-coordinate-changes-across-services?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-liquibase` |
| **Category** | Database — schema migration |
| **Hard dependencies** | `liquibase-core`, Spring Boot |
| **Compatible with** | MySQL, PostgreSQL, Oracle, DM, Kingbase, MSSQL, SQLite (dev) |

### `What` this component is — and what it isn't

| ✅ It gives you | ❌ It does not give you |
|-----------------|------------------------|
| Spring Boot autoconfig for Liquibase | A migration authoring tool (use Liquibase CLI / IDE plugin) |
| Multi-DB support (8 dialects) | Online schema change (use gh-ost / pt-online-schema-change) |
| Pre-startup validation | Database connection pooling (use HikariCP) |
| Per-DB context isolation | Cross-DB migration coordination |

## ✨ Features

### `Core` capabilities

- ✅ **Spring Boot autoconfig** — drop-in, runs at startup.
- ✅ **8 DB dialects** — MySQL, PostgreSQL, Oracle, DM, Kingbase, MSSQL, H2, SQLite.
- ✅ **Multiple changelog formats** — XML, YAML, JSON, SQL.
- ✅ **Pre-startup validation** — fail fast on bad changelog.
- ✅ **Skip on test profile** — `spring.profiles.active=test` skips migrations.
- ✅ **Per-DB context** — isolated changelogs per datasource.

### `Design` choices

- ✅ **Convention over configuration** — `db/changelog/master.xml` is the default.
- ✅ **Spring Boot native** — uses Spring's `LiquibaseProperties`.
- ✅ **No code required** — declare via `liquibase.change-log` config.

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-liquibase
├── config/
│   ├── LiquibaseAutoConfiguration
│   ├── LiquibaseProperties
│   └── MultiDataSourceLiquibaseConfig
├── dialect/
│   ├── MySQLDialectSupport
│   ├── PostgreSQLDialectSupport
│   ├── OracleDialectSupport
│   ├── DmDialectSupport                 ← 达梦
│   ├── KingbaseDialectSupport           ← 人大金仓
│   └── MssqlDialectSupport
├── validation/
│   └── PreStartupValidator
└── changelog/
    └── ChangeLogLoader                 ← from classpath / file system
```

## 🚀 Quick Start

### 1) `Add` the dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-liquibase</artifactId>
</dependency>
```

### 2) `Add` changelog file

```xml
<!-- src/main/resources/db/changelog/master.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog">
    <include file="v1.0.0/001-create-users.xml" relativeToChangelogFile="true"/>
    <include file="v1.0.0/002-create-orders.xml" relativeToChangelogFile="true"/>
</databaseChangeLog>
```

```xml
<!-- src/main/resources/db/changelog/v1.0.0/001-create-users.xml -->
<databaseChangeLog>
    <changeSet id="001" author="platform">
        <createTable tableName="users">
            <column name="id" type="varchar(64)">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="email" type="varchar(255)"/>
            <column name="created_at" type="timestamp" defaultValueComputed="now()"/>
        </createTable>
    </changeSet>
</databaseChangeLog>
```

### 3) `Configure`

```yaml
platform:
  component:
    liquibase:
      enabled: true
      change-log: classpath:db/changelog/master.xml
      default-schema: myapp
      drop-first: false              # drop tables on clean DB only
      skip-on-test-profile: true
```

### 4) `Run` app

```bash
mvn spring-boot:run
# Logs:
# Liquibase: Reading from `classpath:db/changelog/master.xml`
# Liquibase: ChangeSet v1.0.0/001 ran successfully
# Liquibase: ChangeSet v1.0.0/002 ran successfully
# Started Application in 3.5s
```

## 🔧 Core Capabilities

### 1) `Spring` `Boot` autoconfig

Default location: `db/changelog/master.xml`. Override with `platform.component.liquibase.change-log`.

### 2) `Multi`-`DB` support

| Database | Support | Notes |
|----------|---------|-------|
| MySQL / MariaDB | ✓ | Default dialect |
| PostgreSQL | ✓ | Including `pgvector` extensions |
| Oracle | ✓ | 12c+ |
| **达梦 (DM)** | ✓ | Chinese domestic DB |
| **人大金仓 (Kingbase)** | ✓ | Chinese domestic DB |
| MSSQL | ✓ | 2016+ |
| H2 | ✓ (test) | In-memory |
| SQLite | ✓ (dev) | Embedded |

### 3) `Changelog` formats

```xml
<!-- XML (default) -->
<changeSet id="001">
    <addColumn tableName="users"><column name="phone" type="varchar(32)"/></addColumn>
</changeSet>
```

```yaml
# YAML
databaseChangeLog:
  - changeSet:
      id: 001
      changes:
        - addColumn:
            tableName: users
            columns:
              - column: { name: phone, type: varchar(32) }
```

```sql
-- SQL (raw)
-- changeset author:platform id:001
ALTER TABLE users ADD COLUMN phone VARCHAR(32);
```

### 4) `Pre`-startup validation

`PreStartupValidator` parses the changelog and validates syntax before app startup. Bad changelog = app fails fast with a clear error.

### 5) `Skip` on test profile

```yaml
spring:
  profiles:
    active: test
```

`platform.component.liquibase.skip-on-test-profile: true` (default) — no migrations run.

## ⚙️ Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Master switch |
| `change-log` | String | `classpath:db/changelog/master.xml` | Master changelog path |
| `default-schema` | String | – | Default schema name |
| `drop-first` | boolean | `false` | Drop all tables first (dev only) |
| `skip-on-test-profile` | boolean | `true` | Skip when `spring.profiles.active=test` |
| `liquibase-contexts` | String | – | Comma-separated contexts to filter |

## 🎯 Best Practices

1. **One master changelog, many includes** — `master.xml` includes versioned changelogs.
2. **Never edit a committed changeset** — add a new one to fix.
3. **Use `preconditions`** to fail fast on incompatible DB state.
4. **Version your changelog directory** — `v1.0.0/`, `v1.1.0/`, etc.
5. **Always test rollback in CI** — `mvn liquibase:rollback -Dliquibase.rollbackTag=v1.0.0`.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **No online schema change** | Large tables lock during DDL | Use gh-ost / pt-osc externally |
| **Cross-DB migration coordination** | Multi-DB apps need manual ordering | Sequence changelogs by service dependency |
| **No native GitOps** | Migrations run on app startup | Use Liquibase Flow or external runner |

## ❓ FAQ

### `Q1` — `Why` does the app fail to start with "changelog parse error"?

Most common: XML syntax error or include file not found. Run `liquibase validate` standalone.

### `Q2` — `How` do `I` add a multi-tenant migration?

Use Liquibase contexts: `tenant={tenantId}` and run with `liquibase-contexts: tenant=t1`.

### `Q3` — `Can` `I` disable `Liquibase` for unit tests?

```yaml
platform:
  component:
    liquibase:
      enabled: false
```

Or use `skip-on-test-profile: true` (default).

### `Q4` — `How` do `I` coordinate changes across services?

Sequence changelogs manually; document dependencies in your changelog file names.

## 📚 Further Reading

- **Parent component** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **DAO** — [`../atlas-richie-component-dao/README.md`](../atlas-richie-component-dao/README.md)
- External: [Liquibase docs](https://docs.liquibase.com/) · [Spring Boot Liquibase](https://docs.spring.io/spring-boot/how-to/data-initialization.html#using-liquibase)

---

**atlas-richie-component-liquibase** 🚀
