# Node.js HTTP客户端库

Node.js版本的HTTP客户端库，支持ECC+AES-GCM加密和防重复提交功能。

## 📦 特性

- ✅ **ECC+AES-GCM加密** - 使用Node.js原生crypto模块
- ✅ **防重复提交** - 客户端和服务器双重防护
- ✅ **灵活的配置** - 每个API端点独立配置安全选项
- ✅ **自动处理** - 密钥交换、重新握手、错误处理
- ✅ **请求头自动管理** - 自动读取和保存业务请求头，零侵入
- ✅ **零外部依赖** - 仅使用Node.js内置模块（Node.js 18+）

## 🚀 快速开始

### 前置要求

- **Node.js 18.0.0+**（必需，使用内置fetch和crypto模块）
- TypeScript（可选，但推荐）

### 依赖说明

#### 运行时依赖

**本库为零外部运行时依赖**，仅使用Node.js内置模块：

| 模块 | Node.js版本 | 说明 |
|------|------------|------|
| `crypto` | 所有版本 | 用于ECC密钥交换和AES-GCM加密 |
| `fetch` | 18.0.0+ | 用于HTTP请求（Node.js 18+内置） |

**无需安装任何npm包即可运行！**

#### 开发依赖（可选）

如果使用TypeScript开发，需要安装开发依赖：

```bash
npm install --save-dev typescript@^5.0.0
npm install --save-dev @types/node@^20.0.0
npm install --save-dev ts-node@^10.9.0  # 用于直接运行TypeScript
```

或者使用项目提供的 `package.json`：

```bash
cd nodejs
npm install --save-dev
```

### 安装

```bash
# 方法1: 复制framework目录到你的项目
cp -r nodejs/framework your-project/src/lib/

# 方法2: 如果项目支持TypeScript，可以直接使用
# 无需额外安装，直接导入即可
```

### 基本使用

```typescript
import { HttpClient } from './framework/http-client';
import { AppUrl } from './app.url';

// 创建客户端
const client = new HttpClient({
    baseUrl: 'https://your-gateway.com',
    clientId: 'my-nodejs-client',
    userId: 'user123',
    enableHeaderAutoManagement: true  // 默认启用请求头自动管理
});

// 发送请求
import { ResultVO, isSuccess, extractData } from './framework/http-client';

interface User {
    userId: string;
    username: string;
    email: string;
}

const result: ResultVO<User> = await client.request<User>(AppUrl.USER_LOGIN, {
    body: { username: 'user', password: 'pass' }
});

if (isSuccess(result)) {
    const userData = extractData(result);
    console.log('登录成功:', userData);
    // 注意：token现在从响应头自动获取并保存到内存存储，无需手动处理
}
```

## 📚 详细使用

### 1. 定义URL配置

```typescript
import { Url, Method } from './framework/http-client';

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

### 2. 创建客户端

```typescript
const client = new HttpClient({
    baseUrl: 'https://your-gateway.com',
    clientId: 'my-nodejs-client',
    userId: 'user123',              // 可选：用于防重复提交
    duplicateSubmitTimeWindow: 3000, // 防重复提交时间窗口（毫秒）
    timeout: 30000,                  // 请求超时（毫秒）
    enableHeaderAutoManagement: true // 是否启用请求头自动管理（默认：true）
});
```

### 3. 发送请求

```typescript
// 基本请求
const result = await client.request(AppUrl.MENU_ALL);

// 带请求体
const result = await client.request(AppUrl.USER_LOGIN, {
    body: { username: 'user', password: 'pass' }
});

// 带自定义用户ID（覆盖配置中的值）
const result: ResultVO<OrderResult> = await client.request<OrderResult>(AppUrl.ORDER_SUBMIT, {
    body: { items: [...] },
    userId: 'another-user'
});

