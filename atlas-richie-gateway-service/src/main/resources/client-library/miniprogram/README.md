# 统一HTTP客户端库 - 微信小程序版本

## 📚 概述

统一HTTP客户端库（小程序版本）整合了**ECC+AES-GCM加密**和**防重复提交**功能，专为微信小程序环境设计。

### ✨ 核心特性

- 🔐 **ECC+AES-GCM加密** - 端到端加密，保护敏感数据
- 🛡️ **防重复提交** - 客户端和服务器双重防护
- 🎯 **灵活配置** - 每个API端点独立配置安全选项
- 🔄 **自动处理** - 密钥交换、重新握手、错误处理
- 📱 **小程序优化** - 使用小程序原生API，性能优化

## 🚀 5分钟快速开始

### 步骤1: 安装依赖

```bash
npm install @noble/curves @noble/hashes node-forge
```

### 步骤2: 定义URL配置

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

### 步骤3: 创建HTTP客户端

```typescript
import { HttpClient } from './framework/http-client';

const client = new HttpClient({
    baseUrl: 'https://your-gateway.com',
    clientId: 'my-miniprogram',
    duplicateSubmitTimeWindow: 3000
});
```

### 步骤4: 发送请求

```typescript
// 自动根据URL配置处理加密和防重复提交
const result = await client.request(AppUrl.PAYMENT_CREATE, {
    body: { amount: 100, currency: 'CNY' }
});
```

就这么简单！🎉

## 📦 依赖说明

### 运行时依赖

| 包名 | 版本 | 用途 |
|------|------|------|
| `@noble/curves` | ^1.2.0 | ECC加密（支持secp256r1/P-256曲线） |
| `@noble/hashes` | ^1.3.0 | 哈希工具（用于随机数生成） |
| `node-forge` | ^1.3.1 | AES-GCM加密/解密 |

### 开发依赖

- `typescript`: ^1.0.0（如果使用TypeScript）

### 安装命令

```bash
npm install @noble/curves @noble/hashes node-forge
```

## 🔐 加密算法说明

### ECC密钥交换

- **曲线**: secp256r1 (P-256)
- **库**: `@noble/curves` 的 `p256` 模块
- **格式**: SPKI格式（与Java后端兼容）

### AES-GCM加密

- **算法**: AES-256-GCM
- **库**: `node-forge`
- **IV长度**: 12字节
- **Tag长度**: 16字节

## 📁 目录结构

```
miniprogram/
├── framework/                      # 核心框架（开发人员不可修改）
│   ├── abstract.enums.ts          # 枚举基类
│   ├── method.enum.ts             # HTTP方法枚举
│   ├── url.ts                     # URL枚举类
│   ├── result-vo.ts                # 响应结果类型
│   └── http-client.ts             # 统一HTTP客户端
│
├── examples/                       # 使用示例
│   ├── app.url.ts                 # URL配置示例
│   └── pages/                     # 页面示例
│
├── package.json                    # 依赖配置
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

### 组合2：只使用加密（敏感数据）

```typescript
public static readonly USER_LOGIN = new Url(
    'USER_LOGIN', '/api/auth/login', Method.POST,
    true,   // 加密
    false   // 不防重复
);
```

**适用**：登录、查询敏感数据

### 组合3：只使用防重复提交（普通操作）

```typescript
public static readonly FILE_UPLOAD = new Url(
    'FILE_UPLOAD', '/api/file/upload', Method.POST,
    false,  // 不加密
    true    // 防重复
);
```

**适用**：文件上传、数据导出

### 组合4：两个都使用（最高安全）

```typescript
public static readonly PAYMENT_CREATE = new Url(
    'PAYMENT_CREATE', '/api/payment/create', Method.POST,
    true,   // 加密
    true    // 防重复
);
```

**适用**：支付、订单提交、转账

## 💻 使用示例

### 基础用法

```typescript
import { HttpClient } from './framework/http-client';
import { AppUrl } from './app.url';
import { isSuccess, extractData } from './framework/http-client';

