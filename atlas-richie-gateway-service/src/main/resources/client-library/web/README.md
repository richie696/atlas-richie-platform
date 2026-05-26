# 统一HTTP客户端库

## 📚 概述

统一HTTP客户端库整合了**ECC+AES-GCM加密**和**防重复提交**功能，支持React、Angular和Vue三大主流框架。

### ✨ 核心特性

- 🔐 **ECC+AES-GCM加密** - 端到端加密，保护敏感数据
- 🛡️ **防重复提交** - 客户端和服务器双重防护
- 🎯 **灵活配置** - 每个API端点独立配置安全选项
- 🔄 **自动处理** - 密钥交换、重新握手、错误处理
- 🌐 **跨平台** - 支持Web、移动端、桌面端
- ⚛️ **框架支持** - React 18+、Angular 17+、Vue 3

## 🚀 5分钟快速开始

### 步骤1: 定义URL配置

```typescript
import { Url, Method } from './framework/url';

export class AppUrl {
    // 公开数据：不加密，不防重复
    public static readonly MENU_ALL = new Url(
        'MENU_ALL',
        '/api/menu/all',
        Method.GET
    );

    // 登录：加密，不防重复（允许快速重试）
    public static readonly USER_LOGIN = new Url(
        'USER_LOGIN',
        '/api/auth/login',
        Method.POST,
        true,   // ✅ 加密
        false   // ❌ 不防重复
    );

    // 支付：加密，防重复（最高安全）
    public static readonly PAYMENT_CREATE = new Url(
        'PAYMENT_CREATE',
        '/api/payment/create',
        Method.POST,
        true,   // ✅ 加密
        true    // ✅ 防重复提交
    );
}
```

### 步骤2: 创建HTTP客户端

```typescript
import { HttpClient } from './framework/http-client';

const client = new HttpClient({
    baseUrl: 'https://your-gateway.com',
    clientId: 'my-app',
    duplicateSubmitTimeWindow: 3000
});
```

### 步骤3: 发送请求

```typescript
// 自动根据URL配置处理加密和防重复提交
const result = await client.request(AppUrl.PAYMENT_CREATE, {
    body: { amount: 100, currency: 'CNY' }
});
```

就这么简单！🎉

## 📦 依赖说明

### 运行时依赖

**本库为零依赖**，仅使用浏览器原生API，无需安装任何npm包。

**浏览器要求**：
- 支持 Web Crypto API 的现代浏览器
- 支持 Fetch API
- 支持 ES6+

| API | 浏览器支持 |
|-----|-----------|
| `window.crypto.subtle` | Chrome 37+, Firefox 34+, Safari 11+, Edge 79+ |
| `fetch` | Chrome 42+, Firefox 39+, Safari 10.1+, Edge 14+ |
| `localStorage` / `sessionStorage` | 所有现代浏览器 |

### 开发依赖（可选）

如果使用TypeScript开发，需要安装：

```bash
npm install --save-dev typescript@^5.0.0
```

如果使用框架集成（React/Angular/Vue），需要安装对应的框架：

**React**:
```bash
npm install react@^18.0.0 react-dom@^18.0.0
```

**Angular**:
```bash
npm install @angular/core@^17.0.0 @angular/common@^17.0.0
```

**Vue**:
```bash
npm install vue@^3.0.0
```

### 注意事项

⚠️ **HTTPS要求**：Web Crypto API 需要在HTTPS环境下运行（或localhost）。在生产环境中，必须使用HTTPS。

