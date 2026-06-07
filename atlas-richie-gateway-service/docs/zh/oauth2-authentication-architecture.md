# 第三方系统 OAuth2.0 Client Credentials 认证架构设计文档

## 1. 概述

### 1.1 背景

为第三方系统提供统一的接口访问认证机制，采用 OAuth2.0 Client Credentials 模式，实现安全、高效的 API 访问控制。

### 1.2 设计目标

- **安全性**：第三方系统与自有系统完全隔离，互不影响
- **性能**：网关仅从 Redis 缓存读取配置，零数据库访问
- **可维护性**：通过管理界面统一管理第三方客户端配置
- **可扩展性**：支持多第三方系统接入，配置灵活

### 1.3 技术选型

- **认证协议**：OAuth2.0 Client Credentials 模式（RFC 6749）
- **令牌格式**：
  - **access_token**：JWT (JSON Web Token, RFC 7519) 格式，有效期 1 小时
  - **refresh_token**：随机字符串格式，存储在 Redis，有效期 30 天
  - 使用 HMAC256 签名算法确保 access_token 安全
  - 与自有系统 JWT token 技术栈保持一致，复用现有工具类
- **续签机制**：标准 OAuth2.0 refresh_token 方案
  - 第三方系统主动调用刷新接口获取新 token
  - 符合 OAuth2.0 标准实践，第三方系统可主动控制续签时机
- **配置管理**：general-service + 数据库 + Redis 缓存
- **数据库版本管理**：Liquibase
- **部署方案**：第三方网关独立部署

---

## 2. 整体架构

### 2.1 系统架构图

```mermaid
graph TB
    subgraph "管理侧"
        A[管理页面<br/>HTML/前端] --> B[general-service<br/>管理接口]
        B --> C1[(数据库<br/>third_party_company)]
        B --> C2[(数据库<br/>third_party_client)]
        B --> D[(Redis 缓存<br/>实时同步)]
        E[定时同步任务<br/>Scheduled Task] --> D
        C1 --> E
        C2 --> E
    end
    
    subgraph "网关侧"
        F[第三方网关<br/>独立部署] --> D
        F --> G[InterfaceAuthFilter<br/>OAuth2.0 鉴权]
        G --> H[后端服务集群]
    end
    
    subgraph "第三方系统"
        I[第三方系统A] --> F
        J[第三方系统B] --> F
        K[第三方系统C] --> F
    end
    
    style A fill:#e1f5ff
    style B fill:#e1f5ff
    style C1 fill:#fff4e1
    style C2 fill:#fff4e1
    style D fill:#ffe1f5
    style E fill:#e1f5ff
    style F fill:#e1ffe1
    style G fill:#e1ffe1
    style H fill:#f5e1ff
```

### 2.2 核心组件说明

| 组件 | 职责 | 技术实现 |
|------|------|----------|
| **管理页面** | 提供第三方公司信息和客户端配置的增删改查界面 | HTML + JavaScript / Vue / React |
| **general-service** | 管理第三方公司信息和客户端配置，提供 REST API | Spring Boot + MyBatis-Plus |
| **数据库** | 持久化存储第三方公司信息和客户端配置 | MySQL + Liquibase |
| **Redis 缓存** | 存储客户端配置，供网关快速读取 | Redis |
| **定时同步任务** | 定时全量同步数据库到 Redis（兜底保障） | Spring @Scheduled |
| **第三方网关** | 独立部署的网关服务，处理第三方系统请求 | Spring Cloud Gateway |
| **InterfaceAuthFilter** | OAuth2.0 认证过滤器，验证 access_token | Gateway Filter |

---

## 3. 管理侧方案

### 3.1 业务流程

```mermaid
flowchart TD
    A[管理员登录管理页面] --> B{选择操作}
    
    B -->|创建公司| C1[填写公司信息]
    C1 --> D1[提交创建请求]
    D1 --> E1[general-service 接收请求]
    E1 --> F1[验证统一社会信用代码唯一性]
    F1 --> G1[保存公司信息到数据库]
    G1 --> H1[返回公司ID]
    H1 --> I1[显示创建成功]
    
    B -->|创建客户端| C2[选择关联公司]
    C2 --> C3[填写客户端信息]
    C3 --> D2[提交创建请求]
    D2 --> E2[general-service 接收请求]
    E2 --> F2[验证公司ID存在]
    F2 --> G2[生成 clientId 和 clientSecret]
    G2 --> H2[加密 clientSecret]
    H2 --> I2[保存到数据库]
    I2 --> J2[实时同步到 Redis]
    J2 --> K2[返回 clientId 和 clientSecret]
    K2 --> L2[显示给管理员]
    
    B -->|更新公司| L1[选择要更新的公司]
    L1 --> M1[修改公司信息]
    M1 --> N1[提交更新请求]
    N1 --> O1[general-service 接收请求]
    O1 --> P1[更新数据库]
    P1 --> Q1[返回更新结果]
    
    B -->|更新客户端| L2[选择要更新的客户端]
    L2 --> M2[修改配置信息]
    M2 --> N2[提交更新请求]
    N2 --> O2[general-service 接收请求]
    O2 --> P2[更新数据库]
    P2 --> Q2[实时同步到 Redis]
    Q2 --> R2[返回更新结果]
    
    B -->|删除公司| S1[选择要删除的公司]
    S1 --> T1[检查是否有关联客户端]
    T1 -->|有关联| U1[提示先删除关联客户端]
    T1 -->|无关联| V1[确认删除]
    V1 --> W1[general-service 接收请求]
    W1 --> X1[逻辑删除数据库记录]
    X1 --> Y1[返回删除结果]
    
    B -->|删除客户端| S2[选择要删除的客户端]
    S2 --> T2[确认删除]
    T2 --> U2[general-service 接收请求]
    U2 --> V2[逻辑删除数据库记录]
    V2 --> W2[从 Redis 删除缓存]
    W2 --> X2[返回删除结果]
    
    B -->|重置密钥| Y[选择要重置的客户端]
    Y --> Z[生成新的 clientSecret]
    Z --> AA[更新数据库]
    AA --> AB[实时同步到 Redis]
    AB --> AC[返回新密钥]
    
    style A fill:#e1f5ff
    style E1 fill:#fff4e1
    style E2 fill:#fff4e1
    style G1 fill:#fff4e1
    style I2 fill:#fff4e1
    style J2 fill:#ffe1f5
    style O1 fill:#fff4e1
    style O2 fill:#fff4e1
    style P1 fill:#fff4e1
    style P2 fill:#fff4e1
    style Q2 fill:#ffe1f5
    style W1 fill:#fff4e1
    style W2 fill:#fff4e1
    style X1 fill:#fff4e1
    style V2 fill:#fff4e1
    style W2 fill:#ffe1f5
```