if (isSuccess(result)) {
    const orderData = extractData(result);
    console.log('订单提交成功:', orderData);
}
```

## 🔄 请求头自动管理

客户端支持请求头的自动读取和持久化功能，实现业务无侵入的请求头管理。

### 功能特性

- ✅ **自动读取**: 发送请求前，自动从内存存储读取缓存的请求头并添加到请求headers中
- ✅ **自动保存**: 响应返回后，自动将业务相关的响应头保存到内存存储
- ✅ **智能过滤**: 自动排除浏览器和系统内置的请求头，只保存业务相关的header
- ✅ **零侵入**: 完全自动化，无需在业务代码中手动处理

### 工作原理

1. **发送请求前**：
   - 自动从内存存储读取缓存的请求头
   - 将缓存的请求头合并到请求headers中
   - 用户通过`options.headers`自定义的请求头优先级最高（会覆盖缓存的请求头）

2. **响应返回后**：
   - 遍历响应头，识别业务相关的header
   - 排除系统内置header（如`content-type`、`cache-control`、`x-client-*`等）
   - 将业务请求头自动保存到内存存储

**注意**: Node.js版本使用**内存存储**（实例变量），与Web版本的localStorage不同。数据在客户端实例生命周期内有效。

### 配置选项

```typescript
const client = new HttpClient({
    baseUrl: 'https://your-gateway.com',
    // 启用请求头自动管理（默认：true）
    enableHeaderAutoManagement: true
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
// 如果服务器返回了 X-Custom-Token，会被自动保存到内存存储

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

以下请求头不会被保存（系统会自动过滤）：

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
2. **存储方式**: Node.js版本使用**内存存储**（实例变量），数据在客户端实例生命周期内有效
3. **与Web版本差异**: Web版本使用localStorage持久化，Node.js版本使用内存存储
4. **并发安全**: 内存存储是实例级别的，多实例之间不会共享缓存

## 🔧 与Web版本的差异

Node.js版本的主要差异：

| 功能 | Web版本 | Node.js版本 |
|------|---------|------------|
| **加密API** | `window.crypto.subtle` | `crypto`模块 + `webcrypto` |
| **HTTP请求** | `fetch` | `fetch` (Node.js 18+) |
| **存储** | `localStorage`/`sessionStorage` | 配置对象 |
| **用户ID/Token** | 从storage读取 | 从配置或options传入 |
| **请求头存储** | `localStorage`（持久化） | 内存存储（实例级别） |
| **加载状态** | Capacitor/DOM | 无（服务器端不需要） |

## 📝 配置说明

### HttpClientConfig

```typescript
interface HttpClientConfig {
    baseUrl?: string;                      // 网关基础URL
    clientId?: string;                     // 客户端ID
    duplicateSubmitTimeWindow?: number;    // 防重复提交时间窗口（毫秒）
    maxRetries?: number;                   // 最大重试次数
    retryInterval?: number;                // 重试间隔（毫秒）
    timeout?: number;                      // 请求超时（毫秒）
    userId?: string;                       // 用户ID（可选）
    enableHeaderAutoManagement?: boolean;  // 是否启用请求头自动管理（默认：true）
}
```

### RequestOptions

```typescript
interface RequestOptions {
    body?: any;                    // 请求体数据
    headers?: Record<string, string>; // 额外的请求头
    requestId?: string;            // 自定义请求ID
    allowRetry?: boolean;          // 是否允许重试
    pathParams?: any[];            // URL路径参数
    userId?: string;               // 自定义用户ID
}
```

## 🎯 四种配置组合

| 加密 | 防重复 | 适用场景 | 示例 |
|------|--------|---------|------|
| ❌ | ❌ | 公开数据 | 菜单列表、字典数据 |
| ✅ | ❌ | 敏感数据，可重试 | 用户登录 |
| ❌ | ✅ | 普通操作，防重复 | 文件上传 |
| ✅ | ✅ | 高安全操作 | 支付、订单提交 |

## 🔐 加密说明

Node.js版本使用：

1. **ECC密钥交换**: `crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' })`
2. **ECDH共享密钥**: `crypto.createECDH().computeSecret()`
3. **AES-256-GCM加密**: `crypto.createCipheriv('aes-256-gcm', key, iv)`

所有加密功能都使用Node.js内置的`crypto`模块，无需外部依赖。

## ⚠️ 注意事项

1. **Node.js版本**: 必需Node.js 18.0.0+（内置fetch和crypto模块支持）
2. **并发安全**: HttpClient实例不是线程安全的，如果需要在多个请求间共享，请使用单例模式
3. **内存管理**: 长时间运行的应用，建议定期调用`client.cleanup()`清理资源
4. **错误处理**: 始终使用try-catch捕获请求错误
5. **TypeScript**: 如果使用TypeScript，需要安装开发依赖（见上方"依赖说明"）

## 📖 示例代码

### 基础示例

查看 `examples/` 目录获取基础示例：

- `app.url.ts` - URL配置示例
- `main.ts` - 基础使用示例

### 框架集成示例

查看 `examples/express/` 目录获取Express.js框架集成示例：

- `app.ts` - Express应用集成示例
- `README.md` - Express集成说明

**包含内容**：
- ✅ Express中间件集成
- ✅ 服务类封装
- ✅ 路由处理器示例
- ✅ 错误处理示例

## 🆚 与Web客户端对比

Node.js版本和Web版本共享相同的URL枚举定义方式，但底层实现不同：

**相同点**:
- URL枚举定义方式
- API接口
- 加密算法
- 防重复提交逻辑

**不同点**:
- 加密API实现（Node.js crypto vs Web Crypto API）
- 存储方式（配置对象 vs localStorage）
- 用户ID/Token获取方式
- 无UI相关功能（加载状态等）

---

**版本**: 1.0  
**更新**: 2025-11-01  
**作者**: richie696

