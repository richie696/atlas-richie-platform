# Atlas Richie Angular Example (atlas-richie-gateway-angular-example)

Angular 17+ 完整使用示例，使用最新的Standalone Components和Signals。

## 📁 文件结构

```
angular/
├── app.url.ts                      # URL配置
├── environments/
│   └── environment.ts              # 环境配置
├── services/
│   ├── http-client.service.ts      # HTTP客户端服务
│   └── auth.service.ts             # 认证服务
└── components/
    ├── login-form.component.ts     # 登录表单组件
    └── order-submit.component.ts   # 订单提交组件
```

## 🚀 快速开始

### 1. 配置环境变量

```typescript
// environments/environment.ts
export const environment = {
    production: false,
    gatewayUrl: 'https://your-gateway.com'
};
```

### 2. 使用Service

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

    async fetchData() {
        const result = await this.httpClient.request(AppUrl.USER_PROFILE);
        console.log(result);
    }
}
```

### 3. 使用认证服务

```typescript
import { Component, inject } from '@angular/core';
import { AuthService } from './services/auth.service';

@Component({
    selector: 'app-login',
    standalone: true
})
export class LoginComponent {
    authService = inject(AuthService);

    async login() {
        await this.authService.login('username', 'password');
    }
}
```

## 💡 核心特性

### Standalone Components

所有组件都使用Angular 17+的Standalone模式：

```typescript
@Component({
    selector: 'app-login-form',
    standalone: true,
    imports: [CommonModule, FormsModule]
})
export class LoginFormComponent {
    // ...
}
```

### Signals响应式状态

使用Angular Signals实现响应式状态管理：

```typescript
@Injectable({
    providedIn: 'root'
})
export class AuthService {
    loading = signal(false);
    error = signal<string | null>(null);
    user = signal<User | null>(null);
}
```

### 自动资源清理

使用DestroyRef自动清理资源：

```typescript
@Injectable({
    providedIn: 'root'
})
export class HttpClientService {
    private destroyRef = inject(DestroyRef);

    constructor() {
        this.client = new HttpClient({...});
        
        // 自动清理
        this.destroyRef.onDestroy(() => {
            this.client.cleanup();
        });
    }
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
await this.httpClient.request(AppUrl.ORDER_SUBMIT, {
    body: orderData
});
```

## 📋 组件示例

### 登录表单组件

参见 `components/login-form.component.ts`

特点：
- Standalone Component
- 双向绑定
- 响应式状态
- 自动错误处理

### 订单提交组件

参见 `components/order-submit.component.ts`

特点：
- Signal Inputs
- 自动加密和防重复提交
- 完整的错误处理

## 🎯 最佳实践

1. **使用inject()** - 优先使用inject()函数注入依赖
2. **Standalone Components** - 使用standalone模式简化模块管理
3. **Signals** - 使用Signals实现响应式状态
4. **配置URL** - 所有URL在app.url.ts中集中管理
5. **错误处理** - 总是处理DUPLICATE_REQUEST和DUPLICATE_SUBMIT错误

## 🔗 相关文档

- [快速入门](../../快速入门.md)
- [集成说明](../../集成说明.md)

