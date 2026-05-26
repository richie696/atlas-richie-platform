# 小程序设备指纹安全方案使用指南

## 一、概述

小程序环境相比浏览器端和原生应用，设备识别能力有限。本方案提供了**适用于小程序的高安全性设备识别方案**，基于系统信息和本地存储实现。

## 二、安全策略

### 2.1 小程序环境限制

| 特性 | 小程序 | 浏览器端 | 原生应用 |
|------|--------|---------|---------|
| **系统信息** | ✅ 可用（有限） | ✅ 可用 | ✅ 完整 |
| **MAC地址** | ❌ 不可用 | ❌ 不可用 | ✅ 可用 |
| **设备ID** | ⚠️ 有限（系统信息组合） | ⚠️ 有限 | ✅ 完整（Android ID、IDFV） |
| **本地存储** | ✅ 可用（wx.storage） | ✅ 可用（localStorage） | ✅ 可用（Keychain/SharedPreferences） |

### 2.2 安全策略

1. **系统信息组合**：
   - 设备品牌、型号、系统版本
   - 屏幕分辨率、像素比
   - 小程序版本、SDK版本

2. **本地存储持久化**：
   - 使用 `wx.setStorageSync` 持久化设备指纹
   - 避免每次启动重新生成

3. **动态硬件指纹**：
   - 每次请求时动态生成硬件指纹
   - 与存储的指纹匹配验证

4. **双重验证**：
   - 存储的设备ID（稳定性）
   - 动态硬件指纹（安全性）

## 三、使用方法

### 3.1 基础使用

```typescript
import { 
    getOrCreateDeviceFingerprint, 
    generateHardwareFingerprint,
    fingerprintToString,
    getDeviceName 
} from './device-fingerprint';

// 1. 获取或创建设备指纹（首次启动时会生成并保存）
const fingerprint = await getOrCreateDeviceFingerprint();
console.log('设备品牌:', fingerprint.brand);
console.log('设备型号:', fingerprint.model);

// 2. 获取设备名称（用于显示）
const deviceName = await getDeviceName();
console.log('设备名称:', deviceName); // "iPhone 15 Pro on ios"
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
        deviceId: await getOrCreateDeviceId(),  // 设备ID（兼容旧版本）
        hardwareFingerprint: fingerprintToString(dynamicFingerprint)  // 硬件指纹
    };
    
    // 4. 发送登录请求
    wx.request({
        url: 'https://your-gateway.com/api/login',
        method: 'POST',
        header: {
            'Content-Type': 'application/json',
            'X-Hardware-Fingerprint': fingerprintToString(dynamicFingerprint)
        },
        data: loginRequest,
        success: (res) => {
            console.log('登录成功', res.data);
        }
    });
}
```

### 3.3 后续请求时使用

```typescript
import { generateHardwareFingerprint, fingerprintToString } from './device-fingerprint';

// 在 HTTP 拦截器中自动添加硬件指纹
const originalRequest = wx.request;
wx.request = function(options) {
    // 每次请求时重新生成硬件指纹
    generateHardwareFingerprint().then(fingerprint => {
        const fingerprintJson = fingerprintToString(fingerprint);
        
        // 添加到请求头
        options.header = options.header || {};
        options.header['X-Hardware-Fingerprint'] = fingerprintJson;
        
        // 调用原始请求
        originalRequest(options);
    });
};
```

## 四、硬件指纹特征说明

### 4.1 包含的特征

| 特征 | 说明 | 稳定性 | 权重 |
|------|------|--------|------|
| **品牌和型号** | 设备品牌 + 型号 | ⭐⭐⭐⭐⭐ | 0.4 |
| **平台** | ios、android | ⭐⭐⭐⭐⭐ | 0.2 |
| **屏幕分辨率** | 屏幕宽度和高度 | ⭐⭐⭐⭐ | 0.15 |
| **像素比** | 屏幕缩放因子 | ⭐⭐⭐⭐ | 0.1 |
| **系统版本** | 操作系统版本 | ⭐⭐⭐ | 0.1 |
| **语言** | 系统语言 | ⭐⭐⭐ | 0.05 |

### 4.2 相似度计算

硬件指纹验证使用相似度比较，允许硬件特征有轻微变化：

- **相似度 >= 0.8**：认为匹配（允许通过）
- **相似度 < 0.8**：认为不匹配（拒绝请求）

**容错场景**：
- 系统版本更新（如 iOS 17.0 → iOS 17.1）
- 屏幕分辨率变化（如果设备支持）
- 语言切换

## 五、安全优势

### 5.1 相比浏览器端

✅ **系统信息更稳定**：
- 小程序可以获取设备品牌、型号等系统信息
- 不受浏览器环境影响

✅ **本地存储更可靠**：
- 使用微信提供的存储API，稳定性高
- 不受浏览器隐私模式影响

### 5.2 攻击难度

即使攻击者盗取了 Token 和 deviceId：
- ❌ **无法伪造硬件指纹**：设备品牌、型号等系统信息无法伪造
- ❌ **无法模拟系统环境**：即使知道部分信息，也无法完全匹配硬件指纹

## 六、注意事项

### 6.1 小程序限制

⚠️ **系统信息有限**：
- 无法获取 MAC 地址
- 无法获取详细的硬件信息（CPU、内存等）
- 依赖微信提供的系统信息API

⚠️ **存储限制**：
- 小程序存储空间有限（通常 10MB）
- 用户清除小程序数据后，设备指纹会丢失

### 6.2 最佳实践

1. **定期验证**：
   - 定期验证设备指纹是否仍然有效
   - 如果相似度降低，提示用户重新认证

2. **错误处理**：
   - 如果获取系统信息失败，使用默认值
   - 如果存储失败，每次启动重新生成

3. **兼容性**：
   - 提供 `getOrCreateDeviceId()` 方法，兼容旧版本
   - 新版本使用硬件指纹，旧版本使用设备ID

## 七、常见问题

### Q1: 小程序设备指纹会变化吗？

**A**: 可能会变化：
- 系统版本更新：可能改变系统版本信息
- 用户清除小程序数据：设备指纹会丢失，需要重新生成
- 设备更换：会生成新的设备指纹

### Q2: 如果系统信息获取失败怎么办？

**A**: 
- 使用默认值（"unknown"）
- 记录警告日志
- 设备指纹仍然可以生成，但可能不够稳定

### Q3: 可以禁用硬件指纹验证吗？

**A**: 
- 不建议禁用，这会降低安全性
- 如果确实需要，可以修改后端验证逻辑
- 但建议保留 deviceId 验证作为基础保护

## 八、总结

小程序端的设备指纹方案虽然受限于小程序环境，但仍然提供了**有效的安全保护**：

1. ✅ **系统信息组合**：基于设备品牌、型号等系统信息
2. ✅ **本地存储持久化**：使用微信存储API，稳定性高
3. ✅ **动态硬件指纹**：每次请求时动态生成，提高安全性
4. ✅ **双重验证**：设备ID + 硬件指纹，提高安全性

相比浏览器端，小程序端可以获取更稳定的系统信息，提供更好的设备识别能力。
