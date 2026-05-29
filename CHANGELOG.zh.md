**Languages:** [English](CHANGELOG.en.md) | [中文](CHANGELOG.md)

---

## 1.0.0-SNAPSHOT (2026-05-28)

> 当前版本为 **Atlas Richie Platform 首次公开基线版本**，采用全新架构与模块边界设计，基于最新的 JDK25 + Spring Boot 4.x 版本编写

### 架构与平台基线

- 建立 `atlas-richie-platform` 多模块工程基线，明确 `base / component / gateway-service / component-template` 分层边界。
- 在 `atlas-richie-base` 中沉淀基础依赖管理、共享契约与上下文能力，形成跨服务公共底座。
- 在 `atlas-richie-component` 中提供标准化组件能力矩阵，支持配置驱动与多实现切换。
- 在 `atlas-richie-gateway-service` 中形成统一网关服务能力，支持多模式部署（OpenAPI 接口网关、微服务接口网关、内网接口网关）与配置化切换。

### 技术栈与依赖版本

- `JDK`：25
- `Spring Boot`：4.0.x
- `Spring Cloud`：2025.1.1
- `Spring Cloud Alibaba`：2025.1.0.0
- `Spring Cloud Azure`：7.0.0
- `Spring AI`：2.0.0-M4
- `Spring AI Alibaba`：1.1.2.2
- `AgentScope`：1.0.11

### 组件能力概览（首发）

- 缓存与分布式能力：`atlas-richie-component-cache`
- 数据访问与多租户扩展：`atlas-richie-component-dao` / `atlas-richie-component-dao-tenant`
- HTTP 客户端统一抽象：`atlas-richie-component-http`
- 数据脱敏（API/日志/审计场景）：`atlas-richie-component-desensitize`
- 存储、向量检索、搜索、消息、多协议通信等通用组件能力
- 可观测与治理能力：日志、追踪、SkyWalking、线程池等

### 开源与工程治理

- 仓库许可证切换为 `Apache License 2.0`。
- 新增 `NOTICE` 与 `CONTRIBUTING.md`，统一开源协作入口与合规信息。