### 3.2 数据流图

```mermaid
flowchart LR
    subgraph "管理操作"
        A[管理员操作] --> B[管理页面]
    end
    
    subgraph "general-service"
        B --> C1[ThirdPartyCompanyController]
        B --> C2[ThirdPartyClientController]
        C1 --> D1[ThirdPartyCompanyService]
        C2 --> D2[ThirdPartyClientService]
        D1 --> E1[ThirdPartyCompanyMapper]
        D2 --> E2[ThirdPartyClientMapper]
    end
    
    subgraph "数据存储"
        E1 --> F1[(数据库<br/>third_party_company)]
        E2 --> F2[(数据库<br/>third_party_client)]
        D2 --> G[("Redis 缓存<br/>third-party-client:{clientId}")]
    end
    
    subgraph "定时同步"
        H[定时同步任务<br/>每5分钟] --> F
        H --> G
    end
    
    style A fill:#e1f5ff
    style B fill:#e1f5ff
    style C1 fill:#fff4e1
    style C2 fill:#fff4e1
    style D1 fill:#fff4e1
    style D2 fill:#fff4e1
    style E1 fill:#fff4e1
    style E2 fill:#fff4e1
    style F1 fill:#fff4e1
    style F2 fill:#fff4e1
    style G fill:#ffe1f5
    style H fill:#e1f5ff
```

### 3.3 管理侧时序图

```mermaid
sequenceDiagram
    participant Admin as 管理员
    participant UI as 管理页面
    participant CompanyController as ThirdPartyCompanyController
    participant ClientController as ThirdPartyClientController
    participant CompanyService as ThirdPartyCompanyService
    participant ClientService as ThirdPartyClientService
    participant CompanyMapper as ThirdPartyCompanyMapper
    participant ClientMapper as ThirdPartyClientMapper
    participant DB as 数据库
    participant Redis as Redis缓存
    participant Task as 定时同步任务
    
    Note over Admin,Task: 创建公司流程
    Admin->>UI: 填写公司信息并提交
    UI->>CompanyController: POST /api/gateway/third-party-company
    CompanyController->>CompanyService: create(dto)
    CompanyService->>CompanyService: 验证统一社会信用代码唯一性
    CompanyService->>CompanyMapper: insert(company)
    CompanyMapper->>DB: INSERT INTO third_party_company
    DB-->>CompanyMapper: 返回插入结果
    CompanyMapper-->>CompanyService: 返回 company 对象
    CompanyService-->>CompanyController: 返回公司ID
    CompanyController-->>UI: 返回创建结果
    UI-->>Admin: 显示创建成功
    
    Note over Admin,Task: 创建客户端流程
    Admin->>UI: 选择公司并填写客户端信息
    UI->>ClientController: POST /api/gateway/third-party-client
    ClientController->>ClientService: create(dto)
    ClientService->>ClientService: 验证 companyId 存在
    ClientService->>ClientService: 生成 clientId 和 clientSecret
    ClientService->>ClientService: 加密 clientSecret
    ClientService->>ClientMapper: insert(client)
    ClientMapper->>DB: INSERT INTO third_party_client
    DB-->>ClientMapper: 返回插入结果
    ClientMapper-->>ClientService: 返回 client 对象
    ClientService->>Redis: 实时同步配置到缓存
    Redis-->>ClientService: 同步成功
    ClientService-->>ClientController: 返回 clientId 和 clientSecret
    ClientController-->>UI: 返回创建结果
    UI-->>Admin: 显示 clientId 和 clientSecret
    
    Note over Admin,Task: 定时同步任务（兜底保障）
    Task->>DB: 查询所有启用的客户端
    DB-->>Task: 返回客户端列表
    Task->>Task: 解密 clientSecret
    Task->>Redis: 全量同步到缓存
    Redis-->>Task: 同步完成
```

---

## 4. 网关侧方案

### 4.1 业务流程

```mermaid
flowchart TD
    A[第三方系统请求] --> B[第三方网关接收请求]
    B --> C{请求类型}
    
    C -->|获取 access_token| D[OAuth2.0 Token 接口]
    D --> E{grant_type}
    E -->|client_credentials| F[InterfaceAuthFilter 验证]
    F --> G{验证 clientId 和 clientSecret}
    G -->|验证失败| H[返回 401 Unauthorized]
    G -->|验证成功| I[从 Redis 读取客户端配置]
    I --> J[生成 JWT access_token 和 refresh_token]
    J --> K[返回 access_token、refresh_token 和过期时间]
    
    E -->|refresh_token| L[验证 refresh_token]
    L --> M{refresh_token 有效?}
    M -->|无效| N[返回 401 Unauthorized]
    M -->|有效| O[生成新的 access_token 和 refresh_token]
    O --> P[返回新的 token]
    
    C -->|访问业务接口| Q[业务接口请求]
    Q --> R[InterfaceAuthFilter 拦截]
    R --> S{检查 access_token}
    S -->|缺少 token| T[返回 401 Unauthorized]
    S -->|token 无效| U[返回 401 Unauthorized]
    S -->|token 过期| V[返回 401 Unauthorized<br/>提示使用 refresh_token]
    S -->|token 有效| W[继续处理]
    W --> X[转发到后端服务]
    X --> Y[返回业务结果]
    
    style A fill:#e1ffe1
    style B fill:#e1ffe1
    style D fill:#e1ffe1
    style F fill:#e1ffe1
    style I fill:#ffe1f5
    style J fill:#e1ffe1
    style L fill:#e1ffe1
    style R fill:#e1ffe1
    style X fill:#f5e1ff
```

### 4.2 数据流图

