# Atlas Richie Web Framework Examples (atlas-richie-gateway-web-examples)

提供React、Angular和Vue三大主流框架的完整使用示例。

## 📁 目录结构

```
examples/
├── react/              # React 18+ 示例
│   ├── app.url.ts
│   ├── hooks/
│   ├── components/
│   └── README.md
│
├── angular/            # Angular 17+ 示例
│   ├── app.url.ts
│   ├── services/
│   ├── components/
│   └── README.md
│
└── vue/                # Vue 3 示例
    ├── app.url.ts
    ├── composables/
    ├── components/
    └── README.md
```

## 🚀 选择你的框架

### React 18+

**特点**：
- ✅ React Hooks
- ✅ 函数式组件
- ✅ TypeScript类型支持
- ✅ 自动资源管理

**查看**: [react/README.md](./react/README.md)

**快速开始**：
```tsx
import { useHttpClient } from './hooks/useHttpClient';
import { AppUrl } from './app.url';

function MyComponent() {
    const client = useHttpClient();
    
    const handleClick = async () => {
        const result = await client.request(AppUrl.USER_LOGIN, {
            body: { username: 'user', password: 'pass' }
        });
    };
}
```

---

### Angular 17+

**特点**：
- ✅ Standalone Components
- ✅ Signals响应式
- ✅ Dependency Injection
- ✅ RxJS集成

**查看**: [angular/README.md](./angular/README.md)

**快速开始**：
```typescript
import { Component, inject } from '@angular/core';
import { HttpClientService } from './services/http-client.service';
import { AppUrl } from './app.url';

@Component({
    selector: 'app-my-component',
    standalone: true
})
export class MyComponent {
    private httpClient = inject(HttpClientService);
    
    async handleClick() {
        const result = await this.httpClient.request(AppUrl.USER_LOGIN, {
            body: { username: 'user', password: 'pass' }
        });
    }
}
```

---

### Vue 3

**特点**：
- ✅ Composition API
- ✅ `<script setup>`语法
- ✅ Composables模式
- ✅ 响应式系统

**查看**: [vue/README.md](./vue/README.md)

**快速开始**：
```vue
<script setup lang="ts">
import { useHttpClient } from './composables/useHttpClient';
import { AppUrl } from './app.url';

const client = useHttpClient();

const handleClick = async () => {
    const result = await client.request(AppUrl.USER_LOGIN, {
        body: { username: 'user', password: 'pass' }
    });
};
</script>
```

## 📋 功能对比

| 功能 | React | Angular | Vue |
|------|-------|---------|-----|
| 加密支持 | ✅ | ✅ | ✅ |
| 防重复提交 | ✅ | ✅ | ✅ |
| TypeScript | ✅ | ✅ | ✅ |
| 自动资源清理 | ✅ | ✅ | ✅ |
| 状态管理 | useState | Signals | ref/computed |
| 依赖注入 | Context | DI | provide/inject |
| 路由集成 | React Router | Angular Router | Vue Router |

## 🎯 共同特点

所有三个框架示例都提供：

### 1. URL配置

每个示例都包含 `app.url.ts`，定义所有API端点：

```typescript
export class AppUrl {
    public static readonly USER_LOGIN = new Url(
        'USER_LOGIN',
        '/api/auth/login',
        Method.POST,
        true,   // 需要加密
        false   // 不需要防重复提交
    );
}
```

### 2. 自动加密和防重复提交

根据URL配置自动处理：

```typescript
// 这个请求会自动加密和防重复提交
await client.request(AppUrl.ORDER_SUBMIT, {
    body: orderData
});
```

### 3. 完整的错误处理

```typescript
try {
    await client.request(AppUrl.ORDER_SUBMIT, { body: orderData });
} catch (err: any) {
    if (err.message === 'DUPLICATE_REQUEST') {
        // 客户端检测到重复请求
    } else if (err.message === 'DUPLICATE_SUBMIT') {
        // 服务器检测到重复提交
    }
}
```

### 4. 组件示例

每个框架都包含：
- ✅ 登录表单组件
- ✅ 订单提交组件
- ✅ 完整的样式

## 📦 使用步骤

### 1. 选择你的框架目录

```bash
cd examples/react     # 或 angular、vue
```

### 2. 复制代码到你的项目

```bash
# 复制framework目录
cp -r ../../framework your-project/src/

# 复制示例代码
cp -r . your-project/src/
```

### 3. 配置环境变量

**React**:
```bash
# .env
REACT_APP_GATEWAY_URL=https://your-gateway.com
```

**Angular**:
```typescript
// environments/environment.ts
export const environment = {
    gatewayUrl: 'https://your-gateway.com'
};
```

**Vue**:
```bash
# .env
VITE_GATEWAY_URL=https://your-gateway.com
```

### 4. 开始使用

查看各框架目录下的README.md了解详细使用方法。

## 🔗 相关文档

- [快速入门](../快速入门.md) - 5分钟快速上手
- [枚举系统改进说明](../枚举系统改进说明.md) - 详细的设计说明
- [React使用指南](../React使用指南.md) - React专属指南
- [集成说明](../集成说明.md) - 整体集成说明

## 💡 推荐阅读顺序

1. **先看** [快速入门](../快速入门.md) 了解基本概念
2. **选择**你使用的框架目录
3. **阅读**该框架的README.md
4. **复制**示例代码到你的项目
5. **运行**并测试

## ❓ 常见问题

### Q: 可以混用多个框架吗？

A: 可以！每个框架示例都是独立的，可以在同一个项目中使用多个框架（微前端场景）。

### Q: 如何自定义配置？

A: 修改各框架的 `app.url.ts` 文件，添加你自己的API端点配置。

### Q: 支持SSR吗？

A: 支持！但需要确保 `window.crypto` API在SSR环境中可用（通常需要polyfill）。

### Q: 如何调试？

A: 
- 查看浏览器控制台的 `[ECC]` 和 `[防重复提交]` 日志
- 使用 `url.toDebugString()` 查看URL配置
- 检查Network面板的请求头

## 🎉 开始使用

选择你的框架，开始构建安全的应用！

- [React示例](./react/)
- [Angular示例](./angular/)
- [Vue示例](./vue/)

