import { sha256 as jsSha256 } from 'js-sha256';

/**
 * 动态设备指纹生成工具（增强安全版）
 * <p>
 * 提供更安全的设备识别方案，通过动态生成硬件指纹，提高同时盗取 deviceId 和 token 的难度。
 * <p>
 * 安全策略：
 * <ol>
 *   <li>不存储固定的 deviceId，而是每次请求时动态生成硬件指纹</li>
 *   <li>硬件指纹基于多种难以伪造的特征（Canvas、WebGL、屏幕分辨率等）</li>
 *   <li>后端在签发 token 时记录硬件指纹，后续请求时验证指纹是否匹配</li>
 *   <li>即使攻击者盗取了 token 和 localStorage 中的 deviceId，也无法伪造硬件指纹</li>
 * </ol>
 * <p>
 * 工作原理：
 * <ul>
 *   <li>登录时：生成硬件指纹，发送给后端，后端记录在 token 中</li>
 *   <li>后续请求：每次请求时重新生成硬件指纹，后端验证是否与 token 中的指纹匹配</li>
 *   <li>容错处理：允许硬件特征有轻微变化（如浏览器更新），但大幅变化会拒绝</li>
 * </ul>
 *
 * @author richie696
 * @version 2.0
 * @since 1.0.0
 */

/**
 * 硬件指纹特征接口
 */
export interface HardwareFingerprint {
    /** Canvas 指纹（基于 Canvas 渲染特征） */
    canvas: string;
    /** WebGL 指纹（基于 WebGL 渲染特征） */
    webgl: string;
    /** 屏幕分辨率 */
    screen: string;
    /** 时区 */
    timezone: string;
    /** 语言 */
    language: string;
    /** 硬件并发数 */
    hardwareConcurrency: number;
    /** 设备内存（如果可用） */
    deviceMemory?: number;
    /** 颜色深度 */
    colorDepth: number;
    /** 像素比 */
    pixelRatio: number;
    /** 平台信息 */
    platform: string;
}

/**
 * 带安全字段的硬件指纹接口（用于签名和验证）
 */
export interface HardwareFingerprintWithSecurity extends HardwareFingerprint {
    /** 时间戳（毫秒） */
    timestamp: number;
    /** 随机字符串（Base64编码，16字节） */
    nonce: string;
}

/**
 * 生成动态硬件指纹
 * <p>
 * 每次调用都会重新生成，基于当前浏览器的硬件特征。
 * <p>
 * 注意：此方法不依赖 localStorage，每次都是实时生成，提高安全性。
 *
 * @returns Promise<HardwareFingerprint> 硬件指纹对象
 *
 * @example
 * ```typescript
 * const fingerprint = await generateHardwareFingerprint();
 * console.log('Canvas指纹:', fingerprint.canvas);
 * ```
 */