const client = new HttpClient({
    baseUrl: 'https://your-gateway.com',
    clientId: 'my-miniprogram'
});

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
```

### 错误处理

```typescript
try {
    await client.request(AppUrl.ORDER_SUBMIT, { body: orderData });
} catch (err: any) {
    if (err.message === 'DUPLICATE_REQUEST') {
        wx.showToast({
            title: '请求正在处理中，请勿重复提交',
            icon: 'none'
        });
    } else if (err.message === 'DUPLICATE_SUBMIT') {
        wx.showToast({
            title: '服务器检测到重复提交',
            icon: 'none'
        });
    } else if (err.status === 423) {
        console.log('密钥已自动更新');
    } else {
        wx.showToast({
            title: '请求失败: ' + err.message,
            icon: 'none'
        });
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

const result: ResultVO<User> = await client.request<User>(AppUrl.USER_LOGIN, {
    body: { username, password }
});

if (isSuccess(result)) {
    const userData = extractData(result);
    console.log(userData.userId);    // ✅ 类型安全
    console.log(userData.username);  // ✅ 类型安全
} else {
    console.error('登录失败:', result.msg);
}
```

## ⚙️ 配置选项

```typescript
const client = new HttpClient({
    baseUrl: 'https://your-gateway.com',          // 网关地址
    clientId: 'my-miniprogram',                    // 客户端ID
    duplicateSubmitTimeWindow: 3000,              // 防重复时间窗口（毫秒）
    showLoading: true,                            // 是否显示加载状态
    maxRetries: 3,                                // 最大重试次数
    retryInterval: 1000,                          // 重试间隔（毫秒）
    timeout: 30000,                               // 请求超时（毫秒）
    enableHeaderAutoManagement: true,             // 是否启用请求头自动管理
    headerStorageKey: 'http_headers'              // storage中存储请求头的键名
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
    
    // 获取缓存的请求头
    getCachedHeaders(): Record<string, string>
    
    // 清除缓存的请求头
    clearCachedHeaders(): void
    
    // 设置缓存的请求头
    setCachedHeaders(headers: Record<string, string>): void
    
    // 移除指定的缓存请求头
    removeCachedHeader(headerName: string): void
}
```

## 🔄 工作流程

### 加密请求流程

```
小程序客户端                          服务器
  │                                    │
  ├─ 1. 密钥交换 ────────────────────→│ EccCryptoFilter
  │  (首次或KeyPair更新时)              │
  │                                    │
  ├─ 2. AES-GCM加密请求体 ─────────────→│ EccCryptoFilter解密
  │  (X-Encrypted-Data)                │
  │                                    │
  │                                    ├─ 3. 业务处理
  │                                    │
  │←─ 4. AES-GCM加密响应 ───────────────┤ EccCryptoFilter加密
  │  (X-Response-Encrypted)            │
  │                                    │
  └─ 5. 解密响应数据                    │
```

## ⚠️ 注意事项

### 1. 小程序环境要求

- 微信小程序基础库版本 >= 2.0.0
- 需要在小程序管理后台配置服务器域名白名单

### 2. 加密算法兼容性

- **ECC曲线**: 使用 `secp256r1` (P-256)，与Java后端完全兼容
- **AES-GCM**: 使用 `node-forge` 库，支持完整的GCM模式

### 3. Storage使用

- 请求头自动保存在 `localStorage`
- 默认键名: `http_headers`
- 用户ID保存在 `userId` 键

### 4. 性能优化

- 密钥对仅在内存中存储，不持久化
- 共享密钥在内存中缓存
- 建议在应用启动时预加载密钥交换

## 🛡️ 安全注意事项

1. **密钥存储** - 密钥对和共享密钥仅存储在内存中，不持久化
2. **Token管理** - 认证Token存储在storage，注意安全防护
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

## 📚 相关文档

- [Web版本客户端](../web/README.md)
- [Node.js版本客户端](../nodejs/README.md)
- [后端加密实现](../../../src/main/java/cn/com/rydeen/gateway/filter/EccCryptoFilter.java)

## 🐛 常见问题

### Q: 密钥交换失败怎么办？

检查：
1. 网关地址是否正确
2. 网关的ECC加密功能是否启用
3. 网络连接是否正常
4. 小程序控制台的详细错误信息

### Q: 收到HTTP 423响应怎么办？

不用担心！这表示服务器KeyPair已过期更新，客户端会**自动重新握手**并重试请求。

### Q: crypto-js 可以用于AES-GCM吗？

不可以。`crypto-js` 不支持 AES-GCM 模式，必须使用 `node-forge` 库。

### Q: @noble/secp256k1 可以用于ECC吗？

不可以。后端使用的是 `secp256r1` (P-256) 曲线，而 `@noble/secp256k1` 使用的是 `secp256k1` 曲线，两者不兼容。必须使用 `@noble/curves` 的 `p256` 模块。

## 📖 版本历史

- **v1.0.0** (2025-01-XX) - 初始版本，支持ECC+AES-GCM加密和防重复提交

---

**版本**: 1.0  
**作者**: richie696  
**更新日期**: 2025-01-XX  
**许可证**: MIT License

