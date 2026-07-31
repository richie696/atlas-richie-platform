# Atlas Richie Antivirus Service — 接口文档

> 本文档给出对外可用的全部接口（HTTP / REST 与 gRPC）以及字段说明，供真实业务方接入测试。
> 详细架构与运行说明见同目录的 `README.zh.md`。

---

## 1. 服务入口

容器启动后以下端口对外暴露（端口映射：容器 → 宿主机）：

| 类型 | 容器内端口 | 宿主机端口 | 路径 / 服务全限定名 | 协议 |
|------|------------|------------|----------------------|------|
| REST | 9600 | 18100 | `/internal/v1/scans` | `application/json` |
| Actuator | 9600 | 18100 | `/actuator/health` | — |
| gRPC | 9601 | 19601 | `cn.richie696.antivirus.grpc.v1.AntivirusService` | HTTP/2 + protobuf |

所有接口都要求从请求头（REST）/ Metadata（gRPC）拿到 `tenantId`：

| 协议 | Header / Metadata Key |
|------|------------------------|
| REST | `X-Tenant-Id: <tenant>` |
| gRPC | `x-tenant-id: <tenant>`（大小写不敏感） |

> Header 透传由 `atlas-richie-component-grpc` 的 `GrpcServerHeaderInterceptor` + `HeaderContextHolder` 完成；
> 业务代码只需调用 `HeaderContextHolder.getHeader("X-Tenant-Id")` 即可拿到。

---

## 2. 接口清单（REST ↔ gRPC 一一对应）

### 2.1 提交 URL 扫描任务

| 项 | REST | gRPC |
|---|---|---|
| **Method + Path** | `POST /internal/v1/scans` | `SubmitScan(ScanRequest) → ScanTask` |
| **请求体** | `SubmitScanRequest`（JSON） | `ScanRequest`（proto） |
| **响应体** | `ApiResponse<ScanTaskResponse>` | `ScanTask`（proto） |
| **HTTP 状态** | `202 Accepted` | — |

**REST 请求示例**（curl）：

```bash
curl -X POST http://127.0.0.1:18100/internal/v1/scans \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenant-a' \
  -d '{
    "fileId": "file-001",
    "downloadUrl": "https://example.com/abc.zip",
    "fileName": "abc.zip",
    "expectedSize": 1024,
    "expectedEtag": "abc"
  }'
```

**gRPC 请求示例**（grpcurl）：

```bash
grpcurl -plaintext \
  -H 'x-tenant-id: tenant-a' \
  -d '{
    "file_id": "file-001",
    "download_url": "https://example.com/abc.zip",
    "file_name": "abc.zip",
    "expected_size": 1024,
    "expected_etag": "abc"
  }' \
  127.0.0.1:19601 cn.richie696.antivirus.grpc.v1.AntivirusService/SubmitScan
```

### 2.2 提交本地文件扫描任务

| 项 | REST | gRPC |
|---|---|---|
| **Method + Path** | `POST /internal/v1/scans/local` | `SubmitLocalScan(LocalScanRequest) → ScanTask` |
| **请求体** | `SubmitLocalScanRequest`（JSON） | `LocalScanRequest`（proto） |
| **响应体** | `ApiResponse<ScanTaskResponse>` | `ScanTask`（proto） |
| **HTTP 状态** | `202 Accepted` | — |
| **前提** | `platform.antivirus.local.enabled=true` 且路径在 `allowed-paths` 白名单内 | 同左 |

**REST 请求示例**：

```bash
curl -X POST http://127.0.0.1:18100/internal/v1/scans/local \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenant-a' \
  -d '{"localPath":"/data/desktop/opencode.zip","fileName":"opencode.zip"}'
```

**gRPC 请求示例**：

```bash
grpcurl -plaintext \
  -H 'x-tenant-id: tenant-a' \
  -d '{"local_path":"/data/desktop/opencode.zip","file_name":"opencode.zip"}' \
  127.0.0.1:19601 cn.richie696.antivirus.grpc.v1.AntivirusService/SubmitLocalScan
```

### 2.3 查询扫描结果