```mermaid
flowchart LR
    subgraph "第三方系统"
        A[第三方系统] --> B[HTTP 请求]
    end
    
    subgraph "第三方网关"
        B --> C[InterfaceAuthFilter]
        C --> D{请求类型}
        D -->|获取 token| E[验证 clientId/Secret]
        D -->|刷新 token| F[验证 refresh_token]
        D -->|业务请求| G[验证 access_token]
        E --> H[从 Redis 读取配置]
        F --> I[(Redis 缓存<br/>refresh_token)]
        G --> H
        H --> J[(Redis 缓存<br/>客户端配置)]
        E --> K[生成 JWT access_token<br/>和 refresh_token]
        F --> L[生成新的 token]
        G --> M{token 有效?}
        M -->|是| N[继续处理]
        M -->|否| O[返回 401]
        K --> P[存储 refresh_token 到 Redis]
        L --> P
    end
    
    subgraph "后端服务"
        N --> Q[后端服务集群]
        Q --> R[返回业务结果]
    end
    
    style A fill:#e1ffe1
    style C fill:#e1ffe1
    style H fill:#ffe1f5
    style J fill:#ffe1f5
    style I fill:#ffe1f5
    style K fill:#e1ffe1
    style Q fill:#f5e1ff
```

### 4.3 网关侧时序图

```mermaid
sequenceDiagram
    participant ThirdParty as 第三方系统
    participant Gateway as 第三方网关
    participant Filter as InterfaceAuthFilter
    participant Redis as Redis缓存
    participant Backend as 后端服务
    
    Note over ThirdParty,Backend: 获取 access_token 流程
    ThirdParty->>Gateway: POST /api/oauth2/token<br/>grant_type=client_credentials<br/>client_id=xxx<br/>client_secret=yyy
    Gateway->>Filter: 路由到 Token 接口
    Filter->>Redis: 读取客户端配置<br/>third-party-client:{clientId}
    Redis-->>Filter: 返回客户端配置
    Filter->>Filter: 验证 clientSecret
    alt 验证失败
        Filter-->>ThirdParty: 401 Unauthorized
    else 验证成功
        Filter->>Filter: 生成 JWT access_token<br/>生成 refresh_token
        Filter->>Redis: 存储 refresh_token<br/>refresh-token:{token}
        Redis-->>Filter: 存储成功
        Filter-->>ThirdParty: 返回 access_token、refresh_token<br/>和过期时间
    end
    
    Note over ThirdParty,Backend: 刷新 access_token 流程
    ThirdParty->>Gateway: POST /api/oauth2/token<br/>grant_type=refresh_token<br/>refresh_token=zzz
    Gateway->>Filter: 路由到 Token 接口
    Filter->>Redis: 验证 refresh_token<br/>refresh-token:{token}
    Redis-->>Filter: 返回验证结果
    alt refresh_token 无效或过期
        Filter-->>ThirdParty: 401 Unauthorized
    else refresh_token 有效
        Filter->>Filter: 生成新的 access_token<br/>生成新的 refresh_token
        Filter->>Redis: 删除旧 refresh_token<br/>存储新 refresh_token
        Filter-->>ThirdParty: 返回新的 access_token<br/>和 refresh_token
    end
    
    Note over ThirdParty,Backend: 访问业务接口流程
    ThirdParty->>Gateway: GET /api/business/xxx<br/>Authorization: Bearer {access_token}
    Gateway->>Filter: 拦截请求
    Filter->>Filter: 提取 access_token
    Filter->>Filter: 验证 token 有效性
    alt token 无效或过期
        Filter-->>ThirdParty: 401 Unauthorized<br/>提示使用 refresh_token
    else token 有效
        Filter->>Backend: 转发请求到后端服务
        Backend-->>Filter: 返回业务结果
        Filter-->>ThirdParty: 返回业务结果
    end
```

---

## 5. 第三方系统与网关交互流程

### 5.1 完整交互泳道图

```mermaid
sequenceDiagram
    participant Admin as 管理员
    participant GeneralService as general-service
    participant DB as 数据库
    participant Redis as Redis缓存
    participant ThirdParty as 第三方系统
    participant Gateway as 第三方网关
    participant Backend as 后端服务
    
    Note over Admin,Backend: 阶段1：配置管理
    Admin->>GeneralService: 创建第三方公司
    GeneralService->>DB: 保存公司信息
    DB-->>GeneralService: 返回公司ID
    GeneralService-->>Admin: 返回公司ID
    
    Admin->>GeneralService: 创建第三方客户端（关联公司）
    GeneralService->>GeneralService: 验证公司ID存在
    GeneralService->>GeneralService: 生成 clientId 和 clientSecret
    GeneralService->>DB: 保存客户端配置
    DB-->>GeneralService: 保存成功
    GeneralService->>Redis: 实时同步配置
    Redis-->>GeneralService: 同步成功
    GeneralService-->>Admin: 返回 clientId 和 clientSecret
    
    Note over Admin,Backend: 阶段2：获取 access_token
    ThirdParty->>Gateway: POST /api/oauth2/token<br/>grant_type=client_credentials<br/>client_id=xxx<br/>client_secret=yyy
    Gateway->>Redis: 读取客户端配置<br/>third-party-client:xxx
    Redis-->>Gateway: 返回客户端配置
    Gateway->>Gateway: 验证 clientSecret
    alt 验证成功
        Gateway->>Gateway: 生成 JWT access_token<br/>(有效期1小时)
        Gateway-->>ThirdParty: 返回 access_token<br/>{"access_token":"xxx",<br/>"expires_in":3600}
    else 验证失败
        Gateway-->>ThirdParty: 401 Unauthorized
    end
    
    Note over Admin,Backend: 阶段3：刷新 access_token（主动续签）
    ThirdParty->>Gateway: POST /api/oauth2/token<br/>grant_type=refresh_token<br/>refresh_token=zzz
    Gateway->>Redis: 验证 refresh_token
    Redis-->>Gateway: 验证结果
    alt refresh_token 有效
        Gateway->>Gateway: 生成新的 access_token<br/>和 refresh_token
        Gateway->>Redis: 更新 refresh_token
        Gateway-->>ThirdParty: 返回新的 access_token<br/>和 refresh_token
    else refresh_token 无效
        Gateway-->>ThirdParty: 401 Unauthorized
    end
    
    Note over Admin,Backend: 阶段4：访问业务接口
    ThirdParty->>Gateway: GET /api/business/xxx<br/>Authorization: Bearer {access_token}
    Gateway->>Gateway: 验证 access_token
    alt token 有效
        Gateway->>Backend: 转发请求到后端服务
        Backend-->>Gateway: 返回业务结果
        Gateway-->>ThirdParty: 返回业务结果
    else token 无效或过期
        Gateway-->>ThirdParty: 401 Unauthorized<br/>提示使用 refresh_token 刷新
    end
    
    Note over Admin,Backend: 阶段5：定时同步（兜底保障）
    GeneralService->>DB: 查询所有启用的客户端
    DB-->>GeneralService: 返回客户端列表
    GeneralService->>Redis: 全量同步到缓存
    Redis-->>GeneralService: 同步完成
```

