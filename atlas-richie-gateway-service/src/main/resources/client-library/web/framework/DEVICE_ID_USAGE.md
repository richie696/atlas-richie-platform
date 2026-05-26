# 浏览器设备ID使用指南

## 一、概述

浏览器设备识别是MFA可信设备功能的基础。本工具提供了可靠的浏览器设备识别方案，结合**浏览器指纹**和**本地存储**，确保设备ID的稳定性和唯一性。

## 二、实现方案

### 2.1 核心策略

采用**混合方案**，结合两种技术：

1. **浏览器指纹（Browser Fingerprint）**：
   - 基于多种浏览器特征生成唯一标识
   - 包括：User-Agent、屏幕分辨率、时区、语言、Canvas指纹、WebGL指纹、硬件信息等
   - 优点：不需要存储，每次都能生成
   - 缺点：可能不够稳定（浏览器更新、插件变化等）

2. **LocalStorage / SessionStorage**：
   - 首次访问时生成UUID，存储在localStorage中
   - 优点：简单、稳定（同一浏览器/设备下）
   - 缺点：用户清除浏览器数据会丢失

### 2.2 工作流程

```
1. 首次访问
   ├─ 检查 LocalStorage 中是否有设备ID
   ├─ 如果没有 → 生成浏览器指纹（SHA-256哈希）
   └─ 保存到 LocalStorage（如果失败，降级到 SessionStorage）

2. 后续访问
   ├─ 从 LocalStorage 读取设备ID
   └─ 直接返回（稳定、快速）

3. 隐私模式
   ├─ LocalStorage 不可用
   ├─ 降级到 SessionStorage（会话级）
   └─ 每次新会话可能生成新的设备ID（预期行为）
```

## 三、使用方法

### 3.1 基础使用

```typescript
import { getOrCreateDeviceId, getDeviceName } from './device-id';

// 1. 获取设备ID（自动处理生成和存储）
const deviceId = await getOrCreateDeviceId();
console.log('设备ID:', deviceId); // 64字符的SHA-256哈希

// 2. 获取设备名称（用于显示）
const deviceName = getDeviceName();
console.log('设备名称:', deviceName); // "Chrome on Windows PC"
```

### 3.2 登录时使用

```typescript
import { getOrCreateDeviceId, getDeviceName } from './device-id';

async function login(username: string, password: string, mfaCode?: string, trustDevice?: boolean) {
    // 1. 获取设备ID和设备名称
    const deviceId = await getOrCreateDeviceId();
    const deviceName = getDeviceName();
    
    // 2. 构建登录请求
    const loginRequest = {
        username,
        password,
        mfaCode,
        deviceId,        // 设备ID（必须）
        deviceName,      // 设备名称（可选，用于显示）
        trustDevice      // 是否信任此设备（可选）
    };
    
    // 3. 发送登录请求
    const response = await fetch('/api/login', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Device-Id': deviceId  // 也可以通过Header传递
        },
        body: JSON.stringify(loginRequest)
    });
    
    return await response.json();
}
```

### 3.3 集成到HTTP客户端

如果使用项目中的 `HttpClient`，可以在请求时自动添加设备ID：

```typescript
import { HttpClient } from './http-client';
import { getOrCreateDeviceId } from './device-id';

// 方式1：在请求时手动添加Header
const deviceId = await getOrCreateDeviceId();
const response = await httpClient.request(LoginUrl.LOGIN, {
    body: { username, password },
    headers: {
        'X-Device-Id': deviceId
    }
});

// 方式2：扩展HttpClient，自动添加设备ID（推荐）
// 在HttpClient的构造函数或请求方法中，自动获取并添加设备ID
class HttpClient {
    private deviceId: string | null = null;
    
    async request<T>(url: Url, options: RequestOptions = {}): Promise<ResultVO<T>> {
        // 自动获取设备ID（如果还没有）
        if (!this.deviceId) {
            this.deviceId = await getOrCreateDeviceId();
        }
        
        // 自动添加到请求头
        const headers = {
            'X-Device-Id': this.deviceId,
            ...options.headers
        };
        
        // ... 发送请求
    }
}
```

### 3.4 MFA验证时使用

```typescript
import { getOrCreateDeviceId, getDeviceName } from './device-id';

async function verifyMfa(mfaCode: string, trustDevice: boolean) {
    const deviceId = await getOrCreateDeviceId();
    const deviceName = getDeviceName();
    
    const response = await fetch('/api/mfa/verify', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Device-Id': deviceId
        },
        body: JSON.stringify({
            mfaCode,
            deviceId,
            deviceName,
            trustDevice
        })
    });
    
    return await response.json();
}
```

### 3.5 后续API请求时使用

所有后续API请求都需要携带设备ID（通过Header `X-Device-Id` 或查询参数 `deviceId`），网关会验证Token中的设备ID是否与请求中的设备ID匹配。

```typescript
// 在HTTP拦截器中自动添加设备ID
axios.interceptors.request.use(async (config) => {
    const deviceId = await getOrCreateDeviceId();
    config.headers['X-Device-Id'] = deviceId;
    return config;
});
```

## 四、方案可靠性分析

### 4.1 优点

✅ **稳定性高**：
- LocalStorage持久化，同一浏览器/设备下设备ID保持不变
- 即使浏览器重启、页面刷新，设备ID仍然一致

✅ **唯一性好**：
- 浏览器指纹基于多种特征，不同浏览器/设备会生成不同的设备ID
- SHA-256哈希确保长度固定（64字符），便于存储和比较

