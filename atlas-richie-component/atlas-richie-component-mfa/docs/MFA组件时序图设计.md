# MFA组件时序图设计

## 📋 目录

- [1. 标准认证流程](#1-标准认证流程)
- [2. 设备绑定流程](#2-设备绑定流程)
- [3. MFA验证流程（带防重放）](#3-mfa验证流程带防重放)
- [4. 缓存降级流程](#4-缓存降级流程)
- [5. 设备信任验证流程](#5-设备信任验证流程)
- [6. 备份码恢复流程](#6-备份码恢复流程)
- [7. 密钥轮换流程](#7-密钥轮换流程)
- [8. 风控检测流程](#8-风控检测流程)
- [9. 审计日志流程](#9-审计日志流程)

---

## 1. 标准认证流程

### 1.1 完整登录认证流程（含MFA）

**架构说明**：  
- **是否需要 MFA** 由**业务登录层**统一判断：业务认证服务调用 `MfaBindManager.checkLoginMfa(tenantId, userId, deviceId)`，先校验可信设备再查 MFA 绑定，得到 `LoginMfaCheckResult`（mfaRequired、mfaBound）。若不需要 MFA（可信设备或未绑定），业务直接返回带 `data.accessToken` 的响应；否则不返回 token。  
- **网关**：若响应体 `data.accessToken` 已存在且非空，则不再调用 MFA 检查，直接放行；否则调用 `checkMfaStatus`（仅读缓存，不做可信设备校验）并返回 MFA_REQUIRED。  
- 验证阶段在网关执行，只读 Redis/KMS，不操作数据库。

```mermaid
sequenceDiagram
    participant U as 用户
    participant C as 客户端
    participant GW as 网关服务<br/>(richie-gateway-service)
    participant AS as 认证服务<br/>(业务登录，如 sample-mfa)
    participant MS as MFA管理<br/>(MfaBindManager)
    participant VF as MFA验证<br/>(validation)
    participant Cache as GlobalCache
    participant KMS as 密钥管理
    participant DB as 数据库

    U->>C: 输入用户名密码
    C->>GW: POST /api/login (含 X-Device-Id)
    GW->>AS: 认证请求
    
    AS->>DB: 查询用户信息
    DB-->>AS: 返回用户数据
    AS->>AS: 验证密码
    
    alt 密码正确
        AS->>MS: checkLoginMfa(tenantId, userId, deviceId)
        MS->>Cache: 可信设备 + MFA 绑定状态
        Cache-->>MS: 结果
        MS-->>AS: LoginMfaCheckResult(mfaRequired, mfaBound)
        
        alt mfaRequired == false（可信设备或未绑定）
            AS-->>GW: 200 + data.accessToken（业务已签发或占位）
            Note over GW: 网关发现 data.accessToken 存在，不再调 checkMfaStatus
            GW-->>C: 签发/透传 Token
            C-->>U: 登录成功
        else mfaRequired == true（已绑定且非可信设备）
            AS-->>GW: 200 + data 无 accessToken
            GW->>VF: checkMfaStatus(userId, tenantId, deviceId)
            VF->>Cache: 只读 MFA 元数据（不做可信设备校验）
            Cache-->>VF: 返回
            VF-->>GW: mfaRequired=true + 元数据
            GW-->>C: MFA_REQUIRED + mfaToken
            
            U->>C: 输入验证码
            C->>GW: POST /api/login (含 mfaCode)
            GW->>VF: 验证 MFA 码
            VF->>Cache: 读 MFA 信息
            VF->>KMS: 检索密钥（密钥存 KMS/Redis，不存 DB）
            KMS-->>VF: 明文密钥
            VF->>VF: TOTP 校验 + 防重放
            alt 验证通过
                VF-->>GW: 验证成功
                GW-->>C: 签发 Token
                C-->>U: 登录成功
            else 验证失败
                VF-->>GW: 验证失败
                GW-->>C: 错误信息
            end
        end
    else 密码错误
        AS-->>GW: 认证失败
        GW-->>C: {error: "用户名或密码错误"}
        C-->>U: 登录失败
    end
```

---

## 2. 设备绑定流程

### 2.1 完整绑定流程（含密钥加密和Liquibase）

**架构说明**：绑定流程在通用服务中执行，操作数据库，使用Liquibase管理DDL

```mermaid
sequenceDiagram
    participant U as 用户
    participant C as 客户端
    participant GW as API网关
    participant GS as 通用服务<br/>(richie-general-service)
    participant MS as MFA管理服务<br/>(management模块)
    participant LB as Liquibase<br/>(DDL管理)
    participant KMS as 密钥管理服务
    participant DB as 数据库
    participant Cache as GlobalCache<br/>(Redis)
    participant QR as QR码生成器

    Note over GS,LB: 首次启动时，Liquibase自动创建表结构
    GS->>LB: 执行数据库迁移
    LB->>DB: 创建MFA相关表（首次）
    DB-->>LB: 表创建成功
    
    U->>C: 请求绑定MFA设备
    C->>GW: POST /api/mfa/bind
    GW->>GS: 路由到通用服务
    GS->>MS: 处理绑定请求
    
    MS->>MS: 生成160位随机密钥
    MS->>KMS: 获取数据加密密钥（DEK）
    KMS-->>MS: 返回DEK
    MS->>MS: 使用AES-256-GCM加密密钥
    MS->>MS: 生成10个备份码
    MS->>MS: 使用BCrypt哈希备份码
    
    MS->>QR: 生成二维码内容
    Note over QR: otpauth://totp/Richie:user123?<br/>secret=JBSWY3DPEHPK3PXP&<br/>issuer=Richie&algorithm=SHA1&digits=6&period=30
    QR-->>MS: 返回二维码URL
    
    MS->>DB: 保存用户MFA信息（事务）
    Note over MS,DB: 1. 加密密钥<br/>2. 哈希备份码<br/>3. 状态=未激活<br/>使用Liquibase管理的表
    DB-->>MS: 保存成功
    
    MS->>Cache: 同步到缓存（分布式锁）
    Note over MS,Cache: 数据库变更后立即同步到Redis<br/>供网关验证使用
    Cache-->>MS: 同步成功
    
    MS->>DB: 记录绑定日志（异步）
    MS-->>GS: 返回绑定信息
    GS-->>GW: 返回结果
    GW-->>C: {qrCodeUrl, secretKey, backupCodes}
    
    C-->>U: 显示二维码和备份码
    U->>U: 使用Authenticator扫描二维码
    U->>U: 保存备份码
    
    U->>C: 输入验证码进行验证
    C->>GW: POST /api/mfa/activate
    GW->>GS: 路由到通用服务
    GS->>MS: 验证初始码
    
    MS->>DB: 从数据库读取用户密钥（或从缓存）
    DB-->>MS: 返回加密密钥
    MS->>KMS: 解密密钥
    KMS-->>MS: 返回明文密钥
    
    MS->>MS: 生成TOTP验证码
    MS->>MS: 验证码比对
    
    alt 验证成功
        MS->>DB: 更新状态为已激活（事务）
        MS->>Cache: 同步激活状态到缓存（分布式锁）
        Note over MS,Cache: 确保网关验证层能读取到最新状态
        MS->>DB: 记录激活日志
        MS-->>GS: 激活成功
        GS-->>GW: 返回结果
        GW-->>C: {activated: true}
        C-->>U: MFA绑定完成
    else 验证失败
        MS->>DB: 记录失败日志
        MS-->>GS: 激活失败
        GS-->>GW: 返回错误
        GW-->>C: {error: "验证码错误"}
        C-->>U: 请重新扫描二维码
    end
```

---

## 3. MFA验证流程（带防重放）

### 3.1 详细验证流程（包含防重放检查）

```mermaid
sequenceDiagram
    participant C as 客户端
    participant GW as 网关过滤器
    participant VS as 验证服务
    participant Cache as GlobalCache
    participant KMS as 密钥管理
    participant RC as 风控引擎
    participant Audit as 审计服务

    C->>GW: 提交MFA验证码
    GW->>GW: 提取userId、code、deviceId
    
    GW->>VS: 验证请求
    Note over VS: 可信设备已在登录阶段由业务 checkLoginMfa 判断，此处仅做 TOTP+防重放
    VS->>Cache: 获取用户MFA信息
    Cache-->>VS: 返回MFA信息
    
    alt 缓存命中
            VS->>KMS: 解密密钥
            KMS-->>VS: 返回明文密钥
            VS->>VS: 计算当前时间步长
            VS->>VS: 生成当前窗口验证码
            VS->>VS: 生成±1窗口验证码
            
            loop 检查3个时间窗口
                VS->>VS: 比对验证码
                alt 验证码匹配
                    VS->>Cache: 检查验证码是否已使用
                    Cache-->>VS: 检查结果
                    
                    alt 验证码未使用
                        VS->>Cache: 标记验证码已使用（TTL=90秒）
                        VS->>Cache: 更新最后使用时间
                        VS->>RC: 风险评估
                        RC-->>VS: 风险等级
                        
                        alt 风险等级正常
                            VS->>Audit: 记录成功日志（异步）
                            Note over VS: 若用户勾选信任设备，由业务/管理侧注册可信设备
                            VS-->>GW: 验证成功
                            GW-->>C: 200 OK
                        else 风险等级高
                            VS->>Audit: 记录风险事件（异步）
                            VS->>VS: 要求额外验证
                            VS-->>GW: 需要额外验证
                            GW-->>C: 401 + 风险提示
                        end
                    else 验证码已使用（重放攻击）
                        VS->>Audit: 记录重放攻击（异步）
                        VS->>RC: 上报安全事件
                        VS-->>GW: 验证失败（重放攻击）
                        GW-->>C: 401 + 安全提示
                    end
                end
            end
            
            alt 所有窗口都不匹配
                VS->>VS: 增加失败次数
                VS->>Cache: 更新失败次数
                
                alt 达到最大失败次数
                    VS->>Cache: 锁定账户
                    VS->>Audit: 记录锁定事件
                    VS-->>GW: 账户已锁定
                    GW-->>C: 403 Forbidden
                else 未达到最大次数
                    VS->>Audit: 记录失败日志
                    VS-->>GW: 验证失败
                    GW-->>C: 401 Unauthorized
                end
            end
        else 缓存未命中
            VS->>VS: 快速失败（网关不降级到数据库）
            VS-->>GW: 验证不可用
            GW-->>C: 503 或错误提示
        end
```

---

## 4. 缓存降级流程

### 4.1 网关验证层缓存故障处理（不降级到数据库）

**架构说明**：网关验证层缓存不可用时快速失败，不降级到数据库，保持网关轻量化

```mermaid
sequenceDiagram
    participant GW as 网关过滤器<br/>(gateway-service)
    participant VF as MFA验证过滤器<br/>(validation模块)
    participant CB as 熔断器
    participant Cache as GlobalCache<br/>(Redis)
    Note over MS: 监控指标由应用层统一管理<br/>MFA组件不包含监控功能

    GW->>VF: 验证请求
    VF->>CB: 检查熔断器状态
    
    alt 熔断器关闭（正常）
        VF->>Cache: 读取用户MFA信息（只读）
        Cache-->>VF: 返回结果
        
        alt 缓存命中
            VF->>VF: 正常验证流程
            VF-->>GW: 验证结果
        else 缓存未命中
            Note over VF: 网关层不降级到数据库<br/>快速失败，保持轻量化
            VF->>Monitor: 记录缓存未命中事件
            VF-->>GW: 验证失败（401 Unauthorized）
            GW-->>C: {error: "MFA配置不存在"}
        else 缓存异常
            VF->>CB: 记录失败
            CB->>CB: 更新失败计数
            
            alt 失败率超过阈值
                CB->>CB: 打开熔断器
                CB->>Monitor: 发送熔断告警
                Note over CB: 网关层不降级到数据库<br/>快速失败策略
                CB->>VF: 返回失败（熔断）
                VF-->>GW: 验证失败（503 Service Unavailable）
                GW-->>C: {error: "MFA服务暂时不可用"}
            else 失败率未超过阈值
                VF->>Monitor: 记录临时故障
                VF-->>GW: 验证失败（401 Unauthorized）
                GW-->>C: {error: "MFA验证失败"}
            end
        end
    else 熔断器打开
        VF->>CB: 检查是否可恢复
        CB->>CB: 检查等待时间
        
        alt 等待时间已过（半开状态）
            CB->>Cache: 尝试读取（测试）
            Cache-->>CB: 返回结果
            
            alt 测试成功
                CB->>CB: 关闭熔断器
                CB->>Monitor: 发送恢复通知
                CB->>VF: 正常流程
                VF->>Cache: 读取用户MFA信息
                Cache-->>VF: 返回结果
                VF->>VF: 继续验证流程
                VF-->>GW: 验证结果
            else 测试失败
                CB->>CB: 重新打开熔断器
                Note over CB: 网关层不降级到数据库
                CB->>VF: 返回失败（熔断）
                VF-->>GW: 验证失败（503 Service Unavailable）
                GW-->>C: {error: "MFA服务暂时不可用"}
            end
        else 等待时间未过
            Note over CB: 网关层不降级到数据库
            CB->>VF: 返回失败（熔断）
            VF-->>GW: 验证失败（503 Service Unavailable）
            GW-->>C: {error: "MFA服务暂时不可用"}
        end
    end
```

### 4.2 管理服务层缓存降级流程（可降级到数据库）

**架构说明**：管理服务层缓存不可用时可以降级到数据库

```mermaid
sequenceDiagram
    participant GS as 通用服务<br/>(general-service)
    participant MS as MFA管理服务<br/>(management模块)
    participant CB as 熔断器
    participant Cache as GlobalCache<br/>(Redis)
    participant DB as 数据库
    Note over MS: 监控指标由应用层统一管理<br/>MFA组件不包含监控功能

    GS->>MS: 管理操作请求
    MS->>CB: 检查熔断器状态
    
    alt 熔断器关闭（正常）
        MS->>Cache: 读取用户MFA信息
        Cache-->>MS: 返回结果
        
        alt 缓存命中
            MS->>MS: 正常处理流程
            MS-->>GS: 处理结果
        else 缓存未命中
            MS->>CB: 执行降级逻辑
            CB->>DB: 从数据库读取
            DB-->>CB: 返回MFA信息
            CB->>MS: 返回数据（降级）
            MS->>MS: 继续处理流程
            MS->>Cache: 异步写回缓存
            MS->>Monitor: 记录降级事件
            MS-->>GS: 处理结果（降级模式）
        else 缓存异常
            MS->>CB: 记录失败
            CB->>CB: 更新失败计数
            
            alt 失败率超过阈值
                CB->>CB: 打开熔断器
                CB->>Monitor: 发送熔断告警
                CB->>DB: 降级到数据库
                DB-->>CB: 返回数据
                CB->>MS: 返回数据（熔断降级）
                MS->>MS: 继续处理流程
                MS-->>GS: 处理结果（熔断降级）
            else 失败率未超过阈值
                CB->>DB: 降级到数据库
                DB-->>CB: 返回数据
                CB->>MS: 返回数据（临时降级）
                MS->>MS: 继续处理流程
                MS-->>GS: 处理结果（临时降级）
            end
        end
    else 熔断器打开
        MS->>CB: 检查是否可恢复
        CB->>CB: 检查等待时间
        
        alt 等待时间已过（半开状态）
            CB->>Cache: 尝试读取（测试）
            Cache-->>CB: 返回结果
            
            alt 测试成功
                CB->>CB: 关闭熔断器
                CB->>Monitor: 发送恢复通知
                CB->>MS: 正常流程
            else 测试失败
                CB->>CB: 重新打开熔断器
                CB->>DB: 继续降级
                DB-->>CB: 返回数据
                CB->>MS: 返回数据（熔断降级）
            end
        else 等待时间未过
            CB->>DB: 直接降级
            DB-->>CB: 返回数据
            CB->>MS: 返回数据（熔断降级）
        end
        
        MS->>MS: 继续处理流程
        MS-->>GS: 处理结果（熔断降级）
    end
```

---

## 5. 设备信任验证流程

**说明**：  
- **“是否跳过 MFA”** 在**登录阶段**由业务层 `MfaBindManager.checkLoginMfa` 判断（先查可信设备再查绑定），不在网关验证流程中判断。  
- 本节描述：**登录时**若为可信设备则无需 MFA；以及 **MFA 验证成功后** 用户选择“信任此设备”时，管理服务注册/更新可信设备的流程。

### 5.1 登录阶段可信设备判断 + 验证后可选注册可信设备

```mermaid
sequenceDiagram
    participant C as 客户端
    participant GW as 网关
    participant AS as 业务认证
    participant MS as MfaBindManager
    participant DS as TrustedDeviceManager
    participant Cache as GlobalCache
    participant DB as 数据库

    Note over AS,MS: 登录阶段：是否需 MFA 由业务 checkLoginMfa 决定
    C->>GW: POST /api/login (含 X-Device-Id)
    GW->>AS: 认证请求
    AS->>MS: checkLoginMfa(tenantId, userId, deviceId)
    MS->>DS: isTrustedDevice(tenantId, userId, deviceId)
    DS->>Cache: 查询设备信任信息
    Cache-->>DS: 返回设备信息
    DS-->>MS: 是否可信
    MS->>Cache: 是否已绑定 MFA
    Cache-->>MS: 绑定状态
    MS-->>AS: LoginMfaCheckResult(mfaRequired, mfaBound)
    
    alt mfaRequired == false（可信设备或未绑定）
        AS-->>GW: 200 + data.accessToken
        GW-->>C: 登录成功（无需 MFA）
    else mfaRequired == true
        AS-->>GW: 200 无 accessToken
        GW-->>C: MFA_REQUIRED
        C->>GW: 提交 MFA 验证码（可选 trustDevice: true）
        GW->>GW: MFA 验证通过后
        alt 用户选择信任设备
            AS->>DS: 注册可信设备
            DS->>DB: 保存设备信息（事务）
            DS->>Cache: 同步到缓存
            DS-->>AS: 注册成功
            AS-->>GW: 登录成功 + trustedDevice: true
            GW-->>C: 200 OK
        else 用户未选择信任
            AS-->>GW: 登录成功
            GW-->>C: 200 OK
        end
    end
```

---

## 6. 备份码恢复流程

### 6.1 使用备份码恢复访问

```mermaid
sequenceDiagram
    participant U as 用户
    participant C as 客户端
    participant GW as 网关
    participant AS as 认证服务
    participant MS as MFA管理服务
    participant DB as 数据库
    participant Cache as GlobalCache

    U->>C: 输入用户名密码
    C->>GW: POST /api/login
    GW->>AS: 基础认证
    AS-->>GW: 认证成功，需要MFA
    
    U->>C: 选择使用备份码
    C->>GW: POST /api/mfa/recover
    Note over C,GW: recoveryType BACKUP_CODE + code
    GW->>MS: 恢复请求
    
    MS->>Cache: 获取用户MFA信息
    Cache-->>MS: 返回MFA信息（含哈希备份码列表）
    
    MS->>MS: 遍历备份码列表
    loop 遍历所有备份码
        MS->>MS: 使用BCrypt验证备份码
        alt 备份码匹配
            MS->>DB: 删除该备份码（事务）
            DB-->>MS: 删除成功
            MS->>Cache: 更新缓存（删除备份码）
            Cache-->>MS: 更新成功
            MS->>DB: 记录恢复日志
            MS->>MS: 临时禁用MFA（可选）
            MS-->>GW: 恢复成功
            GW->>AS: 创建会话
            AS-->>GW: 访问令牌
            GW-->>C: token + recoverySession
            C-->>U: 登录成功，提示重新绑定MFA
            Note over MS: 匹配成功，跳出循环
        end
    end
```

**说明**：若所有备份码均不匹配，则 MS 记录失败尝试与日志，GW 向客户端返回错误，提示用户重试。

---

## 7. 密钥轮换流程

**说明**：当前实现中用户 TOTP 密钥存于 **SecretKeyManager**（KMS/Redis），不存于 `mfa_user_info` 表；轮换时由管理服务与 KMS/Redis 交互，具体策略以组件实现为准。

### 7.1 自动密钥轮换

```mermaid
sequenceDiagram
    participant Scheduler as 定时任务
    participant MS as MFA管理服务
    participant KMS as 密钥管理服务
    participant DB as 数据库
    participant Cache as GlobalCache
    participant Notify as 通知服务

    Scheduler->>MS: 触发密钥轮换检查（每天凌晨2点）
    MS->>DB: 查询需要轮换的用户
    Note over MS,DB: key_rotation_time < NOW() - 90天
    DB-->>MS: 返回用户列表
    
    loop 遍历每个用户
        MS->>MS: 生成新密钥
        MS->>KMS: 使用新DEK加密新密钥
        KMS-->>MS: 返回加密后的新密钥
        
        MS->>DB: 更新密钥（事务）
        Note over MS,DB: 1. 保存新密钥<br/>2. 保留旧密钥（宽限期）<br/>3. 更新key_rotation_time
        DB-->>MS: 更新成功
        
        MS->>Cache: 同步新密钥到缓存
        Cache-->>MS: 同步成功
        
        MS->>Notify: 发送轮换通知（异步）
        Note over Notify: 通知用户密钥已轮换，<br/>需要重新扫描二维码
        
        alt 宽限期（7天）内
            MS->>MS: 新旧密钥同时有效
            Note over MS: 验证时同时检查新旧密钥
        else 宽限期后
            MS->>DB: 删除旧密钥
            DB-->>MS: 删除成功
            MS->>Cache: 更新缓存（删除旧密钥）
        end
    end
    
    MS->>Scheduler: 轮换完成
```

---

## 8. 风控检测流程

### 8.1 风险评分与自适应验证

```mermaid
sequenceDiagram
    participant GW as 网关
    participant VS as 验证服务
    participant RC as 风控引擎
    participant Cache as GlobalCache
    participant ML as 机器学习模型
    participant Audit as 审计服务

    GW->>VS: MFA验证请求
    VS->>RC: 风险评估请求
    Note over VS,RC: userId, ipAddress, deviceId,<br/>userAgent, timestamp
    
    RC->>Cache: 查询IP历史记录
    Cache-->>RC: IP访问频次、地理位置
    
    RC->>Cache: 查询用户行为模式
    Cache-->>RC: 历史登录时间、设备、地点
    
    RC->>Cache: 查询失败尝试记录
    Cache-->>RC: 最近失败次数、时间
    
    RC->>ML: 计算风险评分
    Note over RC,ML: 基于多维度特征<br/>IP异常、时间异常、<br/>设备异常、行为异常
    ML-->>RC: 风险评分（0-100）
    
    RC->>RC: 判断风险等级
    
    alt 高风险（评分 > 80）
        RC->>Audit: 记录高风险事件
        RC->>VS: 返回HIGH_RISK
        VS->>VS: 要求额外验证
        Note over VS: 1. 要求输入备份码<br/>2. 发送邮件验证<br/>3. 人工审核
        VS-->>GW: 需要额外验证
        GW-->>C: 401 + 风险提示
    else 中等风险（评分 40-80）
        RC->>Audit: 记录中等风险事件
        RC->>VS: 返回MEDIUM_RISK
        VS->>VS: 标准MFA验证
        Note over VS: 正常TOTP验证流程
        VS-->>GW: 标准验证
        GW-->>C: 继续验证流程
    else 低风险（评分 < 40）
        RC->>VS: 返回LOW_RISK
        VS->>VS: 简化验证（可选）
        Note over VS: 1. 设备信任可跳过<br/>2. 放宽时间窗口<br/>3. 减少重试限制
        VS-->>GW: 简化验证
        GW-->>C: 继续验证流程
    end
    
    RC->>Cache: 更新风险评分缓存
    Note over RC,Cache: 用于后续风险分析
```

---

## 9. 审计日志流程

**说明**：MFA 组件通过 Spring `ApplicationEventPublisher` 发布审计事件，业务系统通过 `@EventListener` 监听并自行处理（持久化、签名、归档等）。

### 9.1 MFA 审计事件发布与处理

```mermaid
sequenceDiagram
    participant MFA as MFA组件<br/>(management/validation)
    participant EventPub as ApplicationEventPublisher<br/>(Spring)
    participant Listener as 业务监听器<br/>(@EventListener)
    participant Sign as 签名服务<br/>(可选)
    participant DB as 数据库<br/>(业务系统自行定义)
    participant Archive as 归档服务<br/>(可选)

    Note over MFA: MFA操作执行<br/>(绑定/验证/解绑等)
    MFA->>EventPub: publishEvent(MfaAuditEvent)
    Note over MFA,EventPub: 发布审计事件<br/>非阻塞，异步
    
    EventPub->>Listener: 分发事件
    Note over EventPub,Listener: Spring事件机制<br/>同步或异步处理
    
    Listener->>Listener: 转换为实体对象
    
    opt 业务系统启用数字签名
        Listener->>Sign: 请求数字签名
        Sign->>Sign: 使用RSA私钥签名
        Sign-->>Listener: 返回签名
        Listener->>Listener: 附加签名到日志
    end
    
    Listener->>DB: 持久化到数据库（异步）
    DB-->>Listener: 保存成功
    
    opt 业务系统启用归档
        Note over Listener,Archive: 定时任务（每天凌晨3点）
        Archive->>DB: 查询需要归档的日志
        DB-->>Archive: 返回日志列表
        Archive->>Archive: 批量归档 + 匿名化
        Archive->>Archive: 上传到对象存储
        Archive->>DB: 标记为已归档
    end
```

**关键点**：
- MFA 组件只负责发布事件，不直接写数据库
- 业务系统通过 `@EventListener` 监听并自行决定处理方式
- 持久化、签名、归档等由业务系统实现，MFA 组件不关心

---

## 10. 缓存同步流程

### 10.1 数据库变更同步到缓存

```mermaid
sequenceDiagram
    participant MS as 管理服务
    participant DB as 数据库
    participant Lock as 分布式锁
    participant Cache as GlobalCache
    participant MQ as 消息队列

    MS->>DB: 更新用户MFA信息（事务）
    DB-->>MS: 更新成功
    
    MS->>Lock: 获取分布式锁
    Note over MS,Lock: lockKey = "mfa:sync:lock:{userId}"
    Lock-->>MS: 获取锁成功
    
    MS->>MS: 构建缓存对象
    MS->>Cache: 写入缓存
    Cache-->>MS: 写入成功
    
    MS->>MQ: 发布缓存更新事件（可选）
    Note over MS,MQ: 用于集群间同步
    MQ-->>MS: 发布成功
    
    MS->>Lock: 释放分布式锁
    Lock-->>MS: 释放成功
    
    alt 其他节点收到事件
        MQ->>OtherNode: 推送更新事件
        OtherNode->>Cache: 刷新本地缓存
        Cache-->>OtherNode: 刷新成功
    end
```

---

*文档版本：v2.0*  
*最后更新：2026年1月15日*
