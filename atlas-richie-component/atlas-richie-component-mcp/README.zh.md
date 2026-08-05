# Atlas Richie MCP 组件

`atlas-richie-component-mcp` 是 MCP Server/Client 的中台 Adapter 组件。

组件面向业务提供稳定的 Tool、Resource、Prompt、Client Operations 与调用上下文 API，
对内封装 MCP JSON-RPC、协议版本、传输、OAuth、缓存、订阅、MRTR、可观测与兼容细节。

当前已完成 P0，并进入 P1 实现；所有已建模块均已接入
`atlas-richie-component` reactor 与组件 BOM：

- `mcp-api`：稳定注解、调用上下文、Client Operations、业务 DTO 与异常；
- `mcp-protocol`：JSON-RPC 校验、归一化模型、版本协商、双时代 Dialect、
  `server/discover` codec 与带 SHA-256 校验的官方 Schema 离线快照；
- `mcp-schema`：稳定 JSON Schema Adapter API、Draft 2020-12 校验、meta-schema
  预校验、离线 `$ref` 安全边界与复杂度上限；
- `mcp-server-core`：Tool 注册、授权过滤、确定性列表与 revision，以及输入/输出
  Schema 校验、Tool Error/JSON-RPC Error 分流和异常脱敏的 Dispatcher；
- `mcp-transport-http`：无框架依赖的 modern Streamable HTTP POST、Origin、媒体类型、
  协议版本及标准镜像 Header 交叉验证内核；
- `mcp-testkit`：协议兼容性测试夹具。

HTTP Endpoint/SSE 框架绑定、STDIO Transport、OAuth Adapter 与 Server/Client Starter
按详细设计的 P1-P3 继续实现。

- [MCP 组件详细设计](docs/zh/mcp-component-design.md)