✅ **隐私友好**：
- 设备ID是哈希值，不包含原始设备信息
- 用户清除浏览器数据后，会生成新的设备ID（符合隐私预期）

✅ **兼容性强**：
- 支持所有现代浏览器（Chrome、Firefox、Safari、Edge等）
- LocalStorage不可用时，自动降级到SessionStorage

### 4.2 局限性

⚠️ **隐私模式**：
- 隐私模式下，LocalStorage和SessionStorage可能都不可用
- 每次访问可能生成新的设备ID（这是预期行为，符合隐私保护）

⚠️ **浏览器更新**：
- 浏览器大版本更新可能改变某些特征（如User-Agent）
- 但由于LocalStorage的存在，设备ID仍然稳定

⚠️ **清除数据**：
- 用户清除浏览器数据（包括LocalStorage）后，设备ID会丢失
- 下次访问会生成新的设备ID，需要重新信任设备

⚠️ **多浏览器**：
- 同一台电脑上的不同浏览器会生成不同的设备ID
- 这是预期行为，每个浏览器都需要单独信任

### 4.3 与其他方案对比

| 方案                            | 稳定性   | 唯一性   | 隐私友好  | 实现复杂度 | 推荐度      |
|-------------------------------|-------|-------|-------|-------|----------|
| **浏览器指纹 + LocalStorage（本方案）** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐  | ⭐⭐⭐   | ✅ **推荐** |
| 纯浏览器指纹                        | ⭐⭐⭐   | ⭐⭐⭐⭐  | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐  | ⚠️ 稳定性差  |
| Cookie                        | ⭐⭐⭐⭐  | ⭐⭐⭐   | ⭐⭐⭐   | ⭐⭐    | ⚠️ 可能被清除 |
| IndexedDB                     | ⭐⭐⭐⭐⭐ | ⭐⭐⭐   | ⭐⭐⭐   | ⭐⭐⭐   | ⚠️ 实现复杂  |

## 五、最佳实践

### 5.1 设备ID管理

1. **首次访问时生成**：
   - 在应用启动时调用 `getOrCreateDeviceId()`
   - 将设备ID缓存在内存中，避免重复读取LocalStorage

2. **登录时传递**：
   - 登录请求必须携带设备ID（通过Body或Header）
   - 如果用户选择"信任此设备"，后端会注册为可信设备

3. **后续请求携带**：
   - 所有API请求都应携带设备ID（通过Header `X-Device-Id`）
   - 网关会验证Token中的设备ID是否匹配

### 5.2 错误处理

```typescript
try {
    const deviceId = await getOrCreateDeviceId();
    // 使用设备ID
} catch (error) {
    console.error('获取设备ID失败:', error);
    // 降级处理：使用临时ID或提示用户
    // 注意：这种情况下，可信设备功能可能不可用
}
```

### 5.3 用户提示

如果检测到隐私模式或存储不可用，可以提示用户：

```typescript
async function checkStorageAvailable(): Promise<boolean> {
    try {
        const testKey = '__storage_test__';
        localStorage.setItem(testKey, 'test');
        localStorage.removeItem(testKey);
        return true;
    } catch {
        return false;
    }
}

// 使用
const storageAvailable = await checkStorageAvailable();
if (!storageAvailable) {
    console.warn('浏览器存储不可用，设备信任功能可能不稳定');
    // 可以提示用户："为了使用设备信任功能，请允许浏览器存储数据"
}
```

## 六、安全考虑

### 6.1 设备ID泄露

- 设备ID是哈希值，不包含原始设备信息
- 即使泄露，也无法反推出设备特征
- 建议：设备ID不要用于其他业务逻辑，仅用于设备识别

### 6.2 设备ID伪造

- 前端生成的设备ID可以被伪造（通过修改LocalStorage）
- 后端应结合其他因素（IP、User-Agent等）进行验证
- 建议：设备ID主要用于"信任设备"功能，不应作为唯一的安全凭证

### 6.3 跨站攻击

- LocalStorage是域名隔离的，不同域名无法访问
- 建议：确保设备ID只在同源请求中使用

## 七、常见问题

### Q1: 为什么同一台电脑的不同浏览器会生成不同的设备ID？

**A**: 这是预期行为。每个浏览器都有独立的LocalStorage，且浏览器指纹也不同。每个浏览器都需要单独信任。

### Q2: 清除浏览器数据后，设备ID会丢失吗？

**A**: 是的。清除浏览器数据（包括LocalStorage）后，设备ID会丢失，下次访问会生成新的设备ID，需要重新信任设备。

### Q3: 隐私模式下设备ID稳定吗？

**A**: 不稳定。隐私模式下，LocalStorage和SessionStorage可能都不可用，每次访问可能生成新的设备ID。这是符合隐私保护预期的行为。

### Q4: 设备ID可以用于其他业务逻辑吗？

**A**: 不建议。设备ID主要用于MFA可信设备功能。如果用于其他业务逻辑，应结合其他因素（IP、User-Agent等）进行验证。

### Q5: 如何测试设备ID功能？

**A**: 
1. 清除浏览器数据，验证是否生成新的设备ID
2. 在不同浏览器中访问，验证是否生成不同的设备ID
3. 在同一浏览器中多次访问，验证设备ID是否保持一致

## 八、参考实现

完整实现请参考：
- `device-id.ts`：设备ID生成工具
- `http-client.ts`：HTTP客户端集成示例
- `MFA组件完整设计方案.md`：MFA组件设计文档
