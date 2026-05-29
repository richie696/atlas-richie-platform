# 降级响应配置说明

## 概述

网关支持按 URL 路径配置不同的降级响应消息，配置存放在 Nacos 配置中心，可随时修改自定义内容，无需重启服务。

## 配置方式

### 1. Nacos 配置中心配置

在 Nacos 配置中心找到 `platform-gateway.yaml` 配置文件，添加以下配置：

```yaml
platform:
  gateway:
    fallback:
      # 是否启用降级响应配置（默认：true）
      enabled: true
      
      # 默认降级响应消息（当没有匹配到具体路径时使用）
      default-message: "服务暂不可用，请稍后再试！"
      
      # 按路径配置的降级响应消息列表
      path-messages:
        # 订单服务降级响应
        - path: "/api/order/**"
          message: "订单服务暂时不可用，请稍后再试！"
        
        # 用户服务降级响应
        - path: "/api/user/**"
          message: "用户服务暂时不可用，请稍后再试！"
        
        # 支付服务降级响应
        - path: "/api/payment/**"
          message: "支付服务暂时不可用，请稍后再试！"
        
        # OAuth2.0 接口降级响应
        - path: "/api/oauth2/**"
          message: "认证服务暂时不可用，请稍后再试！"
        
        # 所有 API 接口的通用降级响应（放在最后，作为兜底）
        - path: "/api/**"
          message: "API 服务暂时不可用，请稍后再试！"
```

### 2. 路径匹配规则

- **支持 Ant 路径匹配模式**：
  - `?`：匹配单个字符
  - `*`：匹配零个或多个字符（不包含路径分隔符 `/`）
  - `**`：匹配零个或多个路径段（包含路径分隔符 `/`）

- **匹配顺序**：
  - 按配置顺序从上到下匹配
  - 第一个匹配成功的路径将使用对应的消息
  - 如果所有路径都不匹配，使用默认消息

### 3. 配置示例

#### 示例 1：按服务模块配置

```yaml
platform:
  gateway:
    fallback:
      enabled: true
      default-message: "服务暂不可用，请稍后再试！"
      path-messages:
        - path: "/api/order/**"
          message: "订单服务暂时不可用，请稍后再试！"
        - path: "/api/user/**"
          message: "用户服务暂时不可用，请稍后再试！"
        - path: "/api/product/**"
          message: "商品服务暂时不可用，请稍后再试！"
```

#### 示例 2：按接口类型配置

```yaml
platform:
  gateway:
    fallback:
      enabled: true
      default-message: "服务暂不可用，请稍后再试！"
      path-messages:
        # 内部接口
        - path: "/api/internal/**"
          message: "内部服务暂时不可用，请稍后再试！"
        
        # 第三方接口
        - path: "/api/third-party/**"
          message: "第三方服务暂时不可用，请稍后再试！"
        
        # 公开接口
        - path: "/api/public/**"
          message: "公开服务暂时不可用，请稍后再试！"
```

#### 示例 3：精确路径匹配

```yaml
platform:
  gateway:
    fallback:
      enabled: true
      default-message: "服务暂不可用，请稍后再试！"
      path-messages:
        # 精确匹配登录接口
        - path: "/api/auth/login"
          message: "登录服务暂时不可用，请稍后再试！"
        
        # 精确匹配支付接口
        - path: "/api/payment/pay"
          message: "支付服务暂时不可用，请稍后再试！"
        
        # 通配符匹配其他接口
        - path: "/api/**"
          message: "API 服务暂时不可用，请稍后再试！"
```

## 动态刷新

配置支持 Nacos 动态刷新，修改配置后：

1. 在 Nacos 配置中心修改配置
2. 点击"发布"保存配置
3. 网关服务会自动刷新配置（无需重启）
4. 新的降级响应消息立即生效

## 注意事项

1. **配置顺序很重要**：更具体的路径应该放在更通用的路径之前
   - ✅ 正确：`/api/order/**` 在 `/api/**` 之前
   - ❌ 错误：`/api/**` 在 `/api/order/**` 之前（会导致订单接口匹配到通用消息）

2. **路径模式支持**：
   - 支持 Ant 路径匹配（`?`、`*`、`**`）
   - 不支持正则表达式

3. **默认消息**：
   - 如果 `enabled=false`，始终返回默认消息
   - 如果所有路径都不匹配，返回默认消息

4. **配置验证**：
   - 路径和消息都不能为空
   - 空配置会被忽略，使用默认消息

## 使用场景

1. **服务降级**：当后端服务不可用时，返回友好的降级提示
2. **维护通知**：在服务维护期间，返回维护提示信息
3. **个性化提示**：不同服务模块可以有不同的降级提示文案
4. **多语言支持**：可以为不同语言环境配置不同的降级消息（需要配合国际化组件）

## 技术实现

- **配置类**：`FallbackConfig`（位于 `richie-base` 模块）
- **控制器**：`GlobalFallbackController`（位于 `richie-gateway-service` 模块）
- **路径匹配**：使用 Spring 的 `AntPathMatcher` 进行路径匹配
- **配置刷新**：通过 `@RefreshScope` 注解支持 Nacos 动态刷新
