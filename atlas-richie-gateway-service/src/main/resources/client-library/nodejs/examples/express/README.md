# Atlas Richie Express.js Integration Example (atlas-richie-gateway-express-example)

展示如何在Express.js应用中使用HTTP客户端库。

## 📦 安装依赖

```bash
npm install express
npm install --save-dev @types/express @types/node typescript ts-node
```

## 🚀 使用方法

### 1. 运行示例

```bash
# 使用ts-node直接运行
npx ts-node examples/express/app.ts

# 或编译后运行
tsc examples/express/app.ts
node examples/express/app.js
```

### 2. 设置环境变量（可选）

```bash
export GATEWAY_URL=https://your-gateway.com
export CLIENT_ID=express-app
export PORT=3000
```

### 3. 测试API

```bash
# 登录
curl -X POST http://localhost:3000/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"pass"}'

# 提交订单
curl -X POST http://localhost:3000/api/orders \
  -H "Content-Type: application/json" \
  -d '{"items":[{"productId":"P001","quantity":2}],"totalAmount":199.98}'

# 获取菜单
curl http://localhost:3000/api/menu

# 获取用户信息
curl http://localhost:3000/api/user/profile \
  -H "Authorization: Bearer your-token"
```

## 📚 集成方式

### 方式1: 服务类封装

```typescript
export class ApiService {
    constructor(private client: HttpClient) {}
    
    async login(username: string, password: string) {
        return await this.client.request(AppUrl.USER_LOGIN, {
            body: { username, password }
        });
    }
}
```

### 方式2: Express中间件

```typescript
export function httpClientMiddleware(req: Request, res: Response, next: NextFunction) {
    (req as any).httpClient = httpClient;
    next();
}

app.post('/api/endpoint', httpClientMiddleware, async (req, res) => {
    const client = (req as any).httpClient;
    const result = await client.request(AppUrl.USER_LOGIN, {...});
});
```

### 方式3: 单例模式

```typescript
// 在应用启动时创建客户端
const httpClient = new HttpClient({...});

// 在路由中使用
app.post('/api/endpoint', async (req, res) => {
    const result = await httpClient.request(AppUrl.USER_LOGIN, {...});
});
```

## ⚠️ 注意事项

1. **单例模式**: 建议在应用启动时创建一个HTTP客户端实例，在多个请求间共享
2. **Token管理**: token从响应头自动获取并保存到内存存储
3. **错误处理**: 正确处理 `DUPLICATE_REQUEST` 和 `DUPLICATE_SUBMIT` 错误
4. **资源清理**: 应用关闭时调用 `client.cleanup()` 清理资源
5. **响应结构**: 所有请求返回 `ResultVO<T>`，使用 `isSuccess()` 和 `extractData()` 处理响应