### 5.2 第三方系统交互时序图

```mermaid
sequenceDiagram
    participant ThirdParty as 第三方系统
    participant Gateway as 第三方网关
    participant Redis as Redis缓存
    participant Backend as 后端服务
    
    Note over ThirdParty,Backend: 首次访问：获取 access_token 和 refresh_token
    ThirdParty->>Gateway: 1. POST /api/oauth2/token<br/>Body: grant_type=client_credentials<br/>client_id=xxx<br/>client_secret=yyy
    Gateway->>Redis: 2. 读取客户端配置
    Redis-->>Gateway: 3. 返回配置
    Gateway->>Gateway: 4. 验证 clientSecret
    Gateway->>Gateway: 5. 生成 JWT access_token<br/>生成 refresh_token
    Gateway->>Redis: 6. 存储 refresh_token<br/>refresh-token:{token}
    Gateway-->>ThirdParty: 7. 返回 token<br/>{"access_token":"eyJ...",<br/>"refresh_token":"zzz...",<br/>"token_type":"Bearer",<br/>"expires_in":3600}
    
    Note over ThirdParty,Backend: 业务请求：使用 access_token
    ThirdParty->>Gateway: 8. GET /api/business/xxx<br/>Header: Authorization: Bearer {access_token}
    Gateway->>Gateway: 9. 提取并验证 access_token
    alt token 有效且未过期
        Gateway->>Backend: 10. 转发请求到后端服务<br/>Header: X-Third-Party-Client-Id
        Backend-->>Gateway: 11. 返回业务结果
        Gateway-->>ThirdParty: 12. 返回业务结果
    else token 无效或过期
        Gateway-->>ThirdParty: 13. 401 Unauthorized<br/>{"error":"invalid_token",<br/>"error_description":"Token expired"}
    end
    
    Note over ThirdParty,Backend: token 即将过期：主动刷新（推荐）
    ThirdParty->>Gateway: 14. POST /api/oauth2/token<br/>grant_type=refresh_token<br/>refresh_token=zzz
    Gateway->>Redis: 15. 验证 refresh_token
    Redis-->>Gateway: 16. 验证结果
    alt refresh_token 有效
        Gateway->>Gateway: 17. 生成新的 access_token<br/>生成新的 refresh_token
        Gateway->>Redis: 18. 删除旧 refresh_token<br/>存储新 refresh_token
        Gateway-->>ThirdParty: 19. 返回新的 token<br/>{"access_token":"new_xxx",<br/>"refresh_token":"new_zzz",<br/>"expires_in":3600}
    else refresh_token 无效或过期
        Gateway-->>ThirdParty: 20. 401 Unauthorized<br/>{"error":"invalid_grant"}
    end
    
    Note over ThirdParty,Backend: 使用新 token 继续访问
    ThirdParty->>Gateway: 21. GET /api/business/xxx<br/>Header: Authorization: Bearer {new_access_token}
    Gateway->>Gateway: 22. 验证新 access_token
    Gateway->>Backend: 23. 转发请求
    Backend-->>Gateway: 24. 返回业务结果
    Gateway-->>ThirdParty: 25. 返回业务结果
```

---

## 6. 数据模型设计

### 6.1 数据库表结构

#### 6.1.1 第三方公司信息表

**表名：`third_party_company`**

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | BIGINT | 主键ID | PRIMARY KEY |
| company_name | VARCHAR(256) | 公司名称 | NOT NULL |
| company_code | VARCHAR(64) | 统一社会信用代码 | UNIQUE, NOT NULL |
| contact_name | VARCHAR(64) | 联系人姓名 | |
| contact_email | VARCHAR(128) | 联系人邮箱 | |
| contact_phone | VARCHAR(32) | 联系人电话 | |
| company_status | VARCHAR(32) | 公司状态 | DEFAULT 'NORMAL' |
| create_id | VARCHAR(64) | 创建人ID | |
| create_time | DATETIME | 创建时间 | |
| update_id | VARCHAR(64) | 更新人ID | |
| update_time | DATETIME | 更新时间 | |
| deleted | TINYINT(1) | 删除标识 | DEFAULT 0 |

**索引：**
- `idx_company_code` (company_code) - 统一社会信用代码唯一索引
- `idx_company_name` (company_name) - 公司名称索引
- `idx_company_status` (company_status) - 公司状态索引
- `idx_deleted` (deleted) - 删除标识索引

**说明：**
- 一个公司可以关联多个客户端（client_id）
- 公司信息相对稳定，变更频率较低
- 删除公司前需检查是否有关联的客户端

#### 6.1.2 第三方客户端表

**表名：`third_party_client`**

| 字段名 | 类型 | 说明 | 约束 |
|--------|------|------|------|
| id | BIGINT | 主键ID | PRIMARY KEY |
| company_id | BIGINT | 关联的公司ID | FOREIGN KEY, NOT NULL |
| client_id | VARCHAR(64) | 客户端ID（appId） | UNIQUE, NOT NULL |
| client_secret | VARCHAR(256) | 客户端密钥（加密存储） | NOT NULL |
| client_name | VARCHAR(128) | 客户端名称 | NOT NULL |
| description | VARCHAR(512) | 描述信息 | |
| scopes | VARCHAR(512) | 权限范围（JSON数组或逗号分隔） | |
| enabled | TINYINT(1) | 是否启用 | DEFAULT 1 |
| ip_whitelist | TEXT | IP白名单（JSON数组） | |
| token_valid_duration | INT | Token有效期（小时） | DEFAULT 24 |
| expiration_renewal_time | INT | 到期前续签时间（分钟） | DEFAULT 60 |
| contact_name | VARCHAR(64) | 联系人姓名 | |
| contact_email | VARCHAR(128) | 联系人邮箱 | |
| contact_phone | VARCHAR(32) | 联系人电话 | |
| create_id | VARCHAR(64) | 创建人ID | |
| create_time | DATETIME | 创建时间 | |
| update_id | VARCHAR(64) | 更新人ID | |
| update_time | DATETIME | 更新时间 | |
| deleted | TINYINT(1) | 删除标识 | DEFAULT 0 |

**索引：**
- `idx_company_id` (company_id) - 公司ID索引（外键）
- `idx_client_id` (client_id) - 客户端ID唯一索引
- `idx_enabled` (enabled) - 启用状态索引
- `idx_deleted` (deleted) - 删除标识索引

