# Atlas Richie React Example (atlas-richie-gateway-react-example)

React 18+ 完整使用示例，展示如何使用统一HTTP客户端。

## 📁 文件结构

```
react/
├── app.url.ts              # URL配置
├── hooks/
│   ├── useHttpClient.ts    # HTTP客户端Hook
│   └── useAuth.ts          # 认证Hook
└── components/
    ├── LoginForm.tsx       # 登录表单
    └── OrderSubmit.tsx     # 订单提交
```

## 🚀 快速开始

### 1. 配置环境变量

```bash
# .env
REACT_APP_GATEWAY_URL=https://your-gateway.com
```

### 2. 使用Hook

```tsx
import { useHttpClient } from './hooks/useHttpClient';
import { AppUrl } from './app.url';

function MyComponent() {
    const client = useHttpClient();

    const fetchData = async () => {
        const result = await client.request(AppUrl.USER_PROFILE);
        console.log(result);
    };

    return <button onClick={fetchData}>获取数据</button>;
}
```

### 3. 使用认证Hook

```tsx
import { useAuth } from './hooks/useAuth';

function LoginPage() {
    const { login, loading, error } = useAuth();

    const handleLogin = async () => {
        await login('username', 'password');
    };

    return (
        <div>
            <button onClick={handleLogin} disabled={loading}>
                {loading ? '登录中...' : '登录'}
            </button>
            {error && <div>{error}</div>}
        </div>
    );
}
```

## 💡 核心特性

### 自动资源管理

`useHttpClient` Hook自动管理客户端生命周期：

```tsx
const client = useHttpClient();
// 组件卸载时自动调用 client.cleanup()
```

### 加密和防重复提交

根据URL配置自动处理：

```tsx
// app.url.ts
public static readonly ORDER_SUBMIT = new Url(
    'ORDER_SUBMIT',
    '/api/order/submit',
    Method.POST,
    true,  // ✅ 自动加密
    true   // ✅ 自动防重复提交
);

// 使用
await client.request(AppUrl.ORDER_SUBMIT, {
    body: orderData
});
```

### 错误处理

```tsx
try {
    await client.request(AppUrl.ORDER_SUBMIT, { body: orderData });
} catch (err: any) {
    if (err.message === 'DUPLICATE_REQUEST') {
        // 客户端检测到重复请求
    } else if (err.message === 'DUPLICATE_SUBMIT') {
        // 服务器检测到重复提交
    } else {
        // 其他错误
    }
}
```

## 📋 组件示例

### 登录表单

参见 `components/LoginForm.tsx`

### 订单提交

参见 `components/OrderSubmit.tsx`

## 🎯 最佳实践

1. **使用Hook** - 优先使用 `useHttpClient` 而不是直接创建客户端
2. **配置URL** - 所有URL在 `app.url.ts` 中集中管理
3. **错误处理** - 总是处理 `DUPLICATE_REQUEST` 和 `DUPLICATE_SUBMIT` 错误
4. **TypeScript** - 使用泛型指定响应类型

## 🔗 相关文档

- [快速入门](../../快速入门.md)
- [React使用指南](../../React使用指南.md)

