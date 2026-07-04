# Atlas Richie Vue 3 Example (atlas-richie-gateway-vue-example)

Vue 3 完整使用示例，使用Composition API和`<script setup>`语法。

## 📁 文件结构

```
vue/
├── app.url.ts                      # URL配置
├── composables/
│   ├── useHttpClient.ts            # HTTP客户端Composable
│   └── useAuth.ts                  # 认证Composable
└── components/
    ├── LoginForm.vue               # 登录表单组件
    └── OrderSubmit.vue             # 订单提交组件
```

## 🚀 快速开始

### 1. 配置环境变量

```bash
# .env
VITE_GATEWAY_URL=https://your-gateway.com
```

### 2. 使用Composable

```vue
<script setup lang="ts">
import { useHttpClient } from './composables/useHttpClient';
import { AppUrl } from './app.url';

const client = useHttpClient();

const fetchData = async () => {
    const result = await client.request(AppUrl.USER_PROFILE);
    console.log(result);
};
</script>

<template>
    <button @click="fetchData">获取数据</button>
</template>
```

### 3. 使用认证Composable

```vue
<script setup lang="ts">
import { useAuth } from './composables/useAuth';

const { login, loading, error, isLoggedIn } = useAuth();

const handleLogin = async () => {
    await login('username', 'password');
};
</script>

<template>
    <div>
        <button @click="handleLogin" :disabled="loading">
            {{ loading ? '登录中...' : '登录' }}
        </button>
        <div v-if="error">{{ error }}</div>
    </div>
</template>
```

## 💡 核心特性

### Composition API

使用Vue 3的Composition API和`<script setup>`：

```vue
<script setup lang="ts">
import { ref } from 'vue';
import { useHttpClient } from './composables/useHttpClient';

const client = useHttpClient();
const data = ref(null);

const fetchData = async () => {
    data.value = await client.request(AppUrl.MENU_ALL);
};
</script>
```

### 自动资源管理

Composable自动管理客户端生命周期：

```typescript
export function useHttpClient() {
    const client = new HttpClient({...});
    
    // 组件卸载时自动清理
    onUnmounted(() => {
        client.cleanup();
    });
    
    return client;
}
```

### 响应式状态

使用ref和computed实现响应式状态：

```typescript
export function useAuth() {
    const client = useHttpClient();
    const loading = ref(false);
    const error = ref<string | null>(null);
    const user = ref<User | null>(null);
    
    // 检查是否已登录（通过检查缓存的请求头中是否有token）
    const isLoggedIn = computed(() => {
        const cachedHeaders = client.getCachedHeaders();
        // 检查是否有x-rd-request-apitoken（网关签发的token）
        return !!cachedHeaders['x-rd-request-apitoken'] || !!cachedHeaders['X-Rd-Request-Apitoken'];
    });
    
    return { loading, error, user, isLoggedIn };
}
```

### 加密和防重复提交

根据URL配置自动处理：

```typescript
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

## 📋 组件示例

### 登录表单组件

参见 `components/LoginForm.vue`

特点：
- `<script setup>`语法
- 双向绑定 (v-model)
- 响应式状态
- 自动错误处理

### 订单提交组件

参见 `components/OrderSubmit.vue`

特点：
- Props定义
- 自动加密和防重复提交
- 完整的错误处理
- Scoped样式

## 🎯 最佳实践

1. **使用Composables** - 将业务逻辑封装在Composable中
2. **`<script setup>`** - 使用简洁的setup语法
3. **TypeScript** - 充分利用TypeScript类型系统
4. **配置URL** - 所有URL在app.url.ts中集中管理
5. **错误处理** - 总是处理DUPLICATE_REQUEST和DUPLICATE_SUBMIT错误

## 🔌 插件集成

### Pinia状态管理

```typescript
// stores/auth.ts
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { useHttpClient } from '../composables/useHttpClient';
import { AppUrl } from '../app.url';
import { ResultVO, isSuccess, extractData } from '../../../framework/http-client';

interface User {
    userId: string;
    username: string;
    email: string;
}

export const useAuthStore = defineStore('auth', () => {
    const client = useHttpClient();
    const user = ref<User | null>(null);

    async function login(username: string, password: string) {
        const result: ResultVO<User> = await client.request<User>(AppUrl.USER_LOGIN, {
            body: { username, password }
        });
        
        if (isSuccess(result)) {
            const userData = extractData(result);
            user.value = userData;
            // 注意：token现在从响应头自动获取并保存到localStorage，无需手动处理
        } else {
            throw new Error(result.msg || '登录失败');
        }
    }

    return { user, login };
});
```

### Vue Router导航守卫

```typescript
// router/index.ts
import { createRouter } from 'vue-router';
import { AppUrl } from '../app.url';

const router = createRouter({
    routes: [
        {
            path: AppUrl.PAGE_HOME.path,
            component: () => import('../views/Home.vue')
        },
        {
            path: AppUrl.PAGE_LOGIN.path,
            component: () => import('../views/Login.vue')
        }
    ]
});

router.beforeEach((to, from) => {
    // 检查是否已登录（通过检查缓存的请求头中是否有token）
    const client = useHttpClient();
    const cachedHeaders = client.getCachedHeaders();
    const hasToken = !!cachedHeaders['x-rd-request-apitoken'] || !!cachedHeaders['X-Rd-Request-Apitoken'];
    
    if (!hasToken && to.path !== AppUrl.PAGE_LOGIN.path) {
        return AppUrl.PAGE_LOGIN.path;
    }
});

export default router;
```

## 🔗 相关文档

- [快速入门](../../快速入门.md)
- [集成说明](../../集成说明.md)