**外键约束：**
- `fk_client_company` (company_id) REFERENCES `third_party_company`(id)

**说明：**
- 每个客户端必须关联一个公司
- 一个公司可以拥有多个客户端
- 删除公司前需先删除或转移所有关联的客户端

### 6.2 Redis 缓存结构

#### 6.2.1 客户端配置缓存

**Key 格式：** `third-party-client:{clientId}`

**Value 结构（Hash）：**
```
third-party-client:client-001
  ├── clientId: "client-001"
  ├── clientSecret: "plain_secret_xxx"  (明文，用于验证)
  ├── clientName: "第三方系统A"
  ├── scopes: "read,write"
  ├── enabled: "true"
  ├── ipWhitelist: "[\"192.168.1.1\", \"10.0.0.0/8\"]"
  ├── tokenValidDuration: "1"  (小时)
  └── refreshTokenValidDuration: "720"  (小时，30天)
```

#### 6.2.2 Refresh Token 缓存

**Key 格式：** `refresh-token:{token_value}`

**Value 结构（Hash）：**
```
refresh-token:abc123def456...
  ├── clientId: "client-001"
  ├── scopes: "read,write"
  ├── issuedAt: "1234567890"
  ├── expiresAt: "1237246290"  (30天后)
  └── enabled: "true"
```

**TTL 设置**：与 refresh_token 有效期一致（如 30 天），Redis 自动过期清理

### 6.3 JWT Token 结构

#### 6.3.1 Token 格式说明

OAuth2.0 Client Credentials 模式签发的 `access_token` 采用 **JWT (JSON Web Token)** 格式。虽然 OAuth2.0 规范（RFC 6749）并未强制要求 access_token 必须是 JWT 格式，但使用 JWT 格式具有以下优势：

- **自包含性**：Token 本身包含客户端信息（client_id、scopes 等），无需查询数据库即可验证
- **无状态性**：网关无需存储 token 状态，支持水平扩展和高并发
- **可验证性**：通过 HMAC256 签名算法验证 token 的完整性和真实性
- **可解析性**：可直接从 token 中提取客户端信息和权限范围
- **技术统一**：与自有系统的 JWT token 保持技术栈一致，复用现有 `JwtUtils` 工具类

#### 6.3.2 JWT 结构定义

**Header（头部）：**
```json
{
  "alg": "HS256",    // 签名算法：HMAC SHA256
  "typ": "JWT"       // Token 类型：JSON Web Token
}
```

**Payload（载荷）：**
```json
{
  "client_id": "client-001",              // 客户端ID（必需）
  "scopes": ["read", "write"],            // 权限范围（必需）
  "iat": 1234567890,                      // 签发时间（Issued At）
  "exp": 1234654290,                      // 过期时间（Expiration Time）
  "jti": "550e8400-e29b-41d4-a716-446655440000",  // JWT ID（用于防重放）
  "iss": "Richie Gateway",                // 签发者（Issuer）
  "sub": "client_credentials",            // 主题（Subject），标识 OAuth2.0 模式
  "aud": "api.abc.com"                 // 受众（Audience），API 服务地址
}
```

#### 6.3.5 Refresh Token 说明

**Refresh Token 特点：**
- **格式**：随机字符串（非 JWT），存储在 Redis 中
- **有效期**：通常比 access_token 更长（如 30 天）
- **用途**：用于获取新的 access_token，无需重新提供 client_secret
- **存储位置**：Redis，Key 格式：`refresh-token:{token_value}`
- **安全要求**：refresh_token 必须安全存储，不能泄露

**Refresh Token 存储结构（Redis）：**
```
refresh-token:abc123def456...
  ├── clientId: "client-001"
  ├── scopes: "read,write"
  ├── issuedAt: 1234567890
  ├── expiresAt: 1237246290  (30天后)
  └── enabled: "true"
```

**Signature（签名）：**
```
HMACSHA256(
  base64UrlEncode(header) + "." +
  base64UrlEncode(payload),
  secret
)
```

#### 6.3.3 与自有系统 JWT 的区别

| 特性 | 自有系统 JWT | 第三方系统 JWT |
|------|-------------|---------------|
| **用户标识** | `username` | `client_id` |
| **租户信息** | `tenantCode`（可选） | 无 |
| **权限范围** | 用户角色权限 | `scopes`（OAuth2.0 标准） |
| **签发密钥** | 自有系统专用密钥 | 第三方系统专用密钥 |
| **Subject** | `"Interactive token"` | `"client_credentials"` |
| **Audience** | 用户名 | API 服务地址 |
| **用途** | 用户登录认证 | OAuth2.0 Client Credentials 认证 |

#### 6.3.4 Token 生成示例

```java
// 为第三方系统生成 JWT access_token
public static String generateThirdPartyAccessToken(
    String clientId, 
    List<String> scopes, 
    String secret, 
    long expiredTime) {
    
    var algorithm = Algorithm.HMAC256(secret);
    var builder = JWT.create()
            .withClaim("client_id", clientId)
            .withClaim("scopes", scopes)
            .withIssuedAt(new Date())
            .withExpiresAt(new Date(expiredTime))
            .withJWTId(UUID.randomUUID().toString())
            .withIssuer("Richie Gateway")
            .withSubject("client_credentials")
            .withAudience("api.abc.com");
    
    return builder.sign(algorithm);
}

// 生成 refresh_token（随机字符串）
public static String generateRefreshToken() {
    return UUID.randomUUID().toString().replace("-", "") + 
           SecureRandom.getInstanceStrong().nextLong();
}
```

---

## 7. 安全设计

### 7.1 密钥管理

- **数据库存储**：`clientSecret` 加密存储（使用平台加密工具）
- **Redis 缓存**：存储明文 `clientSecret`（仅用于快速验证）
- **传输安全**：所有接口使用 HTTPS

### 7.2 访问控制

- **IP 白名单**：支持配置 IP 白名单（可选）
- **权限范围**：通过 `scopes` 控制访问权限
- **启用/禁用**：支持动态启用/禁用客户端

### 7.3 Token 安全

#### 7.3.1 JWT Token 安全特性

- **签名验证**：使用 HMAC256 算法对 token 进行签名，确保 token 未被篡改
- **有效期控制**：access_token 默认 1 小时有效期，通过 `exp` 字段控制
- **防重放攻击**：通过 `jti` (JWT ID) 字段唯一标识每个 token，支持黑名单机制
- **密钥隔离**：第三方系统使用独立的签名密钥，与自有系统 JWT 密钥分离
- **Token 黑名单**：支持将 token 加入黑名单（可选），用于主动撤销 token

