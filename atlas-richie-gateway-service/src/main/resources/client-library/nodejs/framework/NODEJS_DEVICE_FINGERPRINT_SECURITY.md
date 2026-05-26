# Node.js 设备指纹安全方案使用指南

## 一、概述

Node.js 服务器端应用具有比浏览器端更强的安全能力，可以访问系统级硬件信息。本方案提供了**高安全性的设备识别方案**，适用于 Node.js 服务器端应用。

## 二、安全优势

### 2.1 相比浏览器端的优势

| 特性 | 浏览器端 | Node.js 端 |
|------|---------|-----------|
| **机器ID** | ❌ 不可用 | ✅ 基于硬件特征，稳定可靠 |
| **CPU信息** | ⚠️ 有限（硬件并发数） | ✅ 完整（型号、核心数） |
| **内存信息** | ⚠️ 有限（deviceMemory） | ✅ 完整（总内存、可用内存） |
| **MAC地址** | ❌ 不可用 | ✅ 可访问所有网络接口 |
| **文件系统** | ❌ 不可用 | ✅ 可持久化存储（加密） |
| **系统信息** | ⚠️ 有限 | ✅ 完整（平台、架构、版本） |

### 2.2 安全策略

1. **机器ID（基础标识）**：
   - 优先使用 `node-machine-id`（基于硬件特征）
   - 备用方案：基于硬件特征生成（CPU、内存、MAC地址等）
   - 稳定性高，硬件不变时ID不变

2. **硬件指纹（动态验证）**：
   - 每次请求时动态生成硬件指纹
   - 基于多种硬件特征（CPU、内存、MAC地址、主机名等）
   - 与存储的指纹匹配验证

3. **加密存储（可选）**：
   - 设备指纹可以加密存储到文件系统
   - 使用 AES-256-GCM 加密
   - 避免明文存储敏感信息

4. **双重验证**：
   - 存储的设备ID（稳定性）
   - 动态硬件指纹（安全性）
   - 两者都通过才能使用 Token

## 三、实现方案

### 3.1 基础使用

```typescript
import { 
    getOrCreateDeviceFingerprint, 
    generateHardwareFingerprint,
    fingerprintToString,
    getDeviceName 
} from './device-fingerprint';

// 1. 获取或创建设备指纹（首次运行时会生成并保存）
const fingerprint = await getOrCreateDeviceFingerprint({
    storagePath: '/path/to/storage',  // 可选：自定义存储路径
    storageFileName: '.device_fingerprint',  // 可选：自定义文件名
    encryptionKey: 'your-encryption-key'  // 可选：加密密钥（生产环境建议使用）
});

console.log('机器ID:', fingerprint.machineId);
console.log('CPU信息:', fingerprint.cpu);
console.log('MAC地址:', fingerprint.macAddress);

// 2. 获取设备名称（用于显示）
const deviceName = getDeviceName();
console.log('设备名称:', deviceName); // "Node.js Server on Linux PC (hostname)"
```

### 3.2 登录时使用

```typescript
import { 
    getOrCreateDeviceFingerprint, 
    generateHardwareFingerprint,
    fingerprintToString 
} from './device-fingerprint';

async function login(username: string, password: string, mfaCode?: string) {
    // 1. 获取存储的设备指纹（用于稳定性）
    const storedFingerprint = await getOrCreateDeviceFingerprint();
    
    // 2. 生成动态硬件指纹（用于安全性验证）
    const dynamicFingerprint = await generateHardwareFingerprint();
    
    // 3. 构建登录请求
    const loginRequest = {
        username,
        password,
        mfaCode,
        deviceId: storedFingerprint.machineId,  // 设备ID（用于稳定性）
        hardwareFingerprint: fingerprintToString(dynamicFingerprint)  // 硬件指纹（用于安全性）
    };
    
    // 4. 发送登录请求
    const response = await fetch('https://your-gateway.com/api/login', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Device-Id': storedFingerprint.machineId,
            'X-Hardware-Fingerprint': fingerprintToString(dynamicFingerprint)
        },
        body: JSON.stringify(loginRequest)
    });
    
    return await response.json();
}
```

### 3.3 后续请求时使用

