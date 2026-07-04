# Atlas Richie Statemachine Component (atlas-richie-component-statemachine)

> Lightweight **state machine** built on [Easy Rules 4.1.0](https://github.com/j-easy/easy-rules). Configuration-driven states / transitions, SpEL condition/action evaluation with sandbox safety, Redis single-source-of-truth, Redis Stream async persistence, and **Spring event** listeners. Multi-state-machine support per business object.

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
  - [3. Define the state machine YAML](#3-define-the-state-machine-yaml)
  - [4. Define enums + trigger transitions](#4-define-enums-+-trigger-transitions)
- [🔧 Core Capabilities](#🔧-core-capabilities)
  - [1. Static facade `StateMachine.fire`](#1-static-facade-statemachinefire)
  - [2. SpEL sandbox](#2-spel-sandbox)
  - [3. Redis single source of truth](#3-redis-single-source-of-truth)
  - [4. Async DB persistence via Redis Stream](#4-async-db-persistence-via-redis-stream)
  - [5. Spring events](#5-spring-events)
- [⚙️ Configuration Reference](#⚙️-configuration-reference)
- [🎯 Best Practices](#🎯-best-practices)
- [⚠️ Known Limitations](#⚠️-known-limitations)
- [❓ FAQ](#❓-faq)
  - [Q1: Why SpEL and not MVEL?](#q1-why-spel-and-not-mvel?)
  - [Q2: Can I have multiple state machines per business object?](#q2-can-i-have-multiple-state-machines-per-business-object?)
  - [Q3: How do I handle terminal states?](#q3-how-do-i-handle-terminal-states?)
  - [Q4: Can I dispatch events from outside `StateMachine.fire`?](#q4-can-i-dispatch-events-from-outside-statemachinefire?)
  - [Q5: What if Redis is down?](#q5-what-if-redis-is-down?)
- [📚 Further Reading](#📚-further-reading)
---

## 📖 Overview

| Item | Value |
|------|-------|
| **Artifact** | `com.richie.component:atlas-richie-component-statemachine` |
| **Category** | Domain logic — state machine |
| **Hard dependencies** | `easy-rules` 4.1.0, `atlas-richie-component-cache` (Redis), `atlas-richie-component-dao` |
| **Compatible with** | JDK 17+, Spring Boot 4.x |

### `What` this component is — and what it isn't

| ✅ It gives you | ❌ It does not give you |
|-----------------|------------------------|
| Configuration-driven state machines | A workflow engine (BPMN / Camunda) |
| SpEL sandbox (safe expression eval) | A rule engine for arbitrary business rules |
| Redis single source of truth + async DB persistence | Long-running saga (use Temporal / Cadence) |
| Multi-state-machine per business object | Visual designer (use Eclipse Stardust) |

## ✨ Features

### `Core` capabilities

- ✅ **Easy Rules 4.1.0** — proven rule engine under the hood.
- ✅ **SpEL sandbox** — read-only data binding; blocks method calls, type refs, constructors.
- ✅ **Redis single source of truth** — `getCurrentState` is O(1).
- ✅ **Redis Stream async persistence** — durable replayable audit trail.
- ✅ **Cache warm-up** — pre-load state from DB on startup.
- ✅ **State history** — full audit, default 7 days TTL.
- ✅ **FINAL / ERROR protection** — terminal states can't transition (unless `reopen: true`).
- ✅ **Multi-state-machine** — one business object can have multiple state dimensions.

### `Design` choices

- ✅ **SpEL sandbox over MVEL** — Spring Expression Language with `SimpleEvaluationContext.forReadOnlyDataBinding()`.
- ✅ **Defense-in-depth blacklist** — secondary protection beyond sandbox.
- ✅ **Slow-query threshold** — expression runtime monitored.
- ✅ **Spring events for decoupling** — listeners can react to state changes.

## 🏗️ Architecture & Module Layout

```
atlas-richie-component-statemachine
├── engine/
│   ├── StateMachineEngine
│   ├── StateTransitionEngine
│   └── EasyRulesEngineAdapter
├── config/
│   ├── StateMachineAutoConfiguration
│   └── StateMachineProperties
├── loader/
│   ├── YamlStateMachineLoader
│   └── StateMachineRegistry
├── storage/
│   ├── RedisStateStorage                  ← SSOT
│   ├── DbStateStorage                     ← async via Redis Stream
│   └── StateHistoryStorage
├── expression/
│   ├── SpelEvaluator                      ← sandbox
│   ├── SpelSecurityManager                ← blacklist
│   └── SpelPerformanceMonitor
├── async/
│   ├── StateSyncStreamConsumer           ← Redis Stream → DB
│   └── StateSyncMessage
└── event/
    └── StateChangeApplicationEvent
```

## 🚀 Quick Start

### 1) `Add` the dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-statemachine</artifactId>
</dependency>
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-dao</artifactId>
</dependency>
```

### 2) `Configure`

```yaml
platform:
  component:
    statemachine:
      enabled: true
      db-persistence-mode: ASYNC
      strict-persistence-mode: true
      state-machine:
        enable-history: true
        enable-events: true
        config-path: classpath:statemachine/
      storage:
        type: redis
        timeout: 604800000        # 7 days history TTL
        db:
          enabled: true
          batch-size: 100
      expression:
        slow-threshold-ms: 1000
        enable-security-check: true
```

### 3) `Define` the state machine `YAML`

```yaml
# src/main/resources/statemachine/order-statemachine.yml
name: order
states:
  - name: PENDING
    type: INITIAL
  - name: CONFIRMED
    type: NORMAL
  - name: COMPLETED
    type: FINAL
  - name: CANCELLED
    type: ERROR
transitions:
  - name: confirm
    fromState: PENDING
    toState: CONFIRMED
    event: CONFIRM
    condition: "context.attributes['amount'] != null and context.attributes['amount'] > 0"
    action: "context.attributes['confirmedTime'] = '2026-01-01T00:00:00'"
  - name: complete
    fromState: CONFIRMED
    toState: COMPLETED
    event: COMPLETE
  - name: cancel
    fromState: [PENDING, CONFIRMED]
    toState: CANCELLED
    event: CANCEL
```

### 4) `Define` enums + trigger transitions

```java
public enum OrderSm { order }
public enum OrderEvent { CONFIRM, COMPLETE, CANCEL }

// In your service
@Service
@RequiredArgsConstructor
public class OrderService {
    public void confirm(String orderId, BigDecimal amount) {
        Map<String, Object> attrs = Map.of("amount", amount);
        StateTransitionResult r = StateMachine.fire(
                OrderSm.order, OrderEvent.CONFIRM, orderId, attrs);
        if (!r.isSuccess()) throw new RuntimeException(r.getErrorMessage());
    }
}
```

## 🔧 Core Capabilities

### 1) Static facade `StateMachine.fire`

```java
// Fire an event
StateTransitionResult r = StateMachine.fire(sm, event, businessId, attrs);

// Query
String currentState = StateMachine.getCurrentState(sm, businessId);
boolean canTransition = StateMachine.canTransitionTo(sm, event, businessId);
List<StateHistory> history = StateMachine.getStateHistory(sm, businessId);
```

### 2) `SpEL` sandbox

Allowed:
- `context.attributes['key']`
- `#currentState == 'PENDING'`
- `#event == 'CONFIRM'`

Blocked:
- `T(java.lang.Runtime)` (type refs)
- `new File(...)` (constructors)
- `context.toString()` (method calls)
- `@beanName` (bean refs)

### 3) `Redis` single source of truth

Current state and history live in Redis. `getCurrentState` is O(1) — `O(1)` Redis `GET`. Reads never hit the DB.

### 4) `Async` `DB` persistence via `Redis` `Stream`

Each state change → publish `StateSyncMessage` to Redis Stream. A consumer (`StateSyncStreamConsumer`) reads in batch and writes to DB asynchronously.

### 5) `Spring` events

```java
@EventListener
public void onChange(StateChangeApplicationEvent event) {
    log.info("State changed: {} {} -> {}", event.getBusinessId(),
             event.getFromState(), event.getToState());
}
```

## ⚙️ Configuration Reference

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Master switch |
| `db-persistence-mode` | enum | `ASYNC` | `ASYNC` (Redis Stream) or `SYNC` |
| `strict-persistence-mode` | boolean | `true` | Block ASYNC downgrade when global is SYNC |
| `state-machine.enable-history` | boolean | `true` | Record full transition history |
| `state-machine.enable-events` | boolean | `true` | Publish Spring events on transition |
| `storage.type` | enum | `redis` | `redis` / `memory` |
| `storage.timeout` | long | `604800000` | History TTL (ms), 7 days |
| `storage.db.enabled` | boolean | `false` | Async DB persistence |
| `storage.db.batch-size` | int | `100` | DB batch write size |
| `expression.slow-threshold-ms` | long | `1000` | WARN threshold for slow expressions |
| `expression.enable-security-check` | boolean | `true` | Enable sandbox + blacklist |

## 🎯 Best Practices

1. **Use enums for state machine name and events** — type-safe, IDE-friendly.
2. **Keep SpEL conditions short** — no business logic in expressions; load from DB.
3. **Use `ASYNC` mode for most cases** — only `SYNC` for critical (payment) state machines.
4. **Always define terminal state behavior** — `attributes.reopen: true` if reopen is intended.
5. **Tune `slow-threshold-ms` to your SLA** — 100ms for fast state machines, 1s+ for slow ones.

## ⚠️ Known Limitations

| Limitation | Impact | Workaround |
|------------|--------|------------|
| **Redis is required for production** | No Redis = no SSOT | Use `storage.type: memory` for dev only |
| **SpEL sandbox blocks legitimate method calls** | Some expressions need rewriting | Whitelist via `expression.security-blacklist` |
| **ASYNC mode has eventual consistency** | DB write may lag Redis by ms–s | Use SYNC for critical consistency |
| **No visual debugger** | Hard to trace complex flows | Use `StateHistory` + structured logs |

## ❓ FAQ

### `Q1` — `Why` `SpEL` and not `MVEL`?

MVEL is unmaintained (last release 2017) and had RCE issues. SpEL is maintained by Spring and has a native sandbox (`SimpleEvaluationContext.forReadOnlyDataBinding()`).

### `Q2` — `Can` `I` have multiple state machines per business object?

Yes — different `stateMachineName`s are independent. E.g., `Order.order`, `Order.payment`, `Order.shipping`.

### `Q3` — `How` do `I` handle terminal states?

FINAL / ERROR states reject transitions by default. To reopen, set `attributes.reopen: true` on the transition.

### Q4 — Can I dispatch events from outside `StateMachine.fire`?

Yes — use `StateMachine.dispatch(...)` (lower-level) or post a Spring `StateChangeRequestEvent`.

### `Q5` — `What` if `Redis` is down?

Read fallbacks: throws `StateStorageUnavailableException`. You catch it in `StateTransitionResult.getErrorMessage()`.

## 📚 Further Reading

- **Parent component** — [`../README.md`](../README.md) / [`../README.zh.md`](../README.md)
- **Cache (Redis)** — required for SSOT
- **Messaging (Redis Stream)** — required for async persistence
- External: [Easy Rules](https://github.com/j-easy/easy-rules) · [Spring SpEL](https://docs.spring.io/spring-framework/reference/core/expressions.html)

---

**atlas-richie-component-statemachine** 🚀