#### 7.3.2 Refresh Token 安全特性

- **独立存储**：refresh_token 存储在 Redis 中，与 access_token 分离
- **更长有效期**：refresh_token 有效期通常为 30 天，比 access_token 更长
- **一次性使用**：每次使用 refresh_token 获取新 token 后，旧的 refresh_token 失效
- **客户端绑定**：refresh_token 与 client_id 绑定，防止跨客户端使用
- **权限范围保持**：使用 refresh_token 获取的新 access_token 保持原有 scopes
- **主动撤销**：支持将 refresh_token 加入黑名单，立即失效
- **并发刷新保护**：使用 Redis 分布式锁 + Lua 脚本原子操作，防止并发刷新导致的安全问题
  - 问题：如果第三方系统并发调用刷新接口，可能导致多个请求同时验证通过，生成多个新的 refresh_token
  - 解决方案：使用分布式锁确保同一 refresh_token 只能被一个请求处理，使用 Lua 脚本确保验证和删除的原子性

#### 7.3.3 Token 验证流程

**Access Token 验证流程：**
1. **提取 Token**：从请求头 `Authorization: Bearer {access_token}` 中提取
2. **验证签名**：使用第三方系统专用密钥验证 token 签名
3. **验证有效期**：检查 `exp` 字段，确保 token 未过期
4. **验证客户端**：从 token 中提取 `client_id`，验证客户端是否启用
5. **验证权限**：检查 `scopes` 字段，验证客户端是否有权限访问该资源
6. **黑名单检查**：检查 token 是否在黑名单中（可选）

**Refresh Token 验证流程（含并发保护）：**
1. **获取分布式锁**：使用 `refresh-token-lock:{token}` 作为锁键，防止并发刷新
2. **提取 Token**：从请求参数 `refresh_token` 中提取
3. **原子验证和删除**：使用 Lua 脚本原子性地验证 refresh_token 存在性并从 Redis 删除
4. **验证客户端**：验证 refresh_token 绑定的 client_id 是否匹配
5. **验证启用状态**：检查 refresh_token 是否被禁用或撤销
6. **生成新 Token**：验证通过后，生成新的 access_token 和 refresh_token
7. **更新存储**：存储新的 refresh_token
8. **释放锁**：释放分布式锁

**并发刷新保护实现示例：**
```java
public TokenResponse refreshToken(String refreshToken) {
    String lockKey = "refresh-token-lock:" + refreshToken;
    
    // 1. 获取分布式锁（防止并发刷新）
    if (!GlobalCache.pessimisticLockWithRenewal(lockKey, 5, TimeUnit.SECONDS)) {
        throw new BusinessException("刷新令牌正在处理中，请稍后重试");
    }
    
    try {
        // 2. 原子性验证和删除（使用 Lua 脚本）
        String luaScript = 
            "if redis.call('exists', KEYS[1]) == 1 then " +
            "  local data = redis.call('hgetall', KEYS[1]); " +
            "  redis.call('del', KEYS[1]); " +
            "  return data; " +
            "else " +
            "  return nil; " +
            "end";
        
        // 3. 执行原子操作
        Map<String, String> tokenData = executeLuaScript(luaScript, refreshTokenKey);
        
        if (tokenData == null) {
            throw new BusinessException("invalid_grant", "刷新令牌无效或已使用");
        }
        
        // 4. 生成新 token
        return generateNewTokens(tokenData);
    } finally {
        // 5. 释放锁
        GlobalCache.releaseLock(lockKey);
    }
}
```

#### 7.3.4 错误信息泄露防护

**问题描述：**
- 错误响应可能泄露敏感信息（如 client_id 是否存在）
- 攻击者可能通过错误信息枚举有效的 client_id

**解决方案：**
- **统一错误响应格式**：符合 OAuth2.0 标准（RFC 6749）
- **不区分错误类型**：client_id 不存在和 client_secret 错误统一返回 `invalid_client`
- **标准错误码**：
  - `invalid_request`：请求参数缺失或格式错误
  - `invalid_client`：客户端认证失败（不区分 client_id 或 secret）
  - `invalid_grant`：授权码、refresh_token 等无效
  - `unauthorized_client`：客户端无权使用此授权类型
  - `unsupported_grant_type`：不支持的 grant_type
  - `invalid_scope`：请求的 scope 无效
  - `invalid_token`：访问令牌无效或已过期
  - `rate_limit_exceeded`：请求过于频繁

**标准错误响应格式：**
```json
{
  "error": "invalid_client",
  "error_description": "客户端认证失败",
  "error_uri": "https://docs.richie696.cn/oauth2/errors#invalid_client"
}
```

#### 7.3.5 Token 撤销机制

**撤销接口：**
```java
@PostMapping("/api/oauth2/revoke")
public ResultVO<Void> revokeToken(@RequestParam String token) {
    // 1. 验证 token 类型（access_token 或 refresh_token）
    // 2. 将 token 加入黑名单
    // 3. 如果是 refresh_token，从 Redis 删除
    // 4. 记录撤销日志
}
```

**撤销场景：**
- 第三方系统主动撤销已泄露的 token
- 管理员强制撤销某个客户端的 token
- 客户端密钥重置后，自动撤销所有相关 token

#### 7.3.6 密钥轮换机制

**配置示例：**
```yaml
platform:
  gateway:
    interface-auth:
      token-secret: "current-secret"
      token-secret-rotation:
        enabled: true
        rotation-interval-days: 90  # 90天轮换一次
        previous-secrets:           # 保留旧密钥用于验证（支持多版本）
          - "previous-secret-1"
          - "previous-secret-2"
```

**轮换策略：**
- 定期轮换签名密钥（建议 90 天）
- 保留旧密钥用于验证已签发的 token（支持多版本密钥）
- 新签发的 token 使用新密钥
- 旧 token 在过期前仍可使用旧密钥验证

#### 7.3.7 安全最佳实践

- **HTTPS 传输**：所有 token 传输必须使用 HTTPS，防止中间人攻击
- **密钥管理**：签名密钥定期轮换，使用强随机密钥
- **Token 存储**：
  - access_token：客户端内存存储，使用后立即清除
  - refresh_token：客户端安全存储（加密存储或安全密钥库）
