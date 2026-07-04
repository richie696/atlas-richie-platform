# Atlas Richie Liquibase组件 (atlas-richie-component-liquibase)

> **Liquibase** 数据库迁移组件。预配置 Spring Boot 自动装配、多数据库支持（MySQL / PostgreSQL / Oracle / 达梦 / 人大金仓 / MSSQL）、changelog 生成、运行时校验、按数据库上下文隔离。可插入任何业务服务。

---

## 📖 目录

- [📖 概述](#📖-概述)
  - [本模块的"是"与"不是"](#本模块的是与不是)
- [✨ 功能特性](#✨-功能特性)
  - [核心能力](#核心能力)
  - [设计选择](#设计选择)
- [🏗️ 架构与模块布局](#🏗️-架构与模块布局)
- [🚀 快速开始](#🚀-快速开始)
  - [1. 引入依赖](#1-引入依赖)
  - [2. 添加 changelog 文件](#2-添加-changelog-文件)
  - [3. 配置](#3-配置)
  - [4. 启动应用](#4-启动应用)
- [🔧 核心能力](#🔧-核心能力)
  - [1. Spring Boot 自动装配](#1-spring-boot-自动装配)
  - [2. 多数据库支持](#2-多数据库支持)
  - [3. Changelog 格式](#3-changelog-格式)
  - [4. 启动前校验](#4-启动前校验)
  - [5. 测试 profile 跳过](#5-测试-profile-跳过)
- [⚙️ 配置参考](#⚙️-配置参考)
- [🎯 最佳实践](#🎯-最佳实践)
- [⚠️ 已知限制](#⚠️-已知限制)
- [❓ 常见问题](#❓-常见问题)
  - [Q1：为什么应用启动失败并报 "changelog parse error"？](#q1：为什么应用启动失败并报-changelog-parse-error？)
  - [Q2：如何添加多租户迁移？](#q2：如何添加多租户迁移？)
  - [Q3：能否在单元测试中禁用 Liquibase？](#q3：能否在单元测试中禁用-liquibase？)
  - [Q4：如何协调跨服务的变更？](#q4：如何协调跨服务的变更？)
- [📚 相关文档](#📚-相关文档)
---

## 📖 概述

| 项 | 值 |
|---|---|
| **坐标** | `com.richie.component:atlas-richie-component-liquibase` |
| **类别** | 数据库——schema 迁移 |
| **强依赖** | `liquibase-core`、Spring Boot |
| **兼容** | MySQL、PostgreSQL、Oracle、达梦、人大金仓、MSSQL、SQLite（dev） |

### 本模块的"是"与"不是"

| ✅ 提供 | ❌ 不提供 |
|--------|---------|
| Spring Boot 自动装配 | 迁移编写工具（用 Liquibase CLI / IDE 插件） |
| 多数据库支持（8 种方言） | 在线 schema 变更（用 gh-ost / pt-online-schema-change） |
| 启动前校验 | 数据库连接池（用 HikariCP） |
| 按数据库上下文隔离 | 跨库迁移协调 |

## ✨ 功能特性

### 核心能力

- ✅ **Spring Boot 自动装配**——开箱即用，启动时执行。
- ✅ **8 种数据库方言**——MySQL、PostgreSQL、Oracle、达梦、人大金仓、MSSQL、H2、SQLite。
- ✅ **多种 changelog 格式**——XML、YAML、JSON、SQL。
- ✅ **启动前校验**——changelog 错误时快速失败。
- ✅ **测试 profile 跳过**——`spring.profiles.active=test` 时不执行迁移。
- ✅ **按数据库上下文**——每个数据源独立 changelog。

### 设计选择

- ✅ **约定优于配置**——默认 `db/changelog/master.xml`。
- ✅ **Spring Boot 原生**——使用 Spring 的 `LiquibaseProperties`。
- ✅ **无需代码**——通过 `liquibase.change-log` 配置声明。

## 🏗️ 架构与模块布局

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
    └── ChangeLogLoader                 ← classpath / 文件系统
```

## 🚀 快速开始

### 1) 引入依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-liquibase</artifactId>
</dependency>
```

### 2) 添加 changelog 文件

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

### 3) 配置

```yaml
platform:
  component:
    liquibase:
      enabled: true
      change-log: classpath:db/changelog/master.xml
      default-schema: myapp
      drop-first: false              # 仅在干净的 DB 上 drop
      skip-on-test-profile: true
```

### 4) 启动应用

```bash
mvn spring-boot:run
# 日志：
# Liquibase: Reading from `classpath:db/changelog/master.xml`
# Liquibase: ChangeSet v1.0.0/001 ran successfully
# Liquibase: ChangeSet v1.0.0/002 ran successfully
# Started Application in 3.5s
```

## 🔧 核心能力

### 1) `Spring` `Boot` 自动装配

默认位置：`db/changelog/master.xml`。用 `platform.component.liquibase.change-log` 覆盖。

### 2) 多数据库支持

| 数据库 | 支持 | 说明 |
|----------|---------|-------|
| MySQL / MariaDB | ✓ | 默认方言 |
| PostgreSQL | ✓ | 包括 `pgvector` 扩展 |
| Oracle | ✓ | 12c+ |
| **达梦 (DM)** | ✓ | 国产数据库 |
| **人大金仓 (Kingbase)** | ✓ | 国产数据库 |
| MSSQL | ✓ | 2016+ |
| H2 | ✓ (test) | 内存库 |
| SQLite | ✓ (dev) | 嵌入式 |

### 3) `Changelog` 格式

```xml
<!-- XML（默认） -->
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
-- SQL（原生）
-- changeset author:platform id:001
ALTER TABLE users ADD COLUMN phone VARCHAR(32);
```

### 4) 启动前校验

`PreStartupValidator` 在应用启动前解析 changelog 并校验语法。错误 changelog = 应用快速失败并给出明确错误。

### 5) 测试 profile 跳过

```yaml
spring:
  profiles:
    active: test
```

`platform.component.liquibase.skip-on-test-profile: true`（默认）——不执行迁移。

## ⚙️ 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | boolean | `true` | 总开关 |
| `change-log` | String | `classpath:db/changelog/master.xml` | Master changelog 路径 |
| `default-schema` | String | – | 默认 schema 名 |
| `drop-first` | boolean | `false` | 先 drop 所有表（仅 dev） |
| `skip-on-test-profile` | boolean | `true` | `spring.profiles.active=test` 时跳过 |
| `liquibase-contexts` | String | – | 逗号分隔的 context 过滤 |

## 🎯 最佳实践

1. **一个 master changelog 多个 include**——`master.xml` include 版本化 changelog。
2. **绝不修改已 commit 的 changeset**——新增一个来修复。
3. **使用 `preconditions`** 在不兼容的 DB 状态下快速失败。
4. **changelog 目录带版本**——`v1.0.0/`、`v1.1.0/` 等。
5. **在 CI 中测试回滚**——`mvn liquibase:rollback -Dliquibase.rollbackTag=v1.0.0`。

## ⚠️ 已知限制

| 限制 | 影响 | 临时方案 |
|------|------|---------|
| **无在线 schema 变更** | 大表 DDL 时锁定 | 外部使用 gh-ost / pt-osc |
| **跨库迁移协调** | 多库应用需手动排序 | 按服务依赖顺序 changelog |
| **无原生 GitOps** | 迁移在应用启动时执行 | 用 Liquibase Flow 或外部 runner |

## ❓ 常见问题

### `Q1`：为什么应用启动失败并报 "changelog parse error"？

最常见：XML 语法错误或 include 文件找不到。单独运行 `liquibase validate`。

### `Q2`：如何添加多租户迁移？

使用 Liquibase contexts：`tenant={tenantId}` 并以 `liquibase-contexts: tenant=t1` 运行。

### `Q3`：能否在单元测试中禁用 `Liquibase`？

```yaml
platform:
  component:
    liquibase:
      enabled: false
```

或使用 `skip-on-test-profile: true`（默认）。

### `Q4`：如何协调跨服务的变更？

手动排序 changelog；在 changelog 文件名中记录依赖。

## 📚 相关文档

- **父组件** — [`../README.zh.md`](../README.zh.md)
- **DAO** — [`./atlas-richie-component-dao/README.zh.md`](./atlas-richie-component-dao/README.zh.md)
- 外部：[Liquibase 文档](https://docs.liquibase.com/) · [Spring Boot Liquibase](https://docs.spring.io/spring-boot/how-to/data-initialization.html#using-liquibase)

---

**atlas-richie-component-liquibase** 🚀
