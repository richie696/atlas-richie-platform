# 硬件指纹安全方案使用指南

## 一、安全问题分析

### 1.1 原有方案的安全风险

**问题**：如果 `deviceId` 明文存储在 `localStorage` 中，攻击者可以同时盗取：
- Token（存储在 localStorage 或内存中）
- deviceId（存储在 localStorage 中）

这样攻击者就可以在任意设备上使用盗取的 Token 和 deviceId，**完全失去了设备绑定的保护作用**。

### 1.2 新方案的安全增强

**解决方案**：采用**动态硬件指纹验证**，提高同时盗取 deviceId 和 token 的难度：

1. **不依赖固定的 deviceId**：
   - 不再将固定的 deviceId 存储在 localStorage
   - 每次请求时动态生成硬件指纹（基于 Canvas、WebGL、屏幕分辨率等）

2. **硬件指纹难以伪造**：
   - Canvas 指纹：基于 GPU 渲染特征，难以伪造
   - WebGL 指纹：基于 GPU 硬件信息，难以伪造
   - 屏幕分辨率、时区等：即使知道这些信息，也无法完全伪造 Canvas/WebGL 指纹

3. **双重验证**：
   - deviceId 验证（如果提供）：基础验证
   - 硬件指纹验证（必须）：增强验证
   - 即使 deviceId 被盗，硬件指纹不匹配也会拒绝请求

## 二、实现方案

### 2.1 前端实现

#### 2.1.1 登录时生成硬件指纹

```typescript
import { generateHardwareFingerprint, fingerprintToString } from './device-fingerprint';

async function login(username: string, password: string, mfaCode?: string) {
    // 1. 生成硬件指纹（每次登录时动态生成）
    const fingerprint = await generateHardwareFingerprint();
    const fingerprintJson = fingerprintToString(fingerprint);
    
    // 2. 构建登录请求
    const loginRequest = {
        username,
        password,
        mfaCode,
        hardwareFingerprint: fingerprintJson  // 硬件指纹（JSON字符串）
    };
    
    // 3. 发送登录请求（硬件指纹通过 Header 传递）
    const response = await fetch('/api/login', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Hardware-Fingerprint': fingerprintJson  // 通过 Header 传递
        },
        body: JSON.stringify(loginRequest)
    });
    
    return await response.json();
}
```

#### 2.1.2 后续请求时携带硬件指纹

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

### 2.2 后端实现

#### 2.2.1 Token 签发时记录硬件指纹

在 `IssueTokensFilter` 中，当签发 Token 时，如果请求中提供了硬件指纹，将其记录到 Token 的 claims 中：

```java
// 如果提供了硬件指纹，将其绑定到Token中
HardwareFingerprintUtils.HardwareFingerprint hardwareFingerprint = extractHardwareFingerprint(exchange);
if (hardwareFingerprint != null) {
    String fingerprintJson = HardwareFingerprintUtils.serializeFingerprint(hardwareFingerprint);
    if (StringUtils.isNotBlank(fingerprintJson)) {
        userVO.addParam("hardwareFingerprint", fingerprintJson);
    }
}
```

#### 2.2.2 Token 验证时检查硬件指纹

在 `AuthenticationFilter` 中，验证 Token 时，如果 Token 中包含硬件指纹，验证请求中的硬件指纹是否匹配：

```java
// 验证硬件指纹（增强安全：即使deviceId被盗，硬件指纹不匹配也会拒绝）
HardwareFingerprintUtils.HardwareFingerprint tokenFingerprint = extractHardwareFingerprintFromToken(token);
if (tokenFingerprint != null) {
    HardwareFingerprintUtils.HardwareFingerprint requestFingerprint = extractHardwareFingerprintFromRequest(exchange);
    if (requestFingerprint == null) {
        // Token中包含硬件指纹，但请求中未提供，拒绝
        return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_2"));
    }
    
    if (!HardwareFingerprintUtils.verifyFingerprint(requestFingerprint, tokenFingerprint)) {
        // 硬件指纹不匹配，拒绝
        return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_2"));
    }
}
```

## 三、安全优势

### 3.1 提高攻击难度