- **最小权限原则**：为每个客户端分配最小必要的 `scopes` 权限
- **Token 轮换**：每次使用 refresh_token 后，旧的 refresh_token 立即失效
- **监控告警**：监控异常 token 使用情况（如频繁刷新、异常 IP），及时告警
- **定期刷新**：建议第三方系统在 access_token 到期前主动刷新，避免业务中断
- **错误信息保护**：统一错误响应格式，不泄露敏感信息
- **并发保护**：使用分布式锁和原子操作防止并发刷新问题

---

## 8. 性能优化与限流

### 8.1 限流机制

**问题描述：**
- Token 接口缺少限流保护，可能导致暴力破解攻击或资源耗尽

**限流策略：**
- **Token 接口（按 client_id）**：10 次/分钟
- **Token 接口（按 IP）**：30 次/分钟（未认证请求）
- **Refresh Token 接口**：5 次/分钟（防止频繁刷新）
- **业务接口**：根据客户端配置的 QPS 限制

**实现示例：**
```java
@Component
public class InterfaceAuthFilter extends AbstractBaseFilter {
    
    @Override
    public Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        
        // Token 接口限流
        if (path.equals("/api/oauth2/token")) {
            String clientId = extractClientId(exchange);
            String ip = getClientIp(exchange);
            
            // 按 client_id 限流：每分钟最多 10 次
            String limitKey = "token-limit:client:" + clientId;
            if (!GlobalCache.tryAcquire(limitKey, 10, 60)) {
                return NetworkUtils.returnError(
                    exchange.getResponse(), 
                    HttpStatus.TOO_MANY_REQUESTS,
                    OAuth2ErrorResponse.rateLimitExceeded()
                );
            }
            
            // 按 IP 限流：每分钟最多 30 次（防止未认证的暴力破解）
            String ipLimitKey = "token-limit:ip:" + ip;
            if (!GlobalCache.tryAcquire(ipLimitKey, 30, 60)) {
                return NetworkUtils.returnError(
                    exchange.getResponse(), 
                    HttpStatus.TOO_MANY_REQUESTS,
                    OAuth2ErrorResponse.rateLimitExceeded()
                );
            }
        }
        
        return chain.filter(exchange);
    }
}
```

### 8.2 缓存预热和降级

**缓存预热：**
```java
@PostConstruct
public void warmupCache() {
    // 从数据库加载所有启用的客户端配置到 Redis
    // 确保服务启动后缓存已就绪
}
```

**缓存降级策略：**
- **优先从 Redis 读取**：正常情况下从 Redis 读取客户端配置
- **降级到数据库**：Redis 不可用时，临时从数据库读取（仅限紧急情况）
- **记录告警**：降级时记录告警日志，便于运维人员及时处理

**实现示例：**
```java
public ThirdPartyClientConfig getClientConfig(String clientId) {
    // 优先从 Redis 读取
    ThirdPartyClientConfig config = getFromRedis(clientId);
    if (config != null) {
        return config;
    }
    
    // Redis 不可用时，降级到数据库（记录告警）
    if (isRedisDown()) {
        log.warn("Redis 不可用，降级到数据库读取: clientId={}", clientId);
        return getFromDatabase(clientId);
    }
    
    return null;
}
```

### 8.3 Refresh Token 批量清理

**问题描述：**
- 过期的 refresh_token 依赖 Redis TTL 自动清理
- 如果大量 refresh_token 过期，可能占用 Redis 内存

**解决方案：**
```java
// 定时任务：清理过期的 refresh_token
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点执行
public void cleanupExpiredRefreshTokens() {
    // 扫描所有 refresh-token:* 键
    // 检查过期时间，删除已过期的 token
    // 记录清理数量
}
```

---

## 9. 部署方案

### 8.1 部署架构

```
┌─────────────────────────────────────────────────────────┐
│  管理侧（general-service）                                │
│  - 管理接口（REST API）                                   │
│  - 管理页面（HTML/前端）                                  │
│  - 定时同步任务                                           │
│  - 数据库连接（MySQL）                                    │
│  - Redis 连接（写入）                                     │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  第三方网关（独立部署）                                    │
│  - InterfaceAuthFilter（OAuth2.0 认证）                   │
│  - 路由转发                                               │
│  - Redis 连接（只读）                                     │
│  - 无数据库连接                                           │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  后端服务集群                                             │
│  - 业务服务A                                              │
│  - 业务服务B                                              │
│  - 业务服务C                                              │
└─────────────────────────────────────────────────────────┘
```

### 8.2 配置隔离

- **第三方网关**：独立配置文件，不包含自有系统相关配置
- **过滤器启用**：第三方网关只启用必要的过滤器
- **性能隔离**：第三方系统访问不影响自有系统性能

---

## 10. 监控与运维

### 10.1 关键指标监控

**核心指标：**
- **Token 签发量**：每分钟签发的 access_token 数量
- **Token 刷新量**：每分钟刷新的 refresh_token 数量
- **认证失败率**：认证失败的请求占比（按错误类型分类）
- **缓存命中率**：Redis 缓存命中率
- **同步任务执行情况**：定时同步任务执行状态
- **限流触发次数**：限流机制触发的次数
- **异常 IP 访问统计**：异常 IP 的访问频率和失败次数
- **客户端活跃度统计**：各客户端的 token 使用频率

**指标采集实现：**
```java
@Component
public class OAuth2Metrics {
    
    // Token 签发计数器
    private final Counter tokenIssuedCounter;
    
    // Token 刷新计数器
    private final Counter tokenRefreshedCounter;
    
    // 认证失败计数器（按错误类型）
    private final Counter authFailureCounter;
    
    // 限流触发计数器
    private final Counter rateLimitCounter;
    
    public void recordTokenIssued(String clientId) {
        tokenIssuedCounter.increment("client_id", clientId);
    }
    
    public void recordAuthFailure(String clientId, String errorType) {
        authFailureCounter.increment("client_id", clientId, "error", errorType);
    }
}
```

### 10.2 告警规则

**告警阈值建议：**
- **认证失败率 > 10%**：可能存在攻击或配置错误
- **同一 IP 认证失败 > 50 次/小时**：可能存在暴力破解
- **Refresh Token 刷新失败率 > 5%**：可能存在 token 泄露
- **Token 签发量异常增长 > 200%**：可能存在异常访问
- **Redis 缓存命中率 < 90%**：缓存可能存在问题
- **同步任务失败**：数据同步异常，需要立即处理

### 10.3 健康检查接口

