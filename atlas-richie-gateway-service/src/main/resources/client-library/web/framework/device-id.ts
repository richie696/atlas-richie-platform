import { sha256 as jsSha256 } from 'js-sha256';

/**
 * 浏览器设备ID生成工具
 * <p>
 * 提供可靠的浏览器设备识别方案，结合浏览器指纹和本地存储，确保设备ID的稳定性和唯一性。
 * <p>
 * 实现策略：
 * <ol>
 *   <li>优先从 LocalStorage 读取已存储的设备ID（最稳定）</li>
 *   <li>如果不存在，生成浏览器指纹（基于多种浏览器特征）</li>
 *   <li>将生成的设备ID保存到 LocalStorage（持久化）</li>
 *   <li>如果 LocalStorage 不可用（隐私模式），降级到 SessionStorage</li>
 * </ol>
 * <p>
 * 浏览器指纹包含的特征：
 * <ul>
 *   <li>User-Agent（浏览器类型和版本）</li>
 *   <li>屏幕分辨率（width x height）</li>
 *   <li>时区（Intl.DateTimeFormat().resolvedOptions().timeZone）</li>
 *   <li>语言（navigator.language）</li>
 *   <li>Canvas 指纹（Canvas 渲染特征）</li>
 *   <li>WebGL 指纹（WebGL 渲染特征）</li>
 *   <li>字体列表（通过 Canvas 检测）</li>
 *   <li>硬件并发数（navigator.hardwareConcurrency）</li>
 *   <li>设备内存（navigator.deviceMemory，如果可用）</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 5.0.0
 */

/**
 * 设备ID存储键名
 */
const DEVICE_ID_STORAGE_KEY = 'atlas_device_id';

/**
 * 设备ID存储键名（备用，用于SessionStorage）
 */
const DEVICE_ID_SESSION_KEY = 'atlas_device_id_session';

/**
 * 获取或创建设备ID
 * <p>
 * 执行流程：
 * <ol>
 *   <li>尝试从 LocalStorage 读取设备ID</li>
 *   <li>如果不存在，生成浏览器指纹</li>
 *   <li>保存到 LocalStorage（如果失败，尝试 SessionStorage）</li>
 *   <li>返回设备ID</li>
 * </ol>
 *
 * @returns Promise<string> 设备ID（SHA-256哈希，64字符）
 *
 * @example
 * ```typescript
 * const deviceId = await getOrCreateDeviceId();
 * console.log('设备ID:', deviceId);
 * ```
 */
export async function getOrCreateDeviceId(): Promise<string> {
    // 1. 尝试从 LocalStorage 读取
    try {
        const storedId = localStorage.getItem(DEVICE_ID_STORAGE_KEY);
        if (storedId && storedId.length === 64) {
            return storedId;
        }
    } catch (e) {
        // LocalStorage 可能被禁用（隐私模式等）
        console.warn('[DeviceId] LocalStorage 不可用，尝试 SessionStorage', e);
    }

    // 2. 尝试从 SessionStorage 读取（隐私模式降级）
    try {
        const sessionId = sessionStorage.getItem(DEVICE_ID_SESSION_KEY);
        if (sessionId && sessionId.length === 64) {
            return sessionId;
        }
    } catch (e) {
        console.warn('[DeviceId] SessionStorage 也不可用', e);
    }

    // 3. 生成新的设备ID（浏览器指纹）
    const deviceId = await generateBrowserFingerprint();

    // 4. 尝试保存到 LocalStorage
    try {
        localStorage.setItem(DEVICE_ID_STORAGE_KEY, deviceId);
        console.log('[DeviceId] 设备ID已保存到 LocalStorage');
    } catch (e) {
        // LocalStorage 不可用，尝试 SessionStorage
        try {
            sessionStorage.setItem(DEVICE_ID_SESSION_KEY, deviceId);
            console.warn('[DeviceId] LocalStorage 不可用，已保存到 SessionStorage（会话级）');
        } catch (e2) {
            console.warn('[DeviceId] 无法保存设备ID到存储，每次访问将生成新的设备ID');
        }
    }

    return deviceId;
}