⚠️ **浏览器兼容性**：如果需要在旧版浏览器中使用，可能需要polyfill：
- [webcrypto-polyfill](https://www.npmjs.com/package/@peculiar/webcrypto) - Web Crypto API polyfill
- [whatwg-fetch](https://www.npmjs.com/package/whatwg-fetch) - Fetch API polyfill

## 📁 目录结构

```
client-library/
├── framework/                      # 核心框架（开发人员不可修改）
│   ├── abstract.enums.ts          # 枚举基类
│   ├── method.enum.ts             # HTTP方法枚举
│   ├── url.ts                     # URL枚举类
│   └── unified-http-client.ts     # 统一HTTP客户端
│
├── examples/                       # 框架使用示例
│   ├── react/                     # React 18+ 示例
│   ├── angular/                   # Angular 17+ 示例
│   └── vue/                       # Vue 3 示例
│
└── README.md                       # 本文档
```

## 🎯 四种配置组合

### 组合1：都不使用（公开数据）

```typescript
public static readonly MENU_ALL = new Url(
    'MENU_ALL', '/api/menu/all', Method.GET
);
```

**适用**：菜单、字典、配置等公开只读数据  
**流程**：客户端 → 服务器（直接处理）

### 组合2：只使用加密（敏感数据）

```typescript
public static readonly USER_LOGIN = new Url(
    'USER_LOGIN', '/api/auth/login', Method.POST,
    true,   // 加密
    false   // 不防重复
);
```

**适用**：登录、查询敏感数据  
**流程**：客户端（加密）→ 服务器（解密 → 处理 → 加密）→ 客户端（解密）

### 组合3：只使用防重复提交（普通操作）

```typescript
public static readonly FILE_UPLOAD = new Url(
    'FILE_UPLOAD', '/api/file/upload', Method.POST,
    false,  // 不加密
    true    // 防重复
);
```

**适用**：文件上传、数据导出  
**流程**：客户端（检查重复 → 记录）→ 服务器（检查重复 → 处理）

### 组合4：两个都使用（最高安全）

```typescript
public static readonly PAYMENT_CREATE = new Url(
    'PAYMENT_CREATE', '/api/payment/create', Method.POST,
    true,   // 加密
    true    // 防重复
);
```

**适用**：支付、订单提交、转账  
**流程**：客户端（检查重复 → 加密 → 记录）→ 服务器（解密 → 检查重复 → 处理 → 加密）→ 客户端（解密 → 清理）

## 💻 框架集成

### ⚛️ React 18+

```tsx
import { useHttpClient } from './examples/react/hooks/useHttpClient';
import { AppUrl } from './examples/react/app.url';

function MyComponent() {
    const client = useHttpClient();
    
    const handleSubmit = async () => {
        await client.request(AppUrl.ORDER_SUBMIT, { body: orderData });
    };
}
```

**查看完整示例**: [examples/react/](./examples/react/)

### 🅰️ Angular 17+

```typescript
import { Component, inject } from '@angular/core';
import { HttpClientService } from './examples/angular/services/http-client.service';
import { AppUrl } from './examples/angular/app.url';

@Component({ standalone: true })
export class MyComponent {
    private httpClient = inject(HttpClientService);
    
    async handleSubmit() {
        await this.httpClient.request(AppUrl.ORDER_SUBMIT, { body: orderData });
    }
}
```

**查看完整示例**: [examples/angular/](./examples/angular/)

### 🟢 Vue 3

```vue
<script setup lang="ts">
import { useHttpClient } from './examples/vue/composables/useHttpClient';
import { AppUrl } from './examples/vue/app.url';

const client = useHttpClient();

const handleSubmit = async () => {
    await client.request(AppUrl.ORDER_SUBMIT, { body: orderData });
};
</script>
```

**查看完整示例**: [examples/vue/](./examples/vue/)

## 🔧 URL枚举系统

### Java风格枚举设计

本库采用Java风格的枚举系统，所有URL集中管理：

```typescript
export class AppUrl {
    // 页面路由
    public static readonly PAGE_HOME = new Url(
        'PAGE_HOME', '/', Method.NAVIGATOR
    );
    
    // API端点（带安全配置）
    public static readonly USER_LOGIN = new Url(
        'USER_LOGIN',           // 枚举名称
        '/api/auth/login',      // URL路径
        Method.POST,            // HTTP方法
        true,                   // 是否加密
        false                   // 是否防重复提交
    );
}
```

### 路径参数支持

```typescript
// 定义带参数的URL
public static readonly USER_DETAIL = new Url(
    'USER_DETAIL',
    '/api/user/{}',     // 使用{}作为占位符
    Method.GET,
    true,
    false
);

// 使用方式1：通过pathParams
await client.request(AppUrl.USER_DETAIL, {
    pathParams: ['123']
});

// 使用方式2：直接调用value
const url = AppUrl.USER_DETAIL.value(['123']);
// => 'https://your-gateway.com/api/user/123'
```

### 实用方法

```typescript
// 查找URL枚举
const url = Url.urlOf('/api/menu/all');
console.log(url.name); // => 'MENU_ALL'

// 枚举操作
const allUrls = Url.values();
const loginUrl = Url.nameOf('USER_LOGIN');

// 类型判断
AppUrl.USER_LOGIN.isApiRequest();      // => true
AppUrl.USER_LOGIN.isWriteOperation();  // => true
AppUrl.PAGE_HOME.isApiRequest();       // => false

// 调试信息
console.log(AppUrl.USER_LOGIN.toDebugString());
// => "Url{name='USER_LOGIN', path='/api/auth/login', method=POST, encryption=true, duplicateCheck=false}"
```

## 🔐 安全配置建议

| 业务场景 | 加密 | 防重复 | 原因 |
|---------|------|--------|------|
| 用户登录 | ✅ | ❌ | 保护密码，允许重试 |
| 用户注册 | ✅ | ✅ | 保护信息，防重复注册 |
| 查询列表 | ❌ | ❌ | 普通查询 |
| 查询详情 | ✅ | ❌ | 敏感信息，可重复查询 |
| 提交订单 | ✅ | ✅ | 高安全操作 |
| 创建支付 | ✅ | ✅ | 最高安全级别 |
| 文件上传 | ❌ | ✅ | 二进制数据，防重复 |
| 获取菜单 | ❌ | ❌ | 公开数据 |

## 🔄 工作流程

### 加密请求流程

```
客户端                          服务器
  │                              │
  ├─ 1. 密钥交换 ────────────────→│ EccCryptoFilter
  │  (首次或KeyPair更新时)        │
  │                              │
  ├─ 2. AES-GCM加密请求体 ───────→│ EccCryptoFilter解密
  │  (X-Encrypted-Data)          │
  │                              │
  │                              ├─ 3. 业务处理
  │                              │
  │←─ 4. AES-GCM加密响应 ─────────┤ EccCryptoFilter加密
  │  (X-Response-Encrypted)      │
  │                              │
  └─ 5. 解密响应数据              │
```

### 防重复提交流程

```
客户端                          服务器
  │                              │
  ├─ 1. 生成请求ID                │
  │  (URL+方法+请求体+用户ID)      │
  │                              │
  ├─ 2. 检查本地队列              │
  │  (时间窗口内有重复?)           │
  │                              │
  ├─ 3. 记录请求并发送 ──────────→│ DuplicateSubmitFilter
  │                              │  检查服务器端队列
  │                              │
  │                              ├─ 4. 业务处理
  │                              │
  │←─ 5. 返回结果 ───────────────┤
  │                              │
  └─ 6. 清理请求记录              │
```

## ⚙️ 配置选项

```typescript
const client = new HttpClient({
    baseUrl: 'https://your-gateway.com',          // 网关地址
    clientId: 'my-app',                           // 客户端ID
    duplicateSubmitTimeWindow: 3000,              // 防重复时间窗口（毫秒）
    showLoading: true,                            // 是否显示加载状态
    maxRetries: 3,                                // 最大重试次数
    retryInterval: 1000,                          // 重试间隔（毫秒）
    timeout: 30000                                // 请求超时（毫秒）
});
```

## 🔧 API参考

### HttpClient

```typescript
class HttpClient {
    // 发送请求
    async request<T>(url: Url, options?: RequestOptions): Promise<T>
    
    // 手动初始化加密（可选）
    async initializeEncryption(): Promise<void>
    
    // 更新配置
    updateConfig(config: Partial<HttpClientConfig>): void
    
    // 清理资源
    cleanup(): void
    
    // 获取用户ID
    getUserId(): string | null
    
    // 获取缓存的请求头（包括token等业务请求头）
    getCachedHeaders(): Record<string, string>
    
    // 清除缓存的请求头
    clearCachedHeaders(): void
}
```

### Url枚举类

```typescript
class Url {
    constructor(
        name: string,                           // 枚举名称
        path: string,                           // URL路径
        method: Method,                         // HTTP方法
        needEncryption?: boolean,               // 是否加密（默认false）
        needDuplicateCheck?: boolean            // 是否防重复（默认false）
    )
    
    // 获取完整URL
    value(pathParams?: any[]): string
    
    // 工具方法
    static urlOf(url: string): Url             // 根据路径查找
    static nameOf(name: string): Url           // 根据名称查找
    static values(): ReadonlyArray<Url>        // 获取所有枚举
    static setBaseUrl(baseUrl: string): void   // 设置基础URL
    
    // 属性
    readonly name: string                       // 枚举名称
    readonly path: string                       // 原始路径
    readonly method: Method                     // HTTP方法
    readonly needEncryption: boolean            // 是否需要加密
    readonly needDuplicateCheck: boolean        // 是否需要防重复
    
    // 判断方法
    isApiRequest(): boolean                     // 是否为API请求
    isWriteOperation(): boolean                 // 是否为写操作
}
```

### RequestOptions

```typescript
interface RequestOptions {
    body?: any;                                 // 请求体
    headers?: Record<string, string>;           // 额外请求头
    requestId?: string;                         // 自定义请求ID
    allowRetry?: boolean;                       // 是否允许重试
    pathParams?: any[];                         // 路径参数
}
```

## 🎨 使用示例

### 基础用法

```typescript
// 简单GET请求
const menuList = await client.request(AppUrl.MENU_ALL);

// POST请求
const loginResult = await client.request(AppUrl.USER_LOGIN, {
    body: { username: 'user', password: 'pass' }
});

// 带路径参数
const userDetail = await client.request(AppUrl.USER_DETAIL, {
    pathParams: ['123']
});

// 带自定义请求头
const result = await client.request(AppUrl.ORDER_LIST, {
    headers: { 'X-Page': '1', 'X-Page-Size': '20' }
});
```

### 错误处理

```typescript
try {
    await client.request(AppUrl.ORDER_SUBMIT, { body: orderData });
} catch (err: any) {
    if (err.message === 'DUPLICATE_REQUEST') {
        // 客户端检测到重复请求
        alert('请求正在处理中，请勿重复提交');
    } else if (err.message === 'DUPLICATE_SUBMIT') {
        // 服务器检测到重复提交（HTTP 429）
        alert('服务器检测到重复提交');
    } else if (err.status === 423) {
        // 服务器KeyPair已更新（自动重新握手）
        console.log('密钥已自动更新');
    } else {
        alert('请求失败: ' + err.message);
    }
}
```

### TypeScript类型支持

```typescript
interface User {
    userId: string;
    username: string;
    email: string;
}

// 使用泛型指定响应类型，返回ResultVO<T>
import { ResultVO, isSuccess, extractData } from './framework/http-client';

const result: ResultVO<User> = await client.request<User>(AppUrl.USER_LOGIN, {
    body: { username, password }
});

// 检查是否成功
if (isSuccess(result)) {
    const userData = extractData(result);
    console.log(userData.userId);    // ✅ 类型安全
    console.log(userData.username);  // ✅ 类型安全
    // 注意：token现在从响应头自动获取并保存到localStorage，无需手动处理
} else {
    console.error('登录失败:', result.msg);
}
```

## 🌐 框架集成指南

### ⚛️ React 18+

**特点**: React Hooks、函数式组件、自动资源管理

```tsx
import { useHttpClient } from './examples/react/hooks/useHttpClient';

function MyComponent() {
    const client = useHttpClient();
    // client会在组件卸载时自动清理
}
```

**查看**: [examples/react/README.md](./examples/react/README.md)

### 🅰️ Angular 17+

**特点**: Standalone Components、Signals、Dependency Injection

```typescript
@Component({ standalone: true })
export class MyComponent {
    private httpClient = inject(HttpClientService);
    // 资源会通过DestroyRef自动清理
}
```

**查看**: [examples/angular/README.md](./examples/angular/README.md)

### 🟢 Vue 3

**特点**: Composition API、`<script setup>`、Composables

```vue
<script setup>
import { useHttpClient } from './examples/vue/composables/useHttpClient';

const client = useHttpClient();
// 组件卸载时通过onUnmounted自动清理
</script>
```

**查看**: [examples/vue/README.md](./examples/vue/README.md)

## 📋 HTTP请求头

### 客户端自动添加的请求头

| 请求头 | 说明 | 使用场景 |
|--------|------|----------|
| X-Client-Id | 客户端唯一标识 | 所有请求 |
| X-Client-Timestamp | 客户端时间戳 | 所有请求 |
| X-Access-Token | 认证Token | 已登录用户 |
| X-User-Id | 用户ID | 已登录用户 |
| X-Gateway-KeyId | 网关密钥ID | 加密请求 |
| X-Client-Public-Key | 客户端公钥 | 密钥交换 |
| X-Encrypted-Data | 加密的请求数据 | 加密请求 |

### 服务器响应头

| 响应头 | 说明 |
|--------|------|
| X-Response-Encrypted | 响应是否加密 |

## 🔄 请求头自动管理

客户端支持请求头的自动读取和持久化功能，实现业务无侵入的请求头管理。

### 功能特性

- ✅ **自动读取**: 发送请求前，自动从localStorage读取缓存的请求头并添加到请求headers中
- ✅ **自动保存**: 响应返回后，自动将业务相关的响应头保存到localStorage
- ✅ **智能过滤**: 自动排除浏览器和系统内置的请求头，只保存业务相关的header
- ✅ **零侵入**: 完全自动化，无需在业务代码中手动处理

### 工作原理

1. **发送请求前**：
   - 自动从localStorage读取缓存的请求头（键名：`http_headers`）
   - 将缓存的请求头合并到请求headers中
   - 用户通过`options.headers`自定义的请求头优先级最高（会覆盖缓存的请求头）

2. **响应返回后**：
   - 遍历响应头，识别业务相关的header
   - 排除系统内置header（如`content-type`、`cache-control`、`x-client-*`等）
   - 将业务请求头自动保存到localStorage

### 配置选项

```typescript
const client = new HttpClient({
    baseUrl: 'https://your-gateway.com',
    // 启用请求头自动管理（默认：true）
    enableHeaderAutoManagement: true,
    // localStorage中存储请求头的键名（默认：'http_headers'）
    headerStorageKey: 'http_headers'
});
```

### 使用示例

#### 1. 自动功能（默认启用）

```typescript
// 客户端会自动处理请求头的读取和保存
// 业务代码无需关心细节

// 第一次请求：响应返回的header会被自动保存
const result1 = await client.request(AppUrl.USER_LOGIN, {
    body: { username: 'user', password: 'pass' }
});
// 如果服务器返回了 X-Custom-Token，会被自动保存到localStorage

// 第二次请求：之前保存的header会自动添加到请求中
const result2 = await client.request(AppUrl.USER_PROFILE);
// X-Custom-Token 会自动添加到请求headers中
```

#### 2. 手动管理请求头

```typescript
// 手动设置请求头到缓存
client.setCachedHeaders({
    'X-Custom-Token': 'token123',
    'X-Language': 'zh-CN',
    'X-Timezone': 'Asia/Shanghai'
});

// 查看当前缓存的请求头
const cachedHeaders = client.getCachedHeaders();
console.log(cachedHeaders);
// { 'X-Custom-Token': 'token123', 'X-Language': 'zh-CN', ... }

// 移除指定的缓存请求头
client.removeCachedHeader('X-Custom-Token');

// 清除所有缓存的请求头
client.clearCachedHeaders();
```

#### 3. 禁用自动管理

```typescript
const client = new HttpClient({
    baseUrl: 'https://your-gateway.com',
    enableHeaderAutoManagement: false  // 禁用自动管理
});

// 禁用后，需要手动管理请求头
const result = await client.request(AppUrl.USER_LOGIN, {
    headers: {
        'X-Custom-Token': 'token123'  // 需要手动传递
    }
});
```

### 系统内置请求头（自动过滤）

以下请求头不会被保存到localStorage（系统会自动过滤）：

- HTTP标准header: `content-type`, `content-length`, `cache-control`, `expires`, `date`, `server`, `connection`, `transfer-encoding`
- 浏览器header: `accept`, `accept-encoding`, `accept-language`, `referer`, `user-agent`, `origin`
- 客户端系统header: `x-client-id`, `x-client-timestamp`, `x-client-public-key`, `x-gateway-keyid`, `x-encrypted-data`
- CORS header: `access-control-*`

### 业务请求头示例

以下类型的请求头会被自动保存和使用：

```typescript
// 这些header会被自动保存和管理
{
    'X-Access-Token': 'eyJhbGciOiJIUzI1NiIs...',
    'X-Refresh-Token': 'refresh_token_here',
    'X-Custom-Token': 'custom_token',
    'X-Language': 'zh-CN',
    'X-Timezone': 'Asia/Shanghai',
    'X-Device-Id': 'device123',
    'X-User-Role': 'admin',
    'Authorization': 'Bearer token123'  // 注意：如果服务器返回，也会被保存
}
```

### 注意事项

1. **优先级**: `options.headers` > 缓存的请求头 > 系统默认请求头
2. **存储位置**: 默认使用`localStorage`，键名为`http_headers`
3. **安全性**: 敏感信息（如token）会保存在localStorage，请确保应用安全
4. **浏览器兼容性**: 需要支持localStorage的现代浏览器

## ⚠️ 常见问题

### Q: 如何判断API是否需要加密？

**需要加密**：
- ✅ 包含敏感数据（密码、支付信息、个人信息）
- ✅ 高安全要求的操作

**不需要加密**：
- ❌ 公开数据（菜单、字典、配置）
- ❌ 二进制文件（已经是二进制）
- ❌ 普通查询列表

### Q: 如何判断API是否需要防重复提交？

**需要防重复**：
- ✅ 写操作（POST、PUT、DELETE）
- ✅ 高价值操作（支付、转账、下单）
- ✅ 资源创建操作

**不需要防重复**：
- ❌ 查询操作（GET）
- ❌ 幂等操作
- ❌ 登录（允许快速重试）

### Q: 密钥交换失败怎么办？

检查：
1. 网关地址是否正确
2. 网关的ECC加密功能是否启用（`rydeen.gateway.ecc-crypto.enabled=true`）
3. 网络连接是否正常
4. 浏览器控制台的详细错误信息

### Q: 收到HTTP 423响应怎么办？

不用担心！这表示服务器KeyPair已过期更新，客户端会**自动重新握手**并重试请求。

### Q: 如何在SSR中使用？

确保 `window.crypto` API可用（可能需要polyfill）。对于Next.js等框架，建议只在客户端组件中使用。

## 🚀 性能优化

### 1. 预加载密钥交换

```typescript
// 应用启动时预先完成密钥交换
async function initApp() {
    await client.initializeEncryption();
    console.log('加密已预加载');
}
```

### 2. 按需启用功能

```typescript
// 只在需要的API启用加密和防重复提交
// 公开数据不启用任何功能，提升性能
static readonly MENU_ALL = new Url(..., false, false);
```

### 3. 合理设置时间窗口

```typescript
// 普通操作：3秒
duplicateSubmitTimeWindow: 3000

// 支付等高价值操作：5-10秒
duplicateSubmitTimeWindow: 5000

// 文件上传等耗时操作：10-30秒
duplicateSubmitTimeWindow: 10000
```

## 🛡️ 安全注意事项

1. **密钥存储** - 密钥对和共享密钥仅存储在内存中，不持久化
2. **Token管理** - 认证Token存储在localStorage，注意XSS防护
3. **HTTPS** - 必须使用HTTPS传输
4. **时间窗口** - 合理设置防重复提交时间窗口
5. **请求ID** - 自动包含用户ID，确保不同用户请求不会误判

## 🔗 服务器端配置

确保Spring Cloud Gateway配置正确：

```yaml
rydeen:
  gateway:
    ecc-crypto:
      enabled: true
      key-pair-ttl: 86400000  # 24小时
    duplicate-submit:
      enabled: true
      time-window: 3000  # 3秒
```

## 📚 快速导航

- **[examples/react/](./examples/react/)** - React完整示例
- **[examples/angular/](./examples/angular/)** - Angular完整示例
- **[examples/vue/](./examples/vue/)** - Vue完整示例
- **[examples/README.md](./examples/README.md)** - 框架示例总览

## 📖 代码示例

每个框架示例都包含：
- ✅ 完整的URL配置（app.url.ts）
- ✅ HTTP客户端封装
- ✅ 认证功能封装
- ✅ 登录表单组件
- ✅ 订单提交组件
- ✅ 完整的错误处理

## 🎉 开始使用

1. **选择你的框架** → [examples/](./examples/)
2. **复制framework目录**到你的项目
3. **复制对应框架的示例代码**
4. **配置环境变量**
5. **开始开发**！

---

**版本**: 2.0  
**作者**: richie696  
**更新日期**: 2025-11-01  
**许可证**: MIT License