| 项 | REST | gRPC |
|---|---|---|
| **Method + Path** | `GET /internal/v1/scans/{taskId}` | `GetScanStatus(GetScanStatusRequest) → ScanTask` |
| **路径/请求参数** | `taskId`（路径变量） | `GetScanStatusRequest{task_id, tenant_id}` |
| **响应** | `ApiResponse<ScanTaskResponse>` | `ScanTask`（proto） |
| **找不到时** | `ApiResponse{success=false, msg="无此任务"}` | gRPC `NOT_FOUND` 状态码 |

**REST 请求示例**：

```bash
curl -X GET http://127.0.0.1:18100/internal/v1/scans/0896936c-21c5-4f28-adc8-1c87d8f27942 \
  -H 'X-Tenant-Id: tenant-a'
```

**gRPC 请求示例**：

```bash
grpcurl -plaintext \
  -H 'x-tenant-id: tenant-a' \
  -d '{"task_id":"0896936c-21c5-4f28-adc8-1c87d8f27942","tenant_id":"tenant-a"}' \
  127.0.0.1:19601 cn.richie696.antivirus.grpc.v1.AntivirusService/GetScanStatus
```

---

## 3. 响应字段（REST `ScanTaskResponse` ↔ gRPC `ScanTask`）

| 字段（REST） | 字段（gRPC） | 类型 | 说明 |
|---|---|---|---|
| `taskId` | `task_id` | string | UUID，业务侧拿来轮询的唯一 ID |
| `fileId` | `file_id` | string | 业务方传入的文件标识 |
| `tenantId` | `tenant_id` | string | 租户标识（从 `X-Tenant-Id` 头拿） |
| `downloadUrl` | `download_url` | string | 仅 URL 模式有值；本地模式为空 |
| `localPath` | `local_path` | string | 仅本地模式有值；URL 模式为空 |
| `fileName` | `file_name` | string | 文件名 |
| `expectedSize` | `expected_size` | int64 | 期望字节数（业务方传入） |
| `expectedEtag` | `expected_etag` | string | 期望 ETag（URL 模式校验用） |
| `actualSize` | `actual_size` | int64 | 实际扫描字节数 |
| `status` | `status` | enum `ScanStatus` | 见下表 |
| `sha256` | `sha256` | string | 文件 SHA-256 |
| `detectedMimeType` | `detected_mime_type` | string | Tika 探测的 MIME |
| `threatName` | `threat_name` | string | INFECTED 时为病毒名，CLEAN/FAILED 时为空 |
| `engineVersion` | `engine_version` | string | 例如 `ClamAV 1.4.5` |
| `signatureVersion` | `signature_version` | string | 例如 `28077`（clamd VERSION 第二段） |
| `errorMessage` | `error_message` | string | FAILED 时为失败原因；其他情况为空 |
| `submittedAt` | `submitted_at_epoch_ms` | ISO8601 / int64 | 提交时间（gRPC 用毫秒时间戳） |
| `startedAt` | `started_at_epoch_ms` | ISO8601 / int64 | 实际开始扫描时间 |
| `completedAt` | `completed_at_epoch_ms` | ISO8601 / int64 | 扫描完成时间 |

### `ScanStatus` 枚举

| 值 | gRPC 数值 | 含义 |
|---|---|---|
| `SCAN_STATUS_UNSPECIFIED` | 0 | proto3 默认值，正常业务不会出现 |
| `PENDING` | 1 | 已受理入队，Stream 消费侧尚未开始 |
| `SCANNING` | 2 | Stream Worker 已领取租约，正在执行 clamd INSTREAM |
| `CLEAN` | 3 | 扫描通过；业务方可放行文件 |
| `INFECTED` | 4 | 检出恶意载荷；`threatName` 非空 |
| `FAILED` | 5 | 异常或下载/读文件失败；`errorMessage` 非空 |

### REST 顶层 `ApiResponse<T>`

```json
{ "success": true, "data": <ScanTaskResponse>, "msg": "ok" }
{ "success": false, "data": null,   "msg": "无此任务" }
```

---

## 4. proto 文件