/**
 * 生成浏览器指纹
 * <p>
 * 基于多种浏览器特征生成唯一标识，使用 SHA-256 哈希确保长度固定（64字符）。
 * <p>
 * 包含的特征：
 * <ul>
 *   <li>User-Agent</li>
 *   <li>屏幕分辨率</li>
 *   <li>时区</li>
 *   <li>语言</li>
 *   <li>Canvas 指纹</li>
 *   <li>WebGL 指纹</li>
 *   <li>字体列表</li>
 *   <li>硬件并发数</li>
 *   <li>设备内存（如果可用）</li>
 * </ul>
 *
 * @returns Promise<string> 浏览器指纹（SHA-256哈希，64字符）
 */
async function generateBrowserFingerprint(): Promise<string> {
    const components: string[] = [];

    // 1. User-Agent
    components.push(navigator.userAgent || '');

    // 2. 屏幕分辨率
    components.push(`${screen.width}x${screen.height}`);

    // 3. 时区
    try {
        const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
        components.push(timeZone || '');
    } catch (e) {
        components.push('');
    }

    // 4. 语言
    components.push(navigator.language || '');

    // 5. Canvas 指纹
    try {
        const canvas = document.createElement('canvas');
        canvas.width = 200;
        canvas.height = 50;
        const ctx = canvas.getContext('2d');
        if (ctx) {
            ctx.textBaseline = 'top';
            ctx.font = '14px Arial';
            ctx.fillText('Browser fingerprint test 🔒', 2, 2);
            const canvasFingerprint = canvas.toDataURL();
            components.push(canvasFingerprint.substring(0, 100)); // 只取前100字符
        }
    } catch (e) {
        components.push('');
    }

    // 6. WebGL 指纹
    try {
        const canvas = document.createElement('canvas');
        const gl = (canvas.getContext('webgl') || canvas.getContext('experimental-webgl')) as WebGLRenderingContext | null;
        if (gl) {
            const debugInfo = gl.getExtension('WEBGL_debug_renderer_info');
            if (debugInfo) {
                const vendor = gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL);
                const renderer = gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL);
                components.push(vendor || '');
                components.push(renderer || '');
            }
        }
    } catch (e) {
        components.push('');
    }

    // 7. 硬件并发数
    components.push(String(navigator.hardwareConcurrency || 0));

    // 8. 设备内存（如果可用）
    if ('deviceMemory' in navigator) {
        components.push(String((navigator as any).deviceMemory || 0));
    }

    // 9. 平台信息
    components.push(getPlatformFromUserAgent());

    // 10. 颜色深度
    components.push(String(screen.colorDepth || 0));

    // 11. 像素比
    components.push(String(window.devicePixelRatio || 1));

    // 组合所有特征并生成 SHA-256 哈希
    const fingerprintString = components.join('|');
    return await sha256(fingerprintString);
}

/**
 * SHA-256 哈希计算
 * <p>
 * 优先使用 Web Crypto API；在非安全上下文（如内网 IP HTTP、Capacitor 等）时使用 js-sha256 降级。
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
            console.warn('[DeviceId] crypto.subtle.digest 不可用，使用 js-sha256 降级', e);
        }
    } else {
        console.warn('[DeviceId] crypto.subtle 不可用（非安全上下文），使用 js-sha256 降级');
    }
    return jsSha256.hex(text);
}

/**
 * 基于 User-Agent 推断平台信息
 * <p>
 * 避免使用已弃用的 navigator.platform 属性，改为通过 UA 解析操作系统类型。
 */
function getPlatformFromUserAgent(): string {
    const ua = navigator.userAgent || '';

    if (/Windows/.test(ua)) {
        return 'Windows';
    }
    if (/Macintosh|Mac OS X/.test(ua)) {
        return 'Mac';
    }
    if (/Linux/.test(ua)) {
        return 'Linux';
    }
    if (/Android/.test(ua)) {
        return 'Android';
    }
    if (/iPhone|iPad|iPod/.test(ua)) {
        return 'iOS';
    }
    return 'Unknown';
}