**健康检查接口：**
```java
@GetMapping("/api/oauth2/health")
public ResultVO<Map<String, Object>> health() {
    Map<String, Object> health = new HashMap<>();
    health.put("status", "UP");
    health.put("redis", checkRedisHealth());
    health.put("cache", checkCacheHealth());
    health.put("timestamp", System.currentTimeMillis());
    return ResultVO.success(health);
}
```

**健康检查项：**
- Redis 连接状态
- 缓存可用性
- 同步任务状态
- 服务整体状态

### 10.4 日志记录

**日志分类：**
- **管理操作日志**：记录所有客户端配置的增删改操作（包含操作人、操作时间、操作内容）
- **认证日志**：记录 token 签发和验证过程（包含 client_id、IP、时间、结果）
- **异常日志**：记录认证失败、同步失败等异常情况（包含错误类型、错误详情）
- **审计日志**：记录所有安全相关操作（包含 token 撤销、密钥重置等）

**审计日志实现：**
```java
@Aspect
public class OAuth2AuditAspect {
    
    @Around("@annotation(OAuth2Operation)")
    public Object audit(ProceedingJoinPoint joinPoint) {
        String operation = getOperation(joinPoint);
        String clientId = extractClientId(joinPoint);
        String ip = getClientIp(joinPoint);
        
        try {
            Object result = joinPoint.proceed();
            // 记录成功日志
            auditLogService.logSuccess(operation, clientId, ip);
            return result;
        } catch (Exception e) {
            // 记录失败日志（包含错误类型）
            auditLogService.logFailure(operation, clientId, ip, e);
            throw e;
        }
    }
}
```

---

## 11. 配置管理优化

### 11.1 客户端配置版本化

**问题描述：**
- 客户端配置变更后，无法回滚
- 缺少配置变更历史

**解决方案：**
```sql
-- 添加配置版本表
CREATE TABLE third_party_client_history (
    id BIGINT PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL,
    config_snapshot TEXT,  -- JSON 格式的配置快照
    change_type VARCHAR(32),  -- CREATE/UPDATE/DELETE
    change_reason VARCHAR(512),
    operator_id VARCHAR(64),
    change_time DATETIME,
    INDEX idx_client_id (client_id),
    INDEX idx_change_time (change_time)
);
```

**功能特性：**
- 记录每次配置变更的完整快照
- 支持配置回滚
- 提供配置变更历史查询
- 记录变更原因和操作人

### 11.2 配置模板和批量操作（可选）

**配置模板：**
```java
@Data
public class ClientConfigTemplate {
    private String templateName;
    private String scopes;
    private Integer tokenValidDuration;
    private Integer refreshTokenValidDuration;
    // ...
}
```

**批量创建：**
```java
@PostMapping("/api/gateway/third-party-client/batch")
public ResultVO<List<Long>> batchCreate(@RequestBody List<ThirdPartyClientDTO> clients) {
    // 批量创建客户端
}
```

---

## 12. 实施计划

### 12.1 开发阶段

1. **Phase 1：数据库与模型**
   - 创建 Liquibase changelog（公司表、客户端表）
   - 创建实体类（ThirdPartyCompany、ThirdPartyClient）
   - 创建 DTO、VO 类
   - 创建 Mapper 接口

2. **Phase 2：管理功能**
   - **公司信息管理**
     - 实现 ThirdPartyCompanyService 业务逻辑
     - 实现 ThirdPartyCompanyController REST API
     - 实现公司信息的增删改查功能
   - **客户端管理**
     - 实现 ThirdPartyClientService 业务逻辑（增加公司关联验证）
     - 实现 ThirdPartyClientController REST API
     - 实现客户端信息的增删改查功能
   - **管理页面（前端）**
     - 实现公司信息管理页面
     - 实现客户端管理页面（支持选择关联公司）

3. **Phase 3：网关功能**
   - 重构 InterfaceAuthFilter
   - 实现 OAuth2.0 Token 接口
   - 实现 access_token 验证和续签

4. **Phase 4：同步机制**
   - 实现实时同步逻辑
   - 实现定时同步任务
   - 测试数据一致性

### 12.2 测试阶段

- **单元测试**：Service、Filter 等核心逻辑
- **集成测试**：端到端流程测试
- **性能测试**：网关性能、缓存性能
- **安全测试**：认证安全、数据安全

### 12.3 上线阶段

- **灰度发布**：先上线管理功能，再上线网关功能
- **监控告警**：配置关键指标监控和告警
- **文档交付**：API 文档、使用手册

---

### 12.4 改进优先级

**🔴 高优先级（必须实现）：**
1. ✅ Refresh Token 并发刷新问题（使用分布式锁 + 原子操作）
2. ✅ 错误信息泄露风险（统一错误响应格式）
3. ✅ 限流机制（防止暴力破解和资源耗尽）
4. ✅ OAuth2.0 标准错误响应（符合 RFC 6749）
5. ✅ 关键指标监控和告警

**🟡 中优先级（建议实现）：**
1. Refresh Token 撤销机制
2. 密钥轮换机制
3. 缓存降级策略
4. 错误日志和审计
5. 配置版本管理
6. 健康检查接口

**🟢 低优先级（可选实现）：**
1. Refresh Token 批量清理
2. 配置模板和批量操作
3. Token 使用统计
4. 多环境支持

---

## 13. 附录

### 13.1 参考文档

- [OAuth2.0 RFC 6749](https://tools.ietf.org/html/rfc6749)
- [JWT RFC 7519](https://tools.ietf.org/html/rfc7519)
- [Liquibase 官方文档](https://docs.liquibase.com/)

### 13.2 术语表

| 术语 | 说明 |
|------|------|
| **company_id** | 公司ID，关联到第三方公司信息表 |
| **company_code** | 统一社会信用代码，唯一标识第三方公司 |
| **client_id** | 客户端标识符，唯一标识第三方系统 |
| **client_secret** | 客户端密钥，用于验证客户端身份 |
| **access_token** | 访问令牌，用于访问受保护的资源（JWT 格式，有效期较短，如 1 小时） |
| **refresh_token** | 刷新令牌，用于获取新的 access_token（随机字符串，有效期较长，如 30 天） |
| **scopes** | 权限范围，定义客户端可以访问的资源 |
| **JWT** | JSON Web Token，一种紧凑的、URL 安全的令牌格式 |
| **grant_type** | 授权类型，OAuth2.0 中指定获取 token 的方式（如 client_credentials、refresh_token） |

---

**文档版本：** v1.0  
**编写日期：** 2025-01-XX  
**作者：** richie696