```typescript
import { generateHardwareFingerprint, fingerprintToString } from './device-fingerprint';

// 在 HTTP 拦截器中自动添加硬件指纹
axios.interceptors.request.use(async (config) => {
    // 每次请求时重新生成硬件指纹（动态生成，不依赖存储）
    const fingerprint = await generateHardwareFingerprint();
    const fingerprintJson = fingerprintToString(fingerprint);
    
    config.headers['X-Hardware-Fingerprint'] = fingerprintJson;
    return config;
});
```

### 3.4 集成到 HTTP 客户端

```typescript
import { HttpClient } from './http-client';
import { 
    getOrCreateDeviceFingerprint, 
    generateHardwareFingerprint,
    fingerprintToString 
} from './device-fingerprint';

class SecureHttpClient extends HttpClient {
    private deviceFingerprint: HardwareFingerprint | null = null;
    
    constructor(config: HttpClientConfig) {
        super(config);
        this.initDeviceFingerprint();
    }
    
    private async initDeviceFingerprint() {
        // 初始化时获取设备指纹
        this.deviceFingerprint = await getOrCreateDeviceFingerprint({
            encryptionKey: process.env.DEVICE_FINGERPRINT_KEY  // 从环境变量读取加密密钥
        });
    }
    
    async request<T>(url: Url, options: RequestOptions = {}): Promise<ResultVO<T>> {
        // 每次请求时生成动态硬件指纹
        const dynamicFingerprint = await generateHardwareFingerprint();
        const fingerprintJson = fingerprintToString(dynamicFingerprint);
        
        // 自动添加到请求头
        const headers = {
            'X-Device-Id': this.deviceFingerprint?.machineId || '',
            'X-Hardware-Fingerprint': fingerprintJson,
            ...options.headers
        };
        
        return super.request(url, { ...options, headers });
    }
}
```

## 四、安全配置

### 4.1 加密存储（推荐）

生产环境建议使用加密存储，避免设备指纹明文存储：

```typescript
const fingerprint = await getOrCreateDeviceFingerprint({
    storagePath: '/var/lib/rydeen',  // 使用系统目录
    storageFileName: '.device_fingerprint',
    encryptionKey: process.env.DEVICE_FINGERPRINT_KEY  // 从环境变量读取
});
```

**加密密钥管理**：
- 使用环境变量存储加密密钥
- 密钥长度建议 >= 32 字符
- 定期轮换密钥（需要重新生成设备指纹）

### 4.2 文件权限

设备指纹文件会自动设置权限 `0o600`（仅所有者可读写），确保安全性：

```typescript
// 文件权限：rw------- (仅所有者可读写)
fs.writeFileSync(filePath, content, { mode: 0o600 });
```

### 4.3 存储位置

默认存储位置：`~/.rydeen/.device_fingerprint`

**推荐配置**：
- **开发环境**：使用默认位置（用户目录）
- **生产环境**：使用系统目录（如 `/var/lib/rydeen/.device_fingerprint`）

## 五、硬件指纹特征说明

### 5.1 包含的特征

| 特征 | 说明 | 稳定性 | 权重 |
|------|------|--------|------|
| **机器ID** | 基于硬件特征生成 | ⭐⭐⭐⭐⭐ | 0.4 |
| **CPU信息** | CPU型号 + 核心数 | ⭐⭐⭐⭐⭐ | 0.2 |
| **总内存** | 系统总内存（允许1%误差） | ⭐⭐⭐⭐ | 0.1 |
| **MAC地址** | 主要网络接口MAC地址 | ⭐⭐⭐⭐ | 0.15 |
| **主机名** | 系统主机名 | ⭐⭐⭐ | 0.05 |
| **平台/架构** | 操作系统类型和架构 | ⭐⭐⭐⭐⭐ | 0.05 |
| **网络接口** | 所有网络接口MAC地址 | ⭐⭐⭐ | 0.05 |

### 5.2 相似度计算

硬件指纹验证使用相似度比较，允许硬件特征有轻微变化：

- **相似度 >= 0.8**：认为匹配（允许通过）
- **相似度 < 0.8**：认为不匹配（拒绝请求）

**容错场景**：
- 网络接口变化（新增/删除接口）
- 内存小幅变化（如虚拟内存调整）
- 主机名变更（如果允许）

## 六、依赖说明

### 6.1 必需依赖

**零外部依赖**：使用 Node.js 内置模块：
- `crypto`：加密和哈希计算
- `os`：系统信息获取
- `fs`：文件系统操作
- `path`：路径处理