/**
 * 获取设备名称（用于显示）
 * <p>
 * 基于 User-Agent 解析设备类型和浏览器名称，用于在管理界面显示。
 *
 * @returns 设备名称（例如："Chrome on Windows PC"、"Safari on iPhone"）
 *
 * @example
 * ```typescript
 * const deviceName = getDeviceName();
 * console.log('设备名称:', deviceName); // "Chrome on Windows PC"
 * ```
 */
export function getDeviceName(): string {
    const ua = navigator.userAgent;

    // 检测设备类型
    let deviceType = '';
    if (/Mobile|Android|iPhone|iPad/.test(ua)) {
        // 移动设备
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
        // 桌面设备
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

    // 检测浏览器名称
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

/**
 * 从 User-Agent 中提取版本号（主版本.次版本）
 */
function parseVersion(ua: string, pattern: RegExp): string {
    const match = ua.match(pattern);
    if (!match || !match[1]) return '';
    const full = match[1];
    const parts = full.split('.');
    if (parts.length >= 2) return `${parts[0]}.${parts[1]}`;
    return full;
}

/**
 * 获取浏览器名称与版本号（用于可信设备等界面显示）
 * <p>
 * 基于 User-Agent 解析浏览器名称和主版本号，格式为「浏览器名称 版本号」。
 *
 * @returns 例如："Chrome 120.0"、"Safari 17.2"、"Edge 120.0"
 *
 * @example
 * ```typescript
 * const name = getBrowserNameAndVersion();
 * console.log(name); // "Chrome 120.0"
 * ```
 */
export function getBrowserNameAndVersion(): string {
    const ua = navigator.userAgent || '';
    // 检测顺序：Edge/Opera 先于 Chrome（UA 中可能含 Chrome）；Safari 用 Version/ 取版本
    let name = '';
    let version = '';

    if (/Edg\//.test(ua)) {
        name = 'Edge';
        version = parseVersion(ua, /Edg\/([\d.]+)/);
    } else if (/OPR\//.test(ua)) {
        name = 'Opera';
        version = parseVersion(ua, /OPR\/([\d.]+)/);
    } else if (/Chrome\//.test(ua) && !/Chromium/.test(ua)) {
        name = 'Chrome';
        version = parseVersion(ua, /Chrome\/([\d.]+)/);
    } else if (/Firefox\//.test(ua)) {
        name = 'Firefox';
        version = parseVersion(ua, /Firefox\/([\d.]+)/);
    } else if (/Safari\//.test(ua) && !/Chrome/.test(ua)) {
        name = 'Safari';
        version = parseVersion(ua, /Version\/([\d.]+)/);
    } else {
        name = 'Unknown Browser';
    }

    if (version) return `${name} ${version}`;
    return name;
}

/**
 * 获取设备指纹（原始指纹字符串，用于审计）
 * <p>
 * 返回用于生成设备ID的原始指纹字符串，可用于安全审计或调试。
 * <p>
 * 注意：此方法会重新生成指纹，不读取存储的设备ID。
 *
 * @returns Promise<string> 原始指纹字符串
 */
export async function getDeviceFingerprint(): Promise<string> {
    const components: string[] = [];

    components.push(navigator.userAgent || '');
    components.push(`${screen.width}x${screen.height}`);

    try {
        const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
        components.push(timeZone || '');
    } catch (e) {
        components.push('');
    }

    components.push(navigator.language || '');
    components.push(String(navigator.hardwareConcurrency || 0));
    components.push(getPlatformFromUserAgent() || '');
    components.push(String(screen.colorDepth || 0));
    components.push(String(window.devicePixelRatio || 1));

    return components.join('|');
}

/**
 * 清除设备ID
 * <p>
 * 清除存储的设备ID，下次调用 {@link getOrCreateDeviceId} 时会生成新的设备ID。
 * <p>
 * 典型使用场景：
 * <ul>
 *   <li>用户登出时清除设备ID</li>
 *   <li>用户主动重置设备信任时</li>
 * </ul>
 */
export function clearDeviceId(): void {
    try {
        localStorage.removeItem(DEVICE_ID_STORAGE_KEY);
    } catch (e) {
        // 忽略错误
    }

    try {
        sessionStorage.removeItem(DEVICE_ID_SESSION_KEY);
    } catch (e) {
        // 忽略错误
    }

    console.log('[DeviceId] 设备ID已清除');
}
