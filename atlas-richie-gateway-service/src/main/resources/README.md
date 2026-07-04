# Atlas Richie Gateway Service Resources (atlas-richie-gateway-service-resources)

## 📁 目录结构

```
resources/
├── client-ts-library/           # TypeScript客户端（Web应用）✅ 完整
│   ├── framework/              # 框架核心代码
│   ├── examples/               # React/Angular/Vue示例
│   └── README.md
│
├── client-library/              # 其他语言客户端
│   ├── go/                     # Go客户端 ✅ 可用
│   ├── swift/                  # Swift客户端 🚧 框架
│   ├── kotlin/                 # Kotlin客户端 🚧 框架
│   ├── rust/                   # Rust客户端 🚧 框架
│   └── README.md
│
└── application*.yml             # 配置文件
```

## 🚀 快速导航

### 我是前端开发者

**→ 使用TypeScript客户端** ⭐

```bash
cd client-library/client-ts-library
```

- [x] 完整的HTTP客户端实现
- [x] React 18+ 完整示例
- [x] Angular 17+ 完整示例
- [x] Vue 3 完整示例
- [x] 详细的文档

**查看**: [client-ts-library/README.md](./client-library/client-ts-library/README.md)

---

### 我是后端开发者

**→ 使用Go客户端** ⭐

```bash
cd client-library/go
```

- [x] 标准库实现（零依赖）
- [x] 跨平台编译
- [x] 高性能
- [x] 单文件部署

**查看**: [go/README.md](./client-library/go/README.md)

---

### 我是移动开发者

**iOS → Swift客户端** 🚧

```bash
cd client-library/swift
```

- [x] CryptoKit加密库
- [x] SwiftUI示例
- [ ] ECC加密完善中

**Android → Kotlin客户端** 🚧

```bash
cd client-library/kotlin
```

- [x] javax.crypto加密
- [x] Activity示例
- [ ] ECC加密完善中

---

## 📚 文档中心

### 客户端文档

| 文档 | 说明 | 优先级 |
|------|------|--------|
| [客户端总览](./client-library/README.md) | 所有语言客户端汇总 | ⭐⭐⭐⭐⭐ |
| [客户端选择指南](./client-library/客户端选择指南.md) | 根据场景快速选择 | ⭐⭐⭐⭐⭐ |
| [TypeScript文档](./client-library/client-ts-library/README.md) | 最完整的文档 | ⭐⭐⭐⭐⭐ |
| [Go文档](./client-library/go/README.md) | Go客户端说明 | ⭐⭐⭐⭐ |

### 服务端文档

| 文档 | 说明 | 优先级 |
|------|------|--------|
| [网关设计文档](../../文档/网关设计文档.md) | 服务端架构和配置 | ⭐⭐⭐⭐⭐ |
| [兼容性说明](../../文档/服务端客户端兼容性.md) | 客户端服务端兼容性 | ⭐⭐⭐⭐ |

---

## 🎯 推荐阅读路径

### 新手路径（20分钟）

1. **[客户端选择指南](./client-library/客户端选择指南.md)** - 5分钟
   - 了解有哪些客户端
   - 根据场景选择合适的语言

2. **选择你的客户端目录** - 5分钟
   - TypeScript: `client-ts-library/`
   - Go: `go/`
   - 其他: `swift/`, `kotlin/`, `rust/`

3. **查看示例代码** - 10分钟
   - 复制到你的项目
   - 开始使用

### 深入路径（1小时）

1. **[客户端总览](./client-library/README.md)** - 10分钟
2. **[TypeScript完整文档](./client-library/client-ts-library/README.md)** - 30分钟
3. **[网关设计文档](../../文档/网关设计文档.md)** - 20分钟

---

## 🌟 核心特性

所有客户端都支持：

1. **🔐 ECC+AES-GCM加密**
   - 端到端加密
   - 自动密钥交换
   - KeyPair自动更新

2. **🛡️ 防重复提交**
   - 客户端本地队列检查
   - 服务器Redis缓存检查
   - 双重防护

3. **🎯 灵活配置**
   - 每个API独立配置
   - 四种组合：不加密不防重复、只加密、只防重复、都使用

4. **⚡ 自动处理**
   - 自动密钥交换
   - 自动重新握手
   - 自动错误处理

---

**版本**: 1.0  
**更新**: 2025-11-01  
**作者**: richie696