export async function generateHardwareFingerprint(): Promise<HardwareFingerprint> {
    const fingerprint: HardwareFingerprint = {
        canvas: '',
        webgl: '',
        screen: '',
        timezone: '',
        language: '',
        hardwareConcurrency: 0,
        deviceMemory: undefined,
        colorDepth: 0,
        pixelRatio: 1,
        platform: ''
    };

    // 1. Canvas 指纹（最稳定且难以伪造）
    try {
        const canvas = document.createElement('canvas');
        canvas.width = 200;
        canvas.height = 50;
        const ctx = canvas.getContext('2d');
        if (ctx) {
            ctx.textBaseline = 'top';
            ctx.font = '14px Arial';
            ctx.fillText('Hardware fingerprint 🔒', 2, 2);
            const canvasDataUrl = canvas.toDataURL();
            // 使用 SHA-256 哈希，避免传输大量数据
            fingerprint.canvas = await sha256(canvasDataUrl);
        }
    } catch (e) {
        console.warn('[Fingerprint] Canvas指纹生成失败', e);
    }

    // 2. WebGL 指纹（GPU相关，难以伪造）
    try {
        const canvas = document.createElement('canvas');
        const glContext = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
        const gl = glContext as WebGLRenderingContext | null;
        if (gl) {
            const debugInfo = gl.getExtension('WEBGL_debug_renderer_info');
            if (debugInfo) {
                const vendor = gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL) || '';
                const renderer = gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) || '';
                const webglInfo = `${vendor}|${renderer}`;
                fingerprint.webgl = await sha256(webglInfo);
            }
        }
    } catch (e) {
        console.warn('[Fingerprint] WebGL指纹生成失败', e);
    }

    // 3. 屏幕分辨率
    fingerprint.screen = `${screen.width}x${screen.height}`;

    // 4. 时区
    try {
        fingerprint.timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || '';
    } catch (e) {
        console.warn('[Fingerprint] 时区获取失败', e);
    }

    // 5. 语言
    fingerprint.language = navigator.language || '';

    // 6. 硬件并发数
    fingerprint.hardwareConcurrency = navigator.hardwareConcurrency || 0;

    // 7. 设备内存（如果可用）
    if ('deviceMemory' in navigator) {
        fingerprint.deviceMemory = (navigator as any).deviceMemory;
    }

    // 8. 颜色深度
    fingerprint.colorDepth = screen.colorDepth || 0;

    // 9. 像素比
    fingerprint.pixelRatio = window.devicePixelRatio || 1;

    // 10. 平台信息
    fingerprint.platform = navigator.platform || '';

    return fingerprint;
}

/**
 * 将硬件指纹转换为字符串（用于传输）
 * <p>
 * 将硬件指纹对象序列化为字符串，便于在请求中传输。
 *
 * @param fingerprint 硬件指纹对象
 * @returns 硬件指纹字符串（JSON格式）
 */
export function fingerprintToString(fingerprint: HardwareFingerprint): string {
    return JSON.stringify(fingerprint);
}

/**
 * 生成带安全字段的硬件指纹（包含时间戳和随机数）
 * <p>
 * 用于HMAC签名，防止重放攻击。
 *
 * @returns Promise<HardwareFingerprintWithSecurity> 带安全字段的硬件指纹对象
 */
export async function generateSecureHardwareFingerprint(): Promise<HardwareFingerprintWithSecurity> {
    const fingerprint = await generateHardwareFingerprint();
    const timestamp = Date.now();
    
    // 生成16字节随机数，并转换为Base64
    const randomBytes = crypto.getRandomValues(new Uint8Array(16));
    const nonce = btoa(String.fromCharCode(...randomBytes));
    
    return {
        ...fingerprint,
        timestamp,
        nonce,
    };
}

/**
 * 使用HMAC-SHA256对硬件指纹进行签名
 * <p>
 * 签名格式：`{JSON数据}.{Base64编码的签名}`
 * <p>
 * 安全机制：
 * - 防止篡改：签名验证确保数据未被修改
 * - 防止重放：时间戳和随机数确保每次请求唯一
 *
 * @param fingerprint 带安全字段的硬件指纹对象
 * @param secretKey HMAC密钥（应与后端共享）
 * @returns Promise<string> 签名后的字符串（格式：JSON.签名）
 *
 * @example
 * ```typescript
 * const fingerprint = await generateSecureHardwareFingerprint();
 * const signed = await signHardwareFingerprint(fingerprint, 'your-secret-key');
 * // 返回：'{"canvas":"...","timestamp":1234567890,"nonce":"..."}.{签名}'
 * ```
 */
