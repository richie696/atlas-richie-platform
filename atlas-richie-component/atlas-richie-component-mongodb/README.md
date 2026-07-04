# Atlas Richie MongoDB Component (atlas-richie-component-mongodb)

> Unified **MongoDB client** component for Spring Boot 4.x. Provides `MongoTemplate`-based CRUD, multi-source routing, **Sentinel** circuit breaker, schema migration, GridFS, and reactive streams. Built on top of `spring-boot-starter-data-mongodb`.

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
  - [3. Inject and use](#3-inject-and-use)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. CRUD via MongoTemplate](#1-crud-via-mongotemplate)
  - [2. Multi-source routing](#2-multi-source-routing)
  - [3. Circuit breaker](#3-circuit-breaker)
  - [4. Reactive streams](#4-reactive-streams)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: How do I enable transactions?](#q1-how-do-i-enable-transactions?)
  - [Q2: Can I use both sync and reactive in the same app?](#q2-can-i-use-both-sync-and-reactive-in-the-same-app?)
  - [Q3: How do I migrate from one MongoDB cluster to another?](#q3-how-do-i-migrate-from-one-mongodb-cluster-to-another?)
  - [Q4: Is the connection pool shared across threads?](#q4-is-the-connection-pool-shared-across-threads?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-mongodb` |
| **Category** | Storage — document database (MongoDB) |
| **Hard dependencies** | `spring-boot-starter-data-mongodb`, MongoDB driver |
| **Compatible with** | MongoDB 4.4+, Atlas, DocumentDB |

### `What` this component is — and what it isn't

| ✅ It gives you | ❌ It does not give you |
|-----------------|------------------------|
| `MongoTemplate` + `MongoClient` autoconfig | A schema migration tool (use Liquibase / Flyway for relational) |
| Multi-source routing (write / read split) | A MongoDB server |
| Sentinel circuit breaker on Mongo calls | Atlas Search indexing |
| Reactive (`ReactiveMongoTemplate`) | ORM mapping (use Spring Data MongoDB `@Document`) |

## ✨ Features

### `Core` capabilities

- ✅ **Spring Boot autoconfig** — drop-in, single `MongoClient` bean.
- ✅ **Multi-source** — primary + secondary, with read-preference routing.
- ✅ **Sentinel circuit breaker** — auto-trip on connection failures; configurable rules.
- ✅ **Reactive** — `ReactiveMongoClient` + `ReactiveMongoTemplate`.
- ✅ **GridFS** — `GridFsTemplate` for large files.
- ✅ **Schema validation** — Bean Validation on documents.

### `Design` choices

- ✅ **One `MongoClient`, multiple databases** — single connection pool, multiple DB handles.
- ✅ **Sentinel first, retry second** — fail fast on dead hosts, retry on transient.
- ✅ **Connection string driven** — `mongodb://...` parsed once.

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-mongodb
├── config/
│   ├── MongodbAutoConfiguration
│   ├── MongodbProperties
│   ├── MongodbSentinelAutoConfiguration
│   └── MongodbCircuitBreakerProperties
├── client/
│   ├── MongoClientFactory                   ← connection-pool tuning
│   └── MongoDatabaseResolver                ← multi-source routing
├── circuitbreaker/
│   ├── MongodbCircuitBreaker                ← wraps every Mongo call
│   └── SentinelRuleProvider
├── reactive/
│   └── ReactiveMongoConfiguration
└── gridfs/
    └── GridFsTemplate
```

## 🚀 Quick Start

### 1) `Add` the dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-mongodb</artifactId>
</dependency>
```

### 2) `Configure`

```yaml
platform:
  component:
    mongodb:
      uri: mongodb://localhost:27017/myapp
      database: myapp
      auto-index-creation: true
      pool:
        max-size: 100
        min-size: 10
        max-wait-time-ms: 5000
      circuit-breaker:
        enabled: true
        rules:
          - resource: mongodb.find
            grade: exception-count
            threshold: 50%
            window-seconds: 10
            min-request-amount: 10
```

### 3) `Inject` and use

```java
@Service
@RequiredArgsConstructor
public class UserRepository {

    private final MongoTemplate mongo;

    public User findById(String id) {
        return mongo.findById(id, User.class);
    }

    public List<User> findActive() {
        Query q = Query.query(Criteria.where("status").is("ACTIVE"));
        return mongo.find(q, User.class);
    }
}
```

## 🔧 Core Capabilities

### 1) `CRUD` via `MongoTemplate`

```java
// Insert
mongo.insert(user);
mongo.insertAll(List.of(user1, user2));

// Query
Query q = Query.query(Criteria.where("email").is(email).and("status").is("ACTIVE"));
User user = mongo.findOne(q, User.class);

// Update
Update u = Update.update("status", "INACTIVE").inc("loginCount", 1);
mongo.updateFirst(q, u, User.class);

// Delete
mongo.remove(q, User.class);

// Aggregation
Aggregation agg = Aggregation.newAggregation(
    Aggregation.match(Criteria.where("status").is("ACTIVE")),
    Aggregation.group("tenantId").count().as("count"),
    Aggregation.project("count").and("_id").as("tenantId")
);
List<Document> results = mongo.aggregate(agg, "users", Document.class).getMappedResults();
```

### 2) `Multi`-source routing

```yaml
platform:
  component:
    mongodb:
      primary:
        uri: mongodb://primary:27017/myapp
        read-preference: primary
      secondary:
        uri: mongodb://replica:27017/myapp
        read-preference: secondary-preferred
```

```java
@Autowired @Qualifier("primaryMongoTemplate") MongoTemplate primary;
@Autowired @Qualifier("secondaryMongoTemplate") MongoTemplate secondary;
```

### 3) `Circuit` breaker

When Sentinel detects N failures / 10s on a Mongo resource, subsequent calls fail fast with `DegradeException`. Configure via `platform.component.mongodb.circuit-breaker.rules`.

### 4) `Reactive` streams

```java
@RestController
public class ReactiveController {
    private final ReactiveMongoTemplate reactive;

    @GetMapping("/users")
    public Flux<User> all() {
        return reactive.findAll(User.class);
    }
}
```

## ⚙️ Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `uri` | String | – | MongoDB connection URI |
| `database` | String | – | Default database |
| `auto-index-creation` | boolean | `false` | Auto-create indexes from `@Indexed` |
| `pool.max-size` | int | `100` | Connection pool max |
| `pool.min-size` | int | `0` | Connection pool min |
| `pool.max-wait-time-ms` | long | `120000` | Wait for connection |
| `circuit-breaker.enabled` | boolean | `false` | Enable Sentinel |
| `circuit-breaker.rules[*].resource` | String | – | Resource name |
| `circuit-breaker.rules[*].grade` | enum | – | `exception-count` / `rt` / `error-ratio` |
| `circuit-breaker.rules[*].threshold` | String | – | Trigger threshold |

## 🎯 Best Practices

1. **Don't enable auto-index-creation in production** — manage indexes via migrations.
2. **Use read-preference for read-heavy workloads** — separate read from write connections.
3. **Configure circuit breakers for every Mongo resource** — prevent cascading failures.
4. **Tune pool size to peak concurrency × 0.1** — MongoDB connections are heavy.
5. **Use reactive only for streaming workloads** — traditional MVC is fine for most cases.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **No schema migration tool** | DDL drift over time | Use Liquibase + Mongo plugin or custom scripts |
| **Reactive circuit breaker limited** | Less mature than sync | Wrap calls in `Mono#timeout` + manual fallback |
| **No Atlas Search integration** | Can't use Atlas-specific features | Use Atlas Search SDK directly |

## ❓ FAQ

### `Q1` — `How` do `I` enable transactions?

MongoDB transactions require a replica set. Configure:

```yaml
platform:
  component:
    mongodb:
      uri: mongodb://host1,host2,host3/myapp?replicaSet=rs0&readConcernLevel=majority
```

### `Q2` — `Can` `I` use both sync and reactive in the same app?

Yes — both `MongoTemplate` and `ReactiveMongoTemplate` are auto-configured.

### `Q3` — `How` do `I` migrate from one `MongoDB` cluster to another?

1. Configure both `primary` and `secondary`
2. Dual-write during migration
3. Switch reads to new cluster
4. Drop old

### `Q4` — `Is` the connection pool shared across threads?

Yes — `MongoClient` is thread-safe; pool is per-cluster.

## 📚 Further Reading

- **Parent component** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **Microservice (Sentinel)** — [`../atlas-richie-component-microservice/README.md`](../atlas-richie-component-microservice/README.md)
- **Liquibase** — [`../atlas-richie-component-liquibase/README.md`](../atlas-richie-component-liquibase/README.md)
- External: [MongoDB Java driver](https://www.mongodb.com/docs/drivers/java/) · [Spring Data MongoDB](https://spring.io/projects/spring-data-mongodb) · [Sentinel flow control](https://sentinelguard.io/)

---

**atlas-richie-component-mongodb** 🚀