### 6.2 可选依赖

**node-machine-id**（推荐安装）：
```bash
npm install node-machine-id
```

**优势**：
- 提供更稳定的机器ID（基于硬件特征）
- 跨平台支持（Windows、macOS、Linux）
- 硬件不变时ID不变

**如果不安装**：
- 使用备用方案（基于硬件特征生成）
- 功能不受影响，但机器ID可能不够稳定

## 七、安全优势总结

### 7.1 相比浏览器端

✅ **更强的硬件访问能力**：
- 可以访问 MAC 地址（浏览器端不可用）
- 可以访问完整的 CPU 和内存信息
- 可以使用机器ID（基于硬件特征）

✅ **更稳定的设备识别**：
- 机器ID基于硬件特征，硬件不变时ID不变
- 文件系统持久化，避免每次重启变化
- 不依赖浏览器环境，不受浏览器更新影响

✅ **更高的安全性**：
- 可以加密存储设备指纹
- 文件权限控制（仅所有者可读写）
- 双重验证（设备ID + 硬件指纹）

### 7.2 攻击难度

即使攻击者盗取了 Token 和 deviceId：
- ❌ **无法伪造硬件指纹**：MAC地址、CPU信息等硬件特征无法伪造
- ❌ **无法访问文件系统**：设备指纹文件加密存储，权限受限
- ❌ **无法模拟系统环境**：即使知道部分信息，也无法完全匹配硬件指纹

## 八、最佳实践

### 8.1 生产环境配置

```typescript
// 1. 使用环境变量管理加密密钥
const encryptionKey = process.env.DEVICE_FINGERPRINT_KEY || '';

// 2. 使用系统目录存储
const storagePath = process.env.DEVICE_FINGERPRINT_PATH || '/var/lib/rydeen';

// 3. 初始化设备指纹
const fingerprint = await getOrCreateDeviceFingerprint({
    storagePath,
    storageFileName: '.device_fingerprint',
    encryptionKey
});
```

### 8.2 错误处理

```typescript
try {
    const fingerprint = await getOrCreateDeviceFingerprint();
    // 使用设备指纹
} catch (error) {
    console.error('获取设备指纹失败:', error);
    // 降级处理：使用临时指纹或提示用户
    // 注意：这种情况下，设备信任功能可能不可用
}
```

### 8.3 定期验证

```typescript
// 定期验证设备指纹是否仍然有效
async function validateDeviceFingerprint() {
    const stored = await getOrCreateDeviceFingerprint();
    const current = await generateHardwareFingerprint();
    
    const similarity = calculateFingerprintSimilarity(stored, current);
    
    if (similarity < 0.8) {
        console.warn('设备指纹不匹配，可能需要重新认证');
        // 可以触发重新登录流程
    }
}
```

## 九、常见问题

### Q1: 机器ID会变化吗？

**A**: 
- 如果使用 `node-machine-id`：硬件不变时ID不变（最稳定）
- 如果使用备用方案：硬件不变时ID不变，但可能受系统配置影响
- 硬件变更（如更换主板）时，机器ID会改变（预期行为）

### Q2: 如果硬件发生变化怎么办？

**A**: 
- 硬件指纹相似度会降低
- 如果相似度 < 0.8，请求会被拒绝
- 用户需要重新登录，重新生成设备指纹

### Q3: 可以禁用硬件指纹验证吗？

**A**: 
- 不建议禁用，这会降低安全性
- 如果确实需要，可以修改后端验证逻辑
- 但建议保留 deviceId 验证作为基础保护

### Q4: 加密密钥丢失怎么办？

**A**: 
- 如果加密密钥丢失，无法解密已存储的设备指纹
- 需要清除设备指纹文件，重新生成
- 建议使用密钥管理系统（如 Vault、KMS）管理加密密钥

## 十、总结

Node.js 端的设备指纹方案具有**更高的安全性和稳定性**：

1. ✅ **机器ID**：基于硬件特征，稳定可靠
2. ✅ **硬件指纹**：动态生成，难以伪造
3. ✅ **加密存储**：可选加密，保护敏感信息
4. ✅ **双重验证**：设备ID + 硬件指纹，提高安全性

相比浏览器端，Node.js 端可以访问更多系统级信息，提供更强的安全保护。