export async function signHardwareFingerprint(
    fingerprint: HardwareFingerprintWithSecurity,
    secretKey: string
): Promise<string> {
    const json = JSON.stringify(fingerprint);
    const subtle = typeof crypto !== 'undefined' ? crypto.subtle : undefined;

    if (subtle) {
        try {
            const encoder = new TextEncoder();
            const keyData = encoder.encode(secretKey);
            const messageData = encoder.encode(json);
            const cryptoKey = await subtle.importKey(
                'raw',
                keyData,
                { name: 'HMAC', hash: 'SHA-256' },
                false,
                ['sign']
            );
            const signature = await subtle.sign('HMAC', cryptoKey, messageData);
            const signatureArray = Array.from(new Uint8Array(signature));
            const signatureBase64 = btoa(String.fromCharCode(...signatureArray));
            return `${json}.${signatureBase64}`;
        } catch (e) {
            console.warn('[DeviceFingerprint] crypto.subtle 签名失败，使用 js-sha256 降级', e);
        }
    } else {
        console.warn('[DeviceFingerprint] crypto.subtle 不可用（非安全上下文），使用 js-sha256 降级');
    }

    // 降级：内网 IP（如 10.x）或非 HTTPS 时使用 js-sha256 的 HMAC-SHA256
    const signatureArray = jsSha256.hmac.array(secretKey, json);
    const signatureBase64 = btoa(String.fromCharCode(...signatureArray));
    return `${json}.${signatureBase64}`;
}

/**
 * 从字符串解析硬件指纹
 *
 * @param fingerprintStr 硬件指纹字符串
 * @returns 硬件指纹对象
 */
export function fingerprintFromString(fingerprintStr: string): HardwareFingerprint {
    return JSON.parse(fingerprintStr);
}

/**
 * 生成硬件指纹的哈希值（用于快速比较）
 * <p>
 * 将硬件指纹对象序列化后计算 SHA-256 哈希，得到一个固定长度的字符串。
 * <p>
 * 用途：
 * <ul>
 *   <li>后端可以将此哈希值存储在 token 中（节省空间）</li>
 *   <li>后续请求时，比较哈希值是否匹配</li>
 *   <li>允许硬件特征有轻微变化时，使用相似度比较</li>
 * </ul>
 *
 * @param fingerprint 硬件指纹对象
 * @returns Promise<string> 硬件指纹哈希值（64字符）
 */
export async function fingerprintToHash(fingerprint: HardwareFingerprint): Promise<string> {
    const fingerprintStr = fingerprintToString(fingerprint);
    return await sha256(fingerprintStr);
}

/**
 * 计算两个硬件指纹的相似度
 * <p>
 * 用于容错处理：当硬件特征有轻微变化时（如浏览器更新），仍然允许通过。
 * <p>
 * 相似度计算规则：
 * <ul>
 *   <li>Canvas 和 WebGL 指纹必须完全匹配（权重最高）</li>
 *   <li>屏幕分辨率、时区、语言等允许变化（权重较低）</li>
 *   <li>相似度 >= 0.8 认为匹配</li>
 * </ul>
 *
 * @param fingerprint1 第一个硬件指纹
 * @param fingerprint2 第二个硬件指纹
 * @returns 相似度（0-1之间，1表示完全匹配）
 */
