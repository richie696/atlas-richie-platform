**Languages:** [English](CHANGELOG.md) | [中文](CHANGELOG.zh.md)

---

## 1.0.0-SNAPSHOT (2026-05-28)

> This is the **first public baseline** of Atlas Richie Platform, with a new architecture and module boundaries, built on JDK 25 and Spring Boot 4.x.

### Architecture & Platform Baseline

- Established the `atlas-richie-platform` multi-module baseline with clear layering: `base` / `component` / `gateway-service` / `component-template`.
- Consolidated dependency management, shared contracts, and context capabilities in `atlas-richie-base` as the cross-service foundation.
- Delivered a standardized component matrix in `atlas-richie-component` with configuration-driven multi-implementation switching.
- Unified gateway capabilities in `atlas-richie-gateway-service` with multiple deployment modes (OpenAPI gateway, microservice gateway, intranet gateway) and configuration-based switching.

### Technology Stack

- `JDK`: 25
- `Spring Boot`: 4.0.x
- `Spring Cloud`: 2025.1.1
- `Spring Cloud Alibaba`: 2025.1.0.0
- `Spring Cloud Azure`: 7.0.0
- `Spring AI`: 2.0.0-M4
- `Spring AI Alibaba`: 1.1.2.2
- `AgentScope`: 1.0.11

### Component Overview (Initial Release)

- Cache & distributed capabilities: `atlas-richie-component-cache`
- Data access & multi-tenant extensions: `atlas-richie-component-dao` / `atlas-richie-component-tenant`
- Unified HTTP client abstraction: `atlas-richie-component-http`
- Data desensitization (API / logging / audit): `atlas-richie-component-desensitize`
- Storage, vector search, search, messaging, multi-protocol communication, and more
- Observability & governance: logging, tracing, SkyWalking, thread pools, etc.

### Open Source & Engineering

- License: Apache License 2.0.
- Added `NOTICE` and `CONTRIBUTING.md` for collaboration and compliance.