✅ **即使攻击者盗取了 Token 和 deviceId**：
- 无法伪造硬件指纹（Canvas、WebGL 指纹基于 GPU 硬件特征）
- 即使知道屏幕分辨率、时区等信息，也无法完全伪造 Canvas/WebGL 指纹
- 请求会被拒绝

✅ **硬件指纹动态生成**：
- 不依赖 localStorage 存储
- 每次请求时重新生成，即使 localStorage 被盗也不影响

✅ **双重验证**：
- deviceId 验证（如果提供）：基础验证
- 硬件指纹验证（必须）：增强验证
- 两者都通过才能使用 Token

### 3.2 容错处理

硬件特征可能因为以下原因发生变化：
- 浏览器更新
- 插件安装/卸载
- 系统设置变更

**解决方案**：使用相似度比较，允许硬件特征有轻微变化：
- Canvas 和 WebGL 指纹必须完全匹配（权重最高）
- 其他特征允许有轻微变化
- 相似度 >= 0.8 认为匹配

## 四、使用建议

### 4.1 前端集成

1. **登录时**：
   - 生成硬件指纹
   - 通过 Header `X-Hardware-Fingerprint` 传递给后端

2. **后续请求**：
   - 每次请求时重新生成硬件指纹
   - 通过 Header `X-Hardware-Fingerprint` 传递给后端

3. **HTTP 客户端封装**：
   - 在 HTTP 拦截器中自动添加硬件指纹
   - 避免在每个请求中手动添加

### 4.2 后端配置

1. **启用硬件指纹验证**：
   - 默认已启用，无需额外配置
   - 如果 Token 中包含硬件指纹，必须验证

2. **容错阈值**：
   - 默认相似度阈值为 0.8
   - 可根据实际情况调整（在 `HardwareFingerprintUtils.SIMILARITY_THRESHOLD` 中修改）

### 4.3 兼容性处理

**向后兼容**：
- 如果 Token 中不包含硬件指纹，不进行验证（兼容旧 Token）
- 如果请求中不提供硬件指纹，但 Token 中包含，拒绝请求（安全优先）

**渐进式升级**：
- 新登录的 Token 会包含硬件指纹
- 旧 Token 仍然可以使用（不包含硬件指纹）
- 建议逐步迁移到新方案

## 五、常见问题

### Q1: 硬件指纹会影响性能吗？

**A**: 影响很小：
- Canvas 和 WebGL 指纹生成耗时 < 10ms
- 只在登录和每次 API 请求时生成
- 可以通过 HTTP 拦截器统一处理，对业务代码无侵入

### Q2: 硬件指纹会变化吗？

**A**: 可能会轻微变化：
- 浏览器更新：可能改变 Canvas/WebGL 渲染特征
- 插件安装/卸载：可能影响 WebGL 指纹
- 系统设置变更：可能影响屏幕分辨率、时区等

**解决方案**：使用相似度比较，允许轻微变化（相似度 >= 0.8 认为匹配）

### Q3: 如果硬件指纹不匹配怎么办？

**A**: 
- 请求会被拒绝（返回 401 Unauthorized）
- 用户需要重新登录，重新生成硬件指纹
- 这是预期行为，确保安全性

### Q4: 可以禁用硬件指纹验证吗？

**A**: 
- 不建议禁用，这会降低安全性
- 如果确实需要，可以修改 `AuthenticationFilter`，跳过硬件指纹验证
- 但建议保留 deviceId 验证作为基础保护

## 六、总结

通过采用**动态硬件指纹验证**方案，我们显著提高了同时盗取 deviceId 和 token 的难度：

1. ✅ **硬件指纹难以伪造**：基于 GPU 硬件特征，攻击者无法伪造
2. ✅ **动态生成**：不依赖 localStorage，每次请求时重新生成
3. ✅ **双重验证**：deviceId + 硬件指纹，两者都通过才能使用 Token
4. ✅ **容错处理**：允许硬件特征有轻微变化，不影响正常使用

这样即使攻击者盗取了 Token 和 deviceId，也无法在非授权设备上使用，**有效防止了 token 被盗用的问题**。
