# Atlas Richie MCP 组件

`atlas-richie-mcp-parent` 是 MCP Server/Client 的中台 Adapter 组件。

组件面向业务提供稳定的 Tool、Resource、Prompt、Client Operations 与调用上下文 API，
对内封装 MCP JSON-RPC、协议版本、传输、OAuth、缓存、订阅、MRTR、可观测与兼容细节。

当前已完成 P0，并已补齐 P1/P2/P3 的可运行基础能力；所有已建模块均已接入
`atlas-richie-component` reactor 与组件 BOM：

- `mcp-api`：稳定注解、调用上下文、Client Operations、业务 DTO 与异常；
- `mcp-protocol`：JSON-RPC 校验、归一化模型、版本协商、双时代 Dialect、
  `server/discover` codec 与带 SHA-256 校验的官方 Schema 离线快照；
- `mcp-schema`：稳定 JSON Schema Adapter API、Draft 2020-12 校验、meta-schema
  预校验、离线 `$ref` 安全边界与复杂度上限；
- `mcp-server-core`：Tool 注册、授权过滤、确定性列表与 revision，以及输入/输出
  Schema 校验、Tool Error/JSON-RPC Error 分流和异常脱敏的 Dispatcher；
- `mcp-transport-http`：无框架依赖的 modern Streamable HTTP POST、Origin、媒体类型、
  协议版本、标准镜像 Header、参数 Header、分页/缓存提示、取消/进度/订阅，以及可切换协议版本的 HTTP Client；
- `mcp-transport-stdio`：newline-delimited JSON framing、EOF 读取、stdout/stderr 约束及子进程优雅关闭；
- `mcp-security-oauth`：受保护资源/授权服务器 metadata、PKCE authorization request、registration、token refresh/introspection、client credentials、token manager、Bearer challenge 与安全 URI policy；
- `mcp-server-spring-boot-starter`：Server 自动配置、Tool/Resource/Prompt/Completion 注册收拢及 Spring MVC/WebFlux `/mcp` Endpoint，支持 JSON、request-scoped SSE、长连接订阅和 OAuth PRM；
- `mcp-client-spring-boot-starter`：按服务器配置创建 `McpOperations`，提供 HTTP Tool、Resource、Prompt、Completion 调用，首次 discovery 协商协议并分页加载 list 结果；支持 TTL cache、缓存主动失效与 OAuth Bearer token；
- `mcp-testkit`：协议兼容性测试夹具。

当前实现的边界仅包括：分布式 Cache/TokenStore 需要由中台注入，P4 conformance、业务 MCP Server
迁移和生产 observability 需要在具体部署中验收；组件本身不重复实现 OAuth Authorization Server。

- [MCP 组件详细设计](docs/zh/mcp-component-design.md)