export function calculateFingerprintSimilarity(
    fingerprint1: HardwareFingerprint,
    fingerprint2: HardwareFingerprint
): number {
    let score = 0;
    let totalWeight = 0;

    // Canvas 指纹（权重最高，必须匹配）
    const canvasWeight = 0.4;
    totalWeight += canvasWeight;
    if (fingerprint1.canvas === fingerprint2.canvas) {
        score += canvasWeight;
    }

    // WebGL 指纹（权重最高，必须匹配）
    const webglWeight = 0.4;
    totalWeight += webglWeight;
    if (fingerprint1.webgl === fingerprint2.webgl) {
        score += webglWeight;
    }

    // 屏幕分辨率（权重中等）
    const screenWeight = 0.05;
    totalWeight += screenWeight;
    if (fingerprint1.screen === fingerprint2.screen) {
        score += screenWeight;
    }

    // 时区（权重较低）
    const timezoneWeight = 0.03;
    totalWeight += timezoneWeight;
    if (fingerprint1.timezone === fingerprint2.timezone) {
        score += timezoneWeight;
    }

    // 语言（权重较低）
    const languageWeight = 0.02;
    totalWeight += languageWeight;
    if (fingerprint1.language === fingerprint2.language) {
        score += languageWeight;
    }

    // 硬件并发数（权重较低）
    const hardwareWeight = 0.03;
    totalWeight += hardwareWeight;
    if (fingerprint1.hardwareConcurrency === fingerprint2.hardwareConcurrency) {
        score += hardwareWeight;
    }

    // 设备内存（权重较低，可能不可用）
    const memoryWeight = 0.02;
    if (fingerprint1.deviceMemory !== undefined && fingerprint2.deviceMemory !== undefined) {
        totalWeight += memoryWeight;
        if (fingerprint1.deviceMemory === fingerprint2.deviceMemory) {
            score += memoryWeight;
        }
    }

    // 颜色深度（权重较低）
    const colorWeight = 0.02;
    totalWeight += colorWeight;
    if (fingerprint1.colorDepth === fingerprint2.colorDepth) {
        score += colorWeight;
    }

    // 像素比（权重较低）
    const pixelWeight = 0.02;
    totalWeight += pixelWeight;
    if (Math.abs(fingerprint1.pixelRatio - fingerprint2.pixelRatio) < 0.1) {
        score += pixelWeight;
    }

    // 平台信息（权重较低）
    const platformWeight = 0.01;
    totalWeight += platformWeight;
    if (fingerprint1.platform === fingerprint2.platform) {
        score += platformWeight;
    }

    return totalWeight > 0 ? score / totalWeight : 0;
}

/**
 * SHA-256 哈希计算
 * <p>
 * 优先使用 Web Crypto API；在非安全上下文（如内网 IP HTTP）时使用 js-sha256 降级。
 *
 * @param text 要哈希的文本
 * @returns Promise<string> SHA-256 哈希值（64字符十六进制字符串）
 */
async function sha256(text: string): Promise<string> {
    const subtle = typeof crypto !== 'undefined' ? crypto.subtle : undefined;
    if (subtle) {
        try {
            const encoder = new TextEncoder();
            const data = encoder.encode(text);
            const hashBuffer = await subtle.digest('SHA-256', data);
            const hashArray = Array.from(new Uint8Array(hashBuffer));
            return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
        } catch (e) {
            console.warn('[DeviceFingerprint] crypto.subtle.digest 不可用，使用 js-sha256 降级', e);
        }
    }
    return jsSha256.hex(text);
}

/**
 * 获取设备名称（用于显示）
 *
 * @returns 设备名称（例如："Chrome on Windows PC"）
 */
export function getDeviceName(): string {
    const ua = navigator.userAgent;

    let deviceType = '';
    if (/Mobile|Android|iPhone|iPad/.test(ua)) {
        if (/iPhone/.test(ua)) {
            deviceType = 'iPhone';
        } else if (/iPad/.test(ua)) {
            deviceType = 'iPad';
        } else if (/Android/.test(ua)) {
            deviceType = 'Android Device';
        } else {
            deviceType = 'Mobile Device';
        }
    } else {
        if (/Windows/.test(ua)) {
            deviceType = 'Windows PC';
        } else if (/Mac/.test(ua)) {
            deviceType = 'Mac';
        } else if (/Linux/.test(ua)) {
            deviceType = 'Linux PC';
        } else {
            deviceType = 'Desktop';
        }
    }

    let browserName = '';
    if (/Chrome/.test(ua) && !/Edge|OPR/.test(ua)) {
        browserName = 'Chrome';
    } else if (/Firefox/.test(ua)) {
        browserName = 'Firefox';
    } else if (/Safari/.test(ua) && !/Chrome/.test(ua)) {
        browserName = 'Safari';
    } else if (/Edge/.test(ua)) {
        browserName = 'Edge';
    } else if (/OPR/.test(ua)) {
        browserName = 'Opera';
    } else {
        browserName = 'Unknown Browser';
    }

    return `${browserName} on ${deviceType}`;
}
