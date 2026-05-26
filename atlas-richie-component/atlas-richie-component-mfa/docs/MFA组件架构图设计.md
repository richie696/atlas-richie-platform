# MFA组件架构图设计

## 📋 目录

- [颜色图例说明](#颜色图例说明)
- [1. 系统整体架构](#1-系统整体架构)
- [2. 网关验证层架构](#2-网关验证层架构)
- [3. 管理服务层架构](#3-管理服务层架构)
- [4. 数据流架构](#4-数据流架构)
- [5. 安全架构](#5-安全架构)
- [6. 部署架构](#6-部署架构)
- [7. 高可用架构](#7-高可用架构)
- [8. 审计日志架构](#8-审计日志架构)

---

## 颜色图例说明

为了更清晰地展示架构，所有架构图使用统一的颜色标识系统：

### 🎨 颜色分类

| 颜色 | 用途 | 示例 |
|------|------|------|
| 🔵 **蓝色系** | 网关验证层、验证相关组件 | MFA验证过滤器、TOTP引擎、网关节点 |
| 🟠 **橙色系** | 管理服务层、管理相关组件 | MFA管理服务、绑定服务、通用服务节点 |
| 🟢 **绿色系** | 缓存、成功状态、支撑服务 | GlobalCache、Redis、验证成功、KMS服务 |
| 🟡 **黄色系** | 数据库、数据存储 | MySQL、数据库操作、数据持久化 |
| 🔴 **红色系** | 错误、失败、告警 | 验证失败、重放攻击、告警通知 |
| 🟣 **紫色系** | 审计、分析 | 审计日志、日志分析 |
| ⚪ **灰色系** | 对象存储、外部服务 | S3/OSS、消息队列 |

### 📊 节点类型标识

- **深色边框（3px）**：核心组件或关键节点
- **中等边框（2px）**：普通组件或处理节点
- **浅色填充**：辅助组件或可选组件
- **白色文字**：重要组件，需要突出显示

### 🔍 快速识别

- **蓝色节点** = 网关验证相关（部署在gateway-service）
- **橙色节点** = 管理服务相关（部署在general-service）
- **绿色节点** = 缓存/成功状态
- **黄色节点** = 数据库操作
- **红色节点** = 错误/失败场景

---

## 1. 系统整体架构

### 1.1 分层架构图（模块分离）

```mermaid
graph TB
    subgraph Client["客户端层"]
        A1[Web浏览器]
        A2[移动APP]
        A3[桌面客户端]
        A4[第三方应用]
    end
    
    subgraph Gateway["网关层 (richie-gateway-service)"]
        B1[负载均衡器]
        B2[MFA验证过滤器<br/>atlas-richie-component-mfa-validation]
        B3[请求路由]
        B4[熔断器]
        B5[TOTP验证引擎]
        B6[防重放服务]
        B7[MFA状态检查<br/>仅读缓存返回元数据]
        B8[风控引擎]
    end
    
    subgraph General["通用服务层 (richie-general-service)"]
        C1[MFA管理服务<br/>atlas-richie-component-mfa-management]
        C2[绑定管理服务]
        C3[状态管理服务]
        C4[设备管理服务]
        C5[备份码服务]
        C6[缓存同步服务]
        C7[Liquibase迁移<br/>atlas-richie-component-liquibase]
    end
    
    subgraph Support["支撑服务层"]
        E1[密钥管理服务<br/>KMS/HSM]
        E2[审计日志服务<br/>数字签名]
        E3[通知服务]
        E4[归档服务]
    end
    
    subgraph Storage["存储层"]
        F1[GlobalCache<br/>Redis集群<br/>只读/可写]
        F2[MySQL数据库<br/>主从复制<br/>仅管理服务访问]
        F3[对象存储<br/>S3/OSS]
    end
    
    A1 --> B1
    A2 --> B1
    A3 --> B1
    A4 --> B1
    
    B1 --> B2
    B2 --> B3
    B2 --> B4
    B2 --> B5
    B5 --> B6
    B5 --> B7
    B5 --> B8
    
    A1 -.管理请求.-> C1
    A2 -.管理请求.-> C1
    A3 -.管理请求.-> C1
    A4 -.管理请求.-> C1
    
    C1 --> C2
    C1 --> C3
    C1 --> C4
    C1 --> C5
    C3 --> C6
    C1 --> C7
    
    B5 -->|只读| F1
    B6 -->|读写| F1
    B7 -->|只读| F1
    
    C2 -->|读写| F2
    C3 -->|读写| F2
    C4 -->|读写| F2
    C5 -->|读写| F2
    C7 -->|DDL| F2
    C6 -->|同步| F1
    
    C2 --> E1
    C3 --> E2
    C5 --> E3
    E2 --> E4
    
    E4 --> F3
    
    %% 客户端层 - 浅蓝色
    style A1 fill:#E3F2FD,stroke:#2196F3,stroke-width:2px,color:#000
    style A2 fill:#E3F2FD,stroke:#2196F3,stroke-width:2px,color:#000
    style A3 fill:#E3F2FD,stroke:#2196F3,stroke-width:2px,color:#000
    style A4 fill:#E3F2FD,stroke:#2196F3,stroke-width:2px,color:#000
    
    %% 网关层 - 蓝色系
    style B1 fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style B2 fill:#2196F3,stroke:#0D47A1,stroke-width:3px,color:#FFF
    style B3 fill:#90CAF9,stroke:#1976D2,stroke-width:2px,color:#000
    style B4 fill:#64B5F6,stroke:#1565C0,stroke-width:2px,color:#FFF
    style B5 fill:#42A5F5,stroke:#1565C0,stroke-width:2px,color:#FFF
    style B6 fill:#1E88E5,stroke:#0D47A1,stroke-width:2px,color:#FFF
    style B7 fill:#1976D2,stroke:#0D47A1,stroke-width:2px,color:#FFF
    style B8 fill:#1565C0,stroke:#0D47A1,stroke-width:2px,color:#FFF
    
    %% 通用服务层 - 橙色系
    style C1 fill:#FF9800,stroke:#E65100,stroke-width:3px,color:#FFF
    style C2 fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    style C3 fill:#FFA726,stroke:#E65100,stroke-width:2px,color:#000
    style C4 fill:#FF9800,stroke:#F57C00,stroke-width:2px,color:#FFF
    style C5 fill:#FB8C00,stroke:#E65100,stroke-width:2px,color:#FFF
    style C6 fill:#F57C00,stroke:#E65100,stroke-width:2px,color:#FFF
    style C7 fill:#FF6F00,stroke:#E65100,stroke-width:2px,color:#FFF
    
    %% 支撑服务层 - 绿色系
    style E1 fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    style E2 fill:#66BB6A,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style E3 fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style E4 fill:#43A047,stroke:#1B5E20,stroke-width:2px,color:#FFF
    
    %% 存储层 - 不同颜色区分
    style F1 fill:#4CAF50,stroke:#2E7D32,stroke-width:3px,color:#FFF
    style F2 fill:#FFC107,stroke:#F57F17,stroke-width:3px,color:#000
    style F3 fill:#9E9E9E,stroke:#616161,stroke-width:2px,color:#FFF
```

### 1.2 组件交互图

```mermaid
graph LR
    subgraph GatewayFlow["网关验证流程"]
        A[请求] --> B{业务已返回<br/>accessToken?}
        B -->|是| C[直接放行签发Token]
        B -->|否| D[checkMfaStatus 仅读缓存<br/>返回 MFA_REQUIRED 或 TOTP验证]
        D --> E{验证成功?}
        E -->|是| F[防重放检查]
        E -->|否| G[失败处理]
        F --> H{已使用?}
        H -->|否| I[标记已使用]
        H -->|是| J[重放攻击]
        I --> K[风控检测]
        K --> L[返回结果]
    end
    
    subgraph ManagementFlow["管理服务流程"]
        M[绑定请求] --> N[生成密钥]
        N --> O[KMS加密]
        O --> P[生成备份码]
        P --> Q[哈希备份码]
        Q --> R[保存数据库]
        R --> S[同步缓存]
        S --> T[返回结果]
    end
    
    %% 网关验证流程 - 蓝色系
    style A fill:#E3F2FD,stroke:#2196F3,stroke-width:2px,color:#000
    style B fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style C fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style D fill:#2196F3,stroke:#0D47A1,stroke-width:2px,color:#FFF
    style E fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style F fill:#42A5F5,stroke:#1565C0,stroke-width:2px,color:#FFF
    style G fill:#F44336,stroke:#C62828,stroke-width:2px,color:#FFF
    style H fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style I fill:#66BB6A,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style J fill:#F44336,stroke:#C62828,stroke-width:2px,color:#FFF
    style K fill:#FF9800,stroke:#E65100,stroke-width:2px,color:#FFF
    style L fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
    
    %% 管理服务流程 - 橙色系
    style M fill:#FFE0B2,stroke:#F57C00,stroke-width:2px,color:#000
    style N fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    style O fill:#FF9800,stroke:#E65100,stroke-width:2px,color:#FFF
    style P fill:#FFA726,stroke:#E65100,stroke-width:2px,color:#000
    style Q fill:#FF9800,stroke:#E65100,stroke-width:2px,color:#FFF
    style R fill:#FFC107,stroke:#F57F17,stroke-width:2px,color:#000
    style S fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style T fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
```

---

## 2. 网关验证层架构

### 2.1 验证过滤器详细架构（网关层）

**部署位置**：`richie-gateway-service`  
**模块**：`richie-component-mfa-validation`

**说明**：可信设备与“是否需要 MFA”由业务登录层 `MfaBindManager.checkLoginMfa` 判断；网关仅在业务未返回 accessToken 时调用 checkMfaStatus（只读缓存，不做设备信任校验），再进入 TOTP 验证。

```mermaid
graph TB
    subgraph "MFA验证过滤器 (网关层)"
        A1[请求拦截] --> A2[参数提取]
        A2 --> A3{业务响应含<br/>accessToken?}
        A3 -->|是| A4[不再检查MFA，放行]
        A3 -->|否| A5[checkMfaStatus 只读缓存<br/>返回元数据或进入验证]
        
        A5 --> B1{缓存可用?}
        B1 -->|是| B2[从缓存读取]
        B1 -->|否| B3[熔断器检查]
        
        B3 --> C1{熔断器状态}
        C1 -->|关闭| C2[快速失败 不降级DB]
        C1 -->|打开| C3[快速失败 503]
        
        B2 --> D1[解密密钥 KMS]
        C2 --> D1
        C3 --> D1
        
        D1 --> D2[TOTP验证]
        D2 --> D3{验证成功?}
        D3 -->|是| D4[防重放检查<br/>只读Redis]
        D3 -->|否| D5[失败计数<br/>只写Redis]
        
        D4 --> E1{已使用?}
        E1 -->|否| E2[标记已使用<br/>唯一可写操作<br/>TTL控制]
        E1 -->|是| E3[重放攻击处理]
        
        E2 --> F1[风控检测<br/>只读Redis]
        D5 --> F2{达到上限?}
        F2 -->|是| F3[通知管理服务锁定<br/>异步]
        F2 -->|否| F4[返回失败]
        
        F1 --> G1[签发访问Token]
        F3 --> G2[返回锁定错误]
        F4 --> G2
        E3 --> G2
        G1 --> H1[返回成功]
        G2 --> H2[返回失败]
    end
    
    style C2 fill:#ffebee
    style C3 fill:#ffebee
    style E2 fill:#e8f5e9
```

**说明**：网关层缓存不可时时快速失败，不降级到数据库，保持轻量化。

### 2.2 缓存降级架构（网关层 - 快速失败）

**重要说明**：网关层不降级到数据库，缓存不可用时快速失败

```mermaid
graph TB
    A[验证请求] --> B[尝试读取缓存]
    B --> C{缓存命中?}
    C -->|是| D[正常验证]
    C -->|否| E{缓存异常?}
    E -->|是| F[触发熔断器]
    E -->|否| G[快速失败 不降级DB]
    
    F --> H{熔断器状态}
    H -->|关闭| I[记录失败]
    H -->|打开| J[快速失败 503]
    
    I --> K{失败率超阈值?}
    K -->|是| L[打开熔断器]
    K -->|否| G
    
    L --> J
    D --> P[返回结果]
    G --> P
    J --> P
    
    %% 起始节点 - 蓝色
    style A fill:#E3F2FD,stroke:#2196F3,stroke-width:2px,color:#000
    style B fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    
    %% 判断节点 - 浅蓝色
    style C fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style E fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style H fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style K fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    
    %% 成功路径 - 绿色
    style D fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style P fill:#66BB6A,stroke:#2E7D32,stroke-width:3px,color:#FFF
    
    %% 失败路径 - 红色
    style F fill:#FFCDD2,stroke:#F44336,stroke-width:2px,color:#000
    style G fill:#F44336,stroke:#C62828,stroke-width:3px,color:#FFF
    style I fill:#EF9A9A,stroke:#E53935,stroke-width:2px,color:#000
    style J fill:#F44336,stroke:#C62828,stroke-width:3px,color:#FFF
    style L fill:#E57373,stroke:#D32F2F,stroke-width:2px,color:#FFF
```

**说明**：网关层缓存未命中或异常时快速失败，不降级到数据库，保持轻量化。

---

## 3. 管理服务层架构

### 3.1 服务模块架构（通用服务层）

**部署位置**：`richie-general-service`  
**模块**：`richie-component-mfa-management`

```mermaid
graph TB
    subgraph Management["MFA管理服务 (通用服务层)"]
        A[API接口层] --> B[服务编排层]
        
        B --> C1[绑定管理服务]
        B --> C2[状态管理服务]
        B --> C3[设备管理服务]
        B --> C4[备份码服务]
        B --> C5[恢复服务]
        
        C1 --> D1[密钥生成]
        C1 --> D2[密钥加密<br/>KMS]
        C1 --> D3[二维码生成]
        C1 --> D4[备份码生成<br/>BCrypt哈希]
        
        C2 --> E1[状态查询<br/>数据库/缓存]
        C2 --> E2[状态更新<br/>数据库+缓存]
        C2 --> E3[账户锁定<br/>数据库+缓存]
        
        C3 --> F1[设备注册<br/>数据库]
        C3 --> F2[设备信任<br/>数据库+缓存]
        C3 --> F3[设备列表<br/>数据库/缓存]
        
        C4 --> G1[备份码生成<br/>BCrypt哈希]
        C4 --> G2[备份码验证<br/>数据库]
        C4 --> G3[备份码删除<br/>数据库]
        
        C5 --> H1[备份码恢复<br/>数据库]
        C5 --> H2[恢复密钥恢复<br/>数据库]
        C5 --> H3[管理员重置<br/>数据库]
        
        C1 --> J[缓存同步服务]
        C2 --> J
        C3 --> J
        C4 --> J
        
        J --> K[分布式锁]
        J --> L[GlobalCache<br/>同步写入]
        J --> M[消息队列<br/>可选]
        
        C1 --> N[发布审计事件<br/>ApplicationEventPublisher]
        C2 --> N
        C3 --> N
        C4 --> N
        C5 --> N
        
        Note over N: MFA组件只发布事件<br/>业务系统监听并处理
        
        R[Liquibase迁移] --> S[DDL管理]
        S --> P
    end
    
    %% API层 - 橙色
    style A fill:#FF9800,stroke:#E65100,stroke-width:3px,color:#FFF
    style B fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    
    %% 管理服务 - 橙色系
    style C1 fill:#FFA726,stroke:#E65100,stroke-width:2px,color:#000
    style C2 fill:#FF9800,stroke:#E65100,stroke-width:2px,color:#FFF
    style C3 fill:#FB8C00,stroke:#E65100,stroke-width:2px,color:#FFF
    style C4 fill:#F57C00,stroke:#E65100,stroke-width:2px,color:#FFF
    style C5 fill:#EF6C00,stroke:#E65100,stroke-width:2px,color:#FFF
    
    %% 密钥相关 - 蓝色系
    style D1 fill:#E3F2FD,stroke:#2196F3,stroke-width:2px,color:#000
    style D2 fill:#2196F3,stroke:#0D47A1,stroke-width:3px,color:#FFF
    style D3 fill:#42A5F5,stroke:#1565C0,stroke-width:2px,color:#FFF
    style D4 fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    
    %% 状态管理 - 黄色/绿色
    style E1 fill:#FFE082,stroke:#F57F17,stroke-width:2px,color:#000
    style E2 fill:#FFC107,stroke:#F57F17,stroke-width:2px,color:#000
    style E3 fill:#F44336,stroke:#C62828,stroke-width:2px,color:#FFF
    
    %% 设备管理 - 黄色/绿色
    style F1 fill:#FFC107,stroke:#F57F17,stroke-width:2px,color:#000
    style F2 fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    style F3 fill:#FFE082,stroke:#F57F17,stroke-width:2px,color:#000
    
    %% 备份码 - 绿色系
    style G1 fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    style G2 fill:#66BB6A,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style G3 fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
    
    %% 恢复服务 - 黄色
    style H1 fill:#FFC107,stroke:#F57F17,stroke-width:2px,color:#000
    style H2 fill:#FFD54F,stroke:#F57F17,stroke-width:2px,color:#000
    style H3 fill:#FFE082,stroke:#F57F17,stroke-width:2px,color:#000
    
    %% 缓存同步 - 绿色系
    style J fill:#4CAF50,stroke:#2E7D32,stroke-width:3px,color:#FFF
    style K fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    style L fill:#66BB6A,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style M fill:#A5D6A7,stroke:#4CAF50,stroke-width:2px,color:#000
    
    %% 审计服务 - 紫色系
    style N fill:#BA68C8,stroke:#6A1B9A,stroke-width:2px,color:#FFF
    style O fill:#AB47BC,stroke:#4A148C,stroke-width:2px,color:#FFF
    style Q fill:#9C27B0,stroke:#4A148C,stroke-width:2px,color:#FFF
    
    %% 数据库 - 黄色
    style P fill:#FFC107,stroke:#F57F17,stroke-width:3px,color:#000
    
    %% Liquibase - 蓝色
    style R fill:#2196F3,stroke:#0D47A1,stroke-width:2px,color:#FFF
    style S fill:#42A5F5,stroke:#1565C0,stroke-width:2px,color:#FFF
```

### 3.2 缓存同步架构（管理服务层）

**执行位置**：`richie-general-service`（管理服务）  
**触发时机**：数据库变更后立即同步

```mermaid
graph TB
    A[数据库变更<br/>管理服务] --> B[事务提交]
    B --> C["获取分布式锁<br/>mfa:sync:lock:{userId}"]
    C --> D{锁获取成功?}
    D -->|是| E[构建缓存对象]
    D -->|否| F[重试机制<br/>指数退避]
    
    E --> G[写入GlobalCache<br/>同步到Redis]
    G --> H{写入成功?}
    H -->|是| I[发布更新事件<br/>可选，集群同步]
    H -->|否| J[记录失败]
    
    I --> K[消息队列<br/>可选]
    K --> L[其他节点接收]
    L --> M[刷新本地缓存]
    
    J --> N[异步重试<br/>定时任务]
    N --> O[重试队列]
    O --> P{重试成功?}
    P -->|是| Q[完成]
    P -->|否| R[记录失败日志<br/>审计日志]
    
    F --> S[等待重试]
    S --> C
    
    %% 起始节点 - 黄色（数据库）
    style A fill:#FFC107,stroke:#F57F17,stroke-width:3px,color:#000
    style B fill:#FFE082,stroke:#F57F17,stroke-width:2px,color:#000
    
    %% 锁相关 - 绿色
    style C fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    style D fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    
    %% 处理节点 - 橙色
    style E fill:#FFE0B2,stroke:#F57C00,stroke-width:2px,color:#000
    style F fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    
    %% 缓存相关 - 绿色
    style G fill:#4CAF50,stroke:#2E7D32,stroke-width:3px,color:#FFF
    style H fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style M fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    
    %% 成功路径 - 绿色
    style I fill:#66BB6A,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style Q fill:#4CAF50,stroke:#2E7D32,stroke-width:3px,color:#FFF
    
    %% 消息队列 - 浅绿色
    style K fill:#A5D6A7,stroke:#4CAF50,stroke-width:2px,color:#000
    style L fill:#A5D6A7,stroke:#4CAF50,stroke-width:2px,color:#000
    
    %% 失败/重试 - 橙色
    style J fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    style N fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    style O fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    style P fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    
    %% 告警 - 红色
    style R fill:#F44336,stroke:#C62828,stroke-width:2px,color:#FFF
```

**说明**：数据库变更后立即同步到 Redis，确保网关验证层能读取到最新数据；使用分布式锁（如 `mfa:sync:lock:{userId}`）保证原子性，避免并发冲突。

---

## 4. 数据流架构

### 4.1 验证数据流（网关层）

**执行位置**：`richie-gateway-service`  
**特点**：只读Redis，不操作数据库

```mermaid
sequenceDiagram
    participant C as 客户端
    participant GW as 网关服务<br/>gateway-service
    participant VS as MFA验证过滤器<br/>validation模块
    participant Cache as GlobalCache<br/>Redis只读
    participant KMS as 密钥管理服务
    participant MS as 管理服务<br/>management模块
    participant DB as 数据库

    Note over GW,VS: 网关验证层：只读Redis，零数据库依赖
    
    C->>GW: 验证码
    GW->>VS: 验证请求
    VS->>Cache: 读取用户信息(只读)
    Cache-->>VS: 加密密钥
    VS->>KMS: 解密密钥
    KMS-->>VS: 明文密钥
    VS->>VS: TOTP验证
    VS->>Cache: 防重放检查(只读)
    Cache-->>VS: 检查结果
    VS->>Cache: 标记已使用(唯一可写)
    Note over VS,Cache: 网关层唯一可写操作：防重放标记
    VS->>MS: 异步通知验证成功
    MS->>DB: 记录日志(异步)
    VS-->>GW: 验证结果
    GW-->>C: 返回结果
    
    Note over VS,DB: 网关层不操作数据库<br/>验证日志由管理服务异步记录
```

### 4.2 绑定数据流（管理服务层）

**执行位置**：`richie-general-service`  
**特点**：操作数据库，使用Liquibase管理DDL，同步到Redis

```mermaid
sequenceDiagram
    participant C as 客户端
    participant GS as 通用服务<br/>general-service
    participant MS as MFA管理服务<br/>management模块
    participant LB as Liquibase<br/>DDL管理
    participant KMS as 密钥管理服务
    participant DB as MySQL数据库
    participant Cache as GlobalCache<br/>Redis
    participant MQ as 消息队列(可选)

    Note over GS,MS: 管理服务层：操作数据库，同步到Redis
    
    C->>GS: 绑定请求
    GS->>MS: 处理绑定
    
    Note over LB,DB: 首次启动时，Liquibase自动创建表结构
    LB->>DB: 执行DDL迁移
    
    MS->>MS: 生成密钥
    MS->>KMS: 加密密钥
    KMS-->>MS: 加密后密钥
    MS->>MS: 生成备份码
    MS->>MS: 哈希备份码(BCrypt)
    MS->>DB: 保存(事务)
    DB-->>MS: 保存成功
    MS->>Cache: 同步缓存(分布式锁)
    Note over MS,Cache: 数据库变更后立即同步到Redis<br/>供网关验证层使用
    Cache-->>MS: 同步成功
    MS->>MQ: 发布事件(可选)
    MQ->>其他节点: 推送更新
    MS-->>GS: 返回结果
    GS-->>C: 返回二维码和备份码
```

---

## 5. 安全架构

### 5.1 密钥管理架构

```mermaid
graph TB
    subgraph Generate["密钥生成与加密"]
        A[生成160位随机密钥] --> B[Base32编码]
        B --> C[获取DEK]
        C --> D[KMS/HSM]
        D --> E[AES-256-GCM加密]
        E --> F[存储加密密钥]
    end
    
    subgraph Decrypt["密钥使用与解密"]
        G[读取加密密钥] --> H[获取DEK]
        H --> I[KMS/HSM]
        I --> J[AES-256-GCM解密]
        J --> K[使用明文密钥]
        K --> L[内存中立即清除]
    end
    
    subgraph Rotate["密钥轮换"]
        M[定时检查] --> N{需要轮换?}
        N -->|是| O[生成新密钥]
        N -->|否| P[结束]
        O --> Q[加密新密钥]
        Q --> R[保存新旧密钥]
        R --> S[宽限期7天]
        S --> T[删除旧密钥]
    end
    
    F --> G
    
    %% 密钥生成流程 - 蓝色系
    style A fill:#E3F2FD,stroke:#2196F3,stroke-width:2px,color:#000
    style B fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style C fill:#90CAF9,stroke:#1976D2,stroke-width:2px,color:#000
    style D fill:#2196F3,stroke:#0D47A1,stroke-width:3px,color:#FFF
    style E fill:#42A5F5,stroke:#1565C0,stroke-width:2px,color:#FFF
    style F fill:#FFC107,stroke:#F57F17,stroke-width:3px,color:#000
    
    %% 密钥解密流程 - 绿色系
    style G fill:#FFC107,stroke:#F57F17,stroke-width:2px,color:#000
    style H fill:#C8E6C9,stroke:#4CAF50,stroke-width:2px,color:#000
    style I fill:#4CAF50,stroke:#2E7D32,stroke-width:3px,color:#FFF
    style J fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    style K fill:#66BB6A,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style L fill:#A5D6A7,stroke:#4CAF50,stroke-width:2px,color:#000
    
    %% 密钥轮换流程 - 橙色系
    style M fill:#FFE0B2,stroke:#F57C00,stroke-width:2px,color:#000
    style N fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    style O fill:#FF9800,stroke:#E65100,stroke-width:2px,color:#FFF
    style P fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style Q fill:#FFA726,stroke:#E65100,stroke-width:2px,color:#000
    style R fill:#FFC107,stroke:#F57F17,stroke-width:2px,color:#000
    style S fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    style T fill:#FF9800,stroke:#E65100,stroke-width:2px,color:#FFF
```

### 5.2 防重放攻击架构

```mermaid
graph TB
    A[验证码输入] --> B[生成时间步长]
    B --> C[计算3个时间窗口]
    C --> D[遍历窗口]
    
    D --> E{验证码匹配?}
    E -->|是| F[检查重放缓存]
    E -->|否| G[下一个窗口]
    
    F --> H{已使用?}
    H -->|是| I[重放攻击]
    H -->|否| J[标记已使用]
    
    J --> K[设置TTL]
    K --> L[TTL=时间窗口*3]
    L --> M[验证成功]
    
    I --> N[记录安全事件]
    N --> O[风控上报]
    O --> P[验证失败]
    
    G --> Q{还有窗口?}
    Q -->|是| D
    Q -->|否| R[验证失败]
    
    %% 起始节点 - 蓝色
    style A fill:#E3F2FD,stroke:#2196F3,stroke-width:2px,color:#000
    style B fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style C fill:#90CAF9,stroke:#1976D2,stroke-width:2px,color:#000
    style D fill:#64B5F6,stroke:#1565C0,stroke-width:2px,color:#FFF
    
    %% 判断节点 - 浅蓝色
    style E fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style H fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style Q fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    
    %% 检查节点 - 绿色
    style F fill:#C8E6C9,stroke:#4CAF50,stroke-width:2px,color:#000
    
    %% 成功路径 - 绿色
    style J fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    style K fill:#66BB6A,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style L fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style M fill:#4CAF50,stroke:#2E7D32,stroke-width:3px,color:#FFF
    
    %% 失败路径 - 红色
    style I fill:#F44336,stroke:#C62828,stroke-width:3px,color:#FFF
    style N fill:#EF9A9A,stroke:#E53935,stroke-width:2px,color:#000
    style O fill:#E57373,stroke:#D32F2F,stroke-width:2px,color:#FFF
    style P fill:#F44336,stroke:#C62828,stroke-width:2px,color:#FFF
    style R fill:#F44336,stroke:#C62828,stroke-width:2px,color:#FFF
    
    %% 循环节点 - 橙色
    style G fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
```

### 5.3 审计日志架构（Spring ApplicationEvent 模式）

**说明**：MFA 组件通过 Spring `ApplicationEventPublisher` 发布审计事件，业务系统通过 `@EventListener` 监听并自行处理（持久化、签名、归档等）。

```mermaid
graph TB
    subgraph MFA["MFA组件层"]
        A[MFA操作执行] --> B[发布审计事件<br/>ApplicationEventPublisher]
    end
    
    subgraph Business["业务系统层"]
        B --> C[业务监听器<br/>@EventListener]
        C --> D[转换为实体对象]
        D --> E{是否启用签名?}
        E -->|是| F[RSA私钥签名]
        E -->|否| G[直接持久化]
        F --> G
        G --> H[保存数据库<br/>业务系统自行定义]
        
        H --> I[定时归档任务]
        I --> J[匿名化处理]
        J --> K[上传对象存储]
    end
    
    subgraph Verify["日志验证流程"]
        L[读取日志] --> M[提取签名]
        M --> N[计算哈希值]
        N --> O[RSA公钥验证]
        O --> P{验证通过?}
        P -->|是| Q[日志完整]
        P -->|否| R[日志被篡改]
    end
    
    %% 日志生成流程 - 蓝色系
    style A fill:#E3F2FD,stroke:#2196F3,stroke-width:2px,color:#000
    style B fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style C fill:#90CAF9,stroke:#1976D2,stroke-width:2px,color:#000
    style D fill:#64B5F6,stroke:#1565C0,stroke-width:2px,color:#FFF
    style E fill:#42A5F5,stroke:#1565C0,stroke-width:2px,color:#FFF
    style F fill:#2196F3,stroke:#0D47A1,stroke-width:3px,color:#FFF
    style G fill:#1E88E5,stroke:#0D47A1,stroke-width:2px,color:#FFF
    style H fill:#FFC107,stroke:#F57F17,stroke-width:3px,color:#000
    
    %% 归档流程 - 绿色系
    style I fill:#C8E6C9,stroke:#4CAF50,stroke-width:2px,color:#000
    style J fill:#A5D6A7,stroke:#4CAF50,stroke-width:2px,color:#000
    style K fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    
    %% 验证流程 - 橙色系
    style L fill:#FFE0B2,stroke:#F57C00,stroke-width:2px,color:#000
    style M fill:#FFC107,stroke:#F57F17,stroke-width:2px,color:#000
    style N fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    style O fill:#FFA726,stroke:#E65100,stroke-width:2px,color:#000
    style P fill:#FF9800,stroke:#E65100,stroke-width:2px,color:#FFF
    style Q fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    
    %% 验证结果 - 绿色/红色
    style R fill:#4CAF50,stroke:#2E7D32,stroke-width:3px,color:#FFF
    style S fill:#F44336,stroke:#C62828,stroke-width:3px,color:#FFF
```

---

## 6. 部署架构

### 6.1 生产环境部署架构

```mermaid
graph TB
    subgraph External["外部访问层"]
        A[公网IP] --> B[CDN/负载均衡]
    end
    
    subgraph GatewayLayer["网关层"]
        B --> C1[网关节点1]
        B --> C2[网关节点2]
        B --> C3[网关节点3]
    end
    
    subgraph ServiceLayer["服务层"]
        C1 --> D1[管理服务节点1]
        C2 --> D2[管理服务节点2]
        C3 --> D3[管理服务节点3]
    end
    
    subgraph CacheLayer["缓存层"]
        D1 --> E1[Redis主节点]
        D2 --> E2[Redis从节点1]
        D3 --> E3[Redis从节点2]
        E1 --> E2
        E1 --> E3
    end
    
    subgraph DatabaseLayer["数据库层"]
        D1 --> F1[MySQL主库]
        D2 --> F2[MySQL从库1]
        D3 --> F3[MySQL从库2]
        F1 --> F2
        F1 --> F3
    end
    
    subgraph SupportServices["支撑服务"]
        D1 --> G1[KMS服务]
        D2 --> G2[消息队列]
        D3 --> G3[对象存储]
    end
    
    Note1["⚠️ 监控说明：<br/>监控指标应由应用层统一管理<br/>（Prometheus、Grafana等）<br/>MFA组件不包含监控功能"]
    
    %% 外部访问层 - 浅灰色
    style A fill:#F5F5F5,stroke:#9E9E9E,stroke-width:2px,color:#000
    style B fill:#E0E0E0,stroke:#757575,stroke-width:2px,color:#000
    
    %% 网关层 - 蓝色系
    style C1 fill:#E3F2FD,stroke:#2196F3,stroke-width:2px,color:#000
    style C2 fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style C3 fill:#90CAF9,stroke:#1976D2,stroke-width:2px,color:#000
    
    %% 服务层 - 橙色系
    style D1 fill:#FFE0B2,stroke:#F57C00,stroke-width:2px,color:#000
    style D2 fill:#FFCCBC,stroke:#E64A19,stroke-width:2px,color:#000
    style D3 fill:#FFAB91,stroke:#D84315,stroke-width:2px,color:#000
    
    %% 缓存层 - 绿色系
    style E1 fill:#4CAF50,stroke:#2E7D32,stroke-width:3px,color:#FFF
    style E2 fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    style E3 fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    
    %% 数据库层 - 黄色系
    style F1 fill:#FFC107,stroke:#F57F17,stroke-width:3px,color:#000
    style F2 fill:#FFE082,stroke:#F57F17,stroke-width:2px,color:#000
    style F3 fill:#FFE082,stroke:#F57F17,stroke-width:2px,color:#000
    
    %% 支撑服务 - 绿色系
    style G1 fill:#66BB6A,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style G2 fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    style G3 fill:#9E9E9E,stroke:#616161,stroke-width:2px,color:#FFF
    
    %% 说明 - 黄色
    style Note1 fill:#FFF3E0,stroke:#F57C00,stroke-width:2px,color:#000
```

### 6.2 容器化部署架构

```mermaid
graph TB
    subgraph K8s["Kubernetes集群"]
        A[Ingress] --> B[Gateway Deployment]
        B --> C[MFA Service]
        C --> D[General Service]
        
        B --> E[Redis StatefulSet]
        D --> F[MySQL StatefulSet]
        
        C --> G[KMS Service]
        D --> H[Message Queue]
        
        B --> I[ConfigMap]
        D --> I
        B --> J[Secret]
        D --> J
        
        B --> K[Service Monitor]
        D --> K
        K --> L[应用层监控<br/>由应用统一管理]
    end
    
    %% Ingress - 浅灰色
    style A fill:#F5F5F5,stroke:#9E9E9E,stroke-width:2px,color:#000
    
    %% Gateway - 蓝色系
    style B fill:#2196F3,stroke:#0D47A1,stroke-width:3px,color:#FFF
    style C fill:#42A5F5,stroke:#1565C0,stroke-width:2px,color:#FFF
    
    %% General Service - 橙色系
    style D fill:#FF9800,stroke:#E65100,stroke-width:3px,color:#FFF
    
    %% 存储 - 绿色/黄色
    style E fill:#4CAF50,stroke:#2E7D32,stroke-width:3px,color:#FFF
    style F fill:#FFC107,stroke:#F57F17,stroke-width:3px,color:#000
    
    %% 支撑服务 - 绿色/灰色
    style G fill:#66BB6A,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style H fill:#9E9E9E,stroke:#616161,stroke-width:2px,color:#FFF
    
    %% 配置 - 浅蓝色
    style I fill:#E1F5FE,stroke:#0277BD,stroke-width:2px,color:#000
    style J fill:#B3E5FC,stroke:#01579B,stroke-width:2px,color:#000
    
    %% 应用层监控 - 灰色系（表示由应用层管理）
    style K fill:#E0E0E0,stroke:#757575,stroke-width:2px,color:#000
    style L fill:#BDBDBD,stroke:#616161,stroke-width:2px,color:#000
```

---

## 7. 高可用架构

### 7.1 高可用设计

```mermaid
graph TB
    subgraph GatewayHA["网关高可用"]
        A1[网关节点1] --> B[健康检查]
        A2[网关节点2] --> B
        A3[网关节点3] --> B
        B --> C{健康状态}
        C -->|健康| D[负载均衡]
        C -->|不健康| E[摘除节点]
    end
    
    subgraph CacheHA["缓存高可用"]
        F1[Redis主节点] --> G[Sentinel]
        F2[Redis从节点1] --> G
        F3[Redis从节点2] --> G
        G --> H{主节点故障?}
        H -->|是| I[自动切换]
        H -->|否| J[正常服务]
    end
    
    subgraph DatabaseHA["数据库高可用"]
        K1[MySQL主库] --> L[主从复制]
        K2[MySQL从库1] --> L
        K3[MySQL从库2] --> L
        L --> M{主库故障?}
        M -->|是| N[自动切换]
        M -->|否| O[正常服务]
    end
    
    subgraph ServiceHA["服务高可用"]
        P1[服务实例1] --> Q[服务发现]
        P2[服务实例2] --> Q
        P3[服务实例3] --> Q
        Q --> R[健康检查]
        R --> S{实例健康?}
        S -->|是| T[正常路由]
        S -->|否| U[摘除实例]
    end
    
    %% 网关节点 - 蓝色系
    style A1 fill:#E3F2FD,stroke:#2196F3,stroke-width:2px,color:#000
    style A2 fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style A3 fill:#90CAF9,stroke:#1976D2,stroke-width:2px,color:#000
    style B fill:#64B5F6,stroke:#1565C0,stroke-width:2px,color:#FFF
    style C fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style D fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style E fill:#F44336,stroke:#C62828,stroke-width:2px,color:#FFF
    
    %% Redis节点 - 绿色系
    style F1 fill:#4CAF50,stroke:#2E7D32,stroke-width:3px,color:#FFF
    style F2 fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    style F3 fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    style G fill:#66BB6A,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style H fill:#C8E6C9,stroke:#4CAF50,stroke-width:2px,color:#000
    style I fill:#F44336,stroke:#C62828,stroke-width:2px,color:#FFF
    style J fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
    
    %% MySQL节点 - 黄色系
    style K1 fill:#FFC107,stroke:#F57F17,stroke-width:3px,color:#000
    style K2 fill:#FFE082,stroke:#F57F17,stroke-width:2px,color:#000
    style K3 fill:#FFE082,stroke:#F57F17,stroke-width:2px,color:#000
    style L fill:#FFD54F,stroke:#F57F17,stroke-width:2px,color:#000
    style M fill:#FFE0B2,stroke:#F57C00,stroke-width:2px,color:#000
    style N fill:#F44336,stroke:#C62828,stroke-width:2px,color:#FFF
    style O fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
    
    %% 服务实例 - 橙色系
    style P1 fill:#FFE0B2,stroke:#F57C00,stroke-width:2px,color:#000
    style P2 fill:#FFCCBC,stroke:#E64A19,stroke-width:2px,color:#000
    style P3 fill:#FFAB91,stroke:#D84315,stroke-width:2px,color:#000
    style Q fill:#FF9800,stroke:#E65100,stroke-width:2px,color:#FFF
    style R fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    style S fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    style T fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style U fill:#F44336,stroke:#C62828,stroke-width:2px,color:#FFF
```

### 7.2 容错机制（管理服务层）

**说明**：管理服务层可降级到数据库，网关层不降级

```mermaid
graph TB
    A[请求] --> B{缓存可用?}
    B -->|是| C[使用缓存]
    B -->|否| D[熔断器检查]
    
    D --> E{熔断器状态}
    E -->|关闭| F[降级到数据库]
    E -->|半开| G[尝试恢复]
    E -->|打开| H[直接降级]
    
    G --> I{测试成功?}
    I -->|是| J[关闭熔断器]
    I -->|否| K[重新打开]
    
    F --> L[数据库读取]
    H --> L
    J --> C
    K --> H
    
    L --> M{读取成功?}
    M -->|是| N[异步写回缓存]
    M -->|否| O[返回错误]
    
    C --> P[返回结果]
    N --> P
    O --> Q[记录告警]
    
    %% 起始节点 - 蓝色
    style A fill:#E3F2FD,stroke:#2196F3,stroke-width:2px,color:#000
    style B fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    
    %% 成功路径 - 绿色
    style C fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style P fill:#66BB6A,stroke:#2E7D32,stroke-width:3px,color:#FFF
    
    %% 熔断器 - 橙色
    style D fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    style E fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    style G fill:#FFA726,stroke:#E65100,stroke-width:2px,color:#000
    style I fill:#FFB74D,stroke:#E65100,stroke-width:2px,color:#000
    style J fill:#4CAF50,stroke:#2E7D32,stroke-width:2px,color:#FFF
    style K fill:#F44336,stroke:#C62828,stroke-width:2px,color:#FFF
    
    %% 降级路径 - 黄色（数据库）
    style F fill:#FFE082,stroke:#F57F17,stroke-width:2px,color:#000
    style H fill:#FFD54F,stroke:#F57F17,stroke-width:2px,color:#000
    style L fill:#FFC107,stroke:#F57F17,stroke-width:2px,color:#000
    style M fill:#FFE0B2,stroke:#F57C00,stroke-width:2px,color:#000
    style N fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    
    %% 错误路径 - 红色
    style O fill:#F44336,stroke:#C62828,stroke-width:2px,color:#FFF
    style Q fill:#EF5350,stroke:#C62828,stroke-width:2px,color:#FFF
```

---

## 8. 审计日志架构

> **说明**：MFA组件作为技术组件，不包含监控指标收集功能。监控指标（如Prometheus、Grafana等）应由接入的应用层统一管理和配置。组件只提供必要的审计日志记录功能，用于安全审计和问题排查。

### 8.1 审计日志体系

```mermaid
graph TB
    subgraph Component["MFA组件层"]
        A1[验证操作] --> B[审计日志服务]
        A2[绑定操作] --> B
        A3[解绑操作] --> B
        A4[状态变更] --> B
    end
    
    subgraph Log["日志处理层"]
        B --> C[日志格式化]
        C --> D[数字签名]
        D --> E[日志存储]
    end
    
    subgraph Storage["存储层"]
        E --> F[数据库<br/>业务系统自行定义]
        E --> G[日志文件<br/>可选]
    end
    
    subgraph Application["应用层（由接入应用管理）"]
        F --> H[日志采集<br/>Logstash/Fluentd]
        G --> H
        H --> I[日志存储<br/>Elasticsearch]
        I --> J[日志查询<br/>Kibana]
        I --> K[应用层监控告警<br/>由应用统一管理]
    end
    
    %% 组件层 - 蓝色系
    style A1 fill:#E3F2FD,stroke:#2196F3,stroke-width:2px,color:#000
    style A2 fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,color:#000
    style A3 fill:#90CAF9,stroke:#1976D2,stroke-width:2px,color:#000
    style A4 fill:#64B5F6,stroke:#1565C0,stroke-width:2px,color:#FFF
    style B fill:#2196F3,stroke:#0D47A1,stroke-width:3px,color:#FFF
    
    %% 日志处理层 - 绿色系
    style C fill:#C8E6C9,stroke:#4CAF50,stroke-width:2px,color:#000
    style D fill:#81C784,stroke:#388E3C,stroke-width:2px,color:#000
    style E fill:#66BB6A,stroke:#2E7D32,stroke-width:2px,color:#FFF
    
    %% 存储层 - 黄色系
    style F fill:#FFC107,stroke:#F57F17,stroke-width:3px,color:#000
    style G fill:#FFE082,stroke:#F57F17,stroke-width:2px,color:#000
    
    %% 应用层 - 灰色系（表示由应用层管理）
    style H fill:#E0E0E0,stroke:#757575,stroke-width:2px,color:#000
    style I fill:#BDBDBD,stroke:#616161,stroke-width:2px,color:#000
    style J fill:#9E9E9E,stroke:#424242,stroke-width:2px,color:#FFF
    style K fill:#9E9E9E,stroke:#616161,stroke-width:2px,color:#FFF
```

### 8.2 审计日志内容

MFA组件记录的审计日志包含以下信息：

- **操作类型**：BIND、UNBIND、VERIFY、RESET、ENABLE、DISABLE
- **认证方式**：TOTP、HOTP、BACKUP_CODE
- **操作结果**：SUCCESS、FAILED、BLOCKED
- **上下文信息**：IP地址、User-Agent、设备ID
- **数字签名**：RSA-SHA256签名，确保日志完整性

**注意**：监控指标（如QPS、成功率、耗时等）应由接入的应用层通过统一的监控体系（如Prometheus、Micrometer等）进行收集和展示，MFA组件不提供这些功能。

---

*文档版本：v2.0*  
*最后更新：2026年1月15日*