```proto
syntax = "proto3";

package cn.richie696.antivirus.grpc.v1;

option java_package = "cn.richie696.antivirus.grpc.v1";
option java_multiple_files = true;
option java_outer_classname = "AntivirusProto";

service AntivirusService {
  rpc SubmitScan(ScanRequest) returns (ScanTask);
  rpc SubmitLocalScan(LocalScanRequest) returns (ScanTask);
  rpc GetScanStatus(GetScanStatusRequest) returns (ScanTask);
}

message ScanRequest {
  string file_id = 1;
  string download_url = 2;
  string file_name = 3;
  optional int64 expected_size = 4;
  string expected_etag = 5;
}

message LocalScanRequest {
  string local_path = 1;
  string file_name = 2;
  optional int64 expected_size = 3;
}

message GetScanStatusRequest {
  string task_id = 1;
  string tenant_id = 2;
}

message ScanTask {
  string task_id = 1;
  string file_id = 2;
  string tenant_id = 3;
  string download_url = 4;
  string local_path = 5;
  string file_name = 6;
  int64 expected_size = 7;
  string expected_etag = 8;
  int64 actual_size = 9;
  ScanStatus status = 10;
  string sha256 = 11;
  string detected_mime_type = 12;
  string threat_name = 13;
  string engine_version = 14;
  string signature_version = 15;
  string error_message = 16;
  int64 submitted_at_epoch_ms = 17;
  int64 started_at_epoch_ms = 18;
  int64 completed_at_epoch_ms = 19;
}

enum ScanStatus {
  SCAN_STATUS_UNSPECIFIED = 0;
  PENDING = 1;
  SCANNING = 2;
  CLEAN = 3;
  INFECTED = 4;
  FAILED = 5;
}
```

---

## 5. 切换 REST ↔ gRPC

通过 Nacos 配置 `platform.antivirus.grpc.enabled` 控制：

| 配置 | REST (9600) | gRPC (9601) |
|------|-------------|-------------|
| `grpc.enabled=true` | ✓ 启动 | ✓ 启动 |
| `grpc.enabled=false` | ✓ 启动 | ✗ 不启动 |

修改 Nacos `platform-antivirus.yaml` 后需要重启容器（gRPC server 生命周期不参与 `?refreshEnabled=true` 热刷新）。

---

## 6. 健康检查 / 反射

| 端点 | 用途 |
|------|------|
| `GET http://127.0.0.1:18100/actuator/health` | REST 服务健康 |
| `grpcurl -plaintext 127.0.0.1:19601 grpc.health.v1.Health/Check` | gRPC 服务健康 |
| `grpcurl -plaintext 127.0.0.1:19601 list` | 列出所有 gRPC 服务（含 `cn.richie696.antivirus.grpc.v1.AntivirusService`、`grpc.health.v1.Health`、`grpc.reflection.v1alpha.ServerReflection`） |

---

## 7. 端到端测试剧本

```bash
# === A. REST 全流程 ===
RESP=$(curl -s -X POST http://127.0.0.1:18100/internal/v1/scans/local \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: tenant-a' \
  -d '{"localPath":"/data/desktop/opencode.zip"}')
TASK_ID=$(echo "$RESP" | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["taskId"])')
echo "REST 提交成功 taskId=$TASK_ID"
curl -s "http://127.0.0.1:18100/internal/v1/scans/$TASK_ID" -H 'X-Tenant-Id: tenant-a' | python3 -m json.tool

# === B. gRPC 全流程 ===
grpcurl -plaintext \
  -H 'x-tenant-id: tenant-a' \
  -d '{"local_path":"/data/desktop/opencode.zip"}' \
  127.0.0.1:19601 cn.richie696.antivirus.grpc.v1.AntivirusService/SubmitLocalScan
```

两种协议拿到的 `taskId` 都能在 Redis 缓存里查询到同一份结果（数据层共用 `ScanTaskRepository`）。

---

## 8. 启动容器命令

```bash
docker run -d --name atlas-richie-antivirus-service \
  -p 18100:9600 \
  -p 19601:9601 \
  -v $HOME/Desktop:/data/desktop:ro \
  -e NACOS_SERVER_ADDR=host.docker.internal:8848 \
  atlas-richie-antivirus-service:local
```

仅需一个环境变量 `NACOS_SERVER_ADDR`，其他配置（Redis / 任务参数 / 扫描引擎 / gRPC 开关）全部从 Nacos 拉取。
