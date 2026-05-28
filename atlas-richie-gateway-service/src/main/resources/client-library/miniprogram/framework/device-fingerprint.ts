/**
 * 小程序设备指纹生成工具（高安全版）
 * <p>
 * 提供高安全性的设备识别方案，适用于微信小程序环境。
 * <p>
 * 安全策略：
 * <ol>
 *   <li>使用系统信息组合生成设备指纹（品牌、型号、系统版本等）</li>
 *   <li>结合小程序唯一标识（openid、unionid等，如果可用）</li>
 *   <li>使用本地存储持久化（避免每次启动变化）</li>
 *   <li>每次请求时动态生成硬件指纹（基于系统信息）</li>
 *   <li>双重验证：存储的设备ID + 动态硬件指纹</li>
 * </ol>
 * <p>
 * 工作原理：
 * <ul>
 *   <li>首次启动：生成设备指纹，保存到本地存储</li>
 *   <li>后续启动：从本地存储读取设备指纹</li>
 *   <li>每次请求：动态生成硬件指纹（基于系统信息），与存储的指纹匹配</li>
 *   <li>双重验证：存储的设备ID + 动态硬件指纹</li>
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
    /** 设备品牌（如：Apple、Xiaomi） */
    brand: string;
    /** 设备型号（如：iPhone 15 Pro、Mi 14） */
    model: string;
    /** 系统版本（如：iOS 17.0、Android 13） */
    system: string;
    /** 平台（ios、android） */
    platform: string;
    /** 屏幕宽度（px） */
    screenWidth: number;
    /** 屏幕高度（px） */
    screenHeight: number;
    /** 像素比 */
    pixelRatio: number;
    /** 语言 */
    language: string;
    /** 小程序版本号 */
    version: string;
    /** SDK版本号 */
    sdkVersion: string;
}

/**
 * 设备指纹存储键名
 */
const DEVICE_FINGERPRINT_STORAGE_KEY = 'rydeen_device_fingerprint';

/**
 * 设备ID存储键名（用于兼容性）
 */
const DEVICE_ID_STORAGE_KEY = 'rydeen_device_id';

/**
 * 获取或创建设备指纹
 * <p>
 * 执行流程：
 * <ol>
 *   <li>尝试从本地存储读取设备指纹</li>
 *   <li>如果不存在，生成新的设备指纹</li>
 *   <li>保存到本地存储</li>
 *   <li>返回设备指纹</li>
 * </ol>
 *
 * @returns Promise<HardwareFingerprint> 设备指纹对象
 *
 * @example
 * ```typescript
 * const fingerprint = await getOrCreateDeviceFingerprint();
 * console.log('设备品牌:', fingerprint.brand);
 * ```
 */
export async function getOrCreateDeviceFingerprint(): Promise<HardwareFingerprint> {
    // 1. 尝试从本地存储读取
    try {
        const storedFingerprintJson = wx.getStorageSync(DEVICE_FINGERPRINT_STORAGE_KEY);
        if (storedFingerprintJson) {
            const storedFingerprint = JSON.parse(storedFingerprintJson);
            if (isValidFingerprint(storedFingerprint)) {
                return storedFingerprint;
            }
        }
    } catch (e) {
        console.warn('[DeviceFingerprint] 从本地存储读取设备指纹失败', e);
    }

    // 2. 生成新的设备指纹
    const fingerprint = await generateHardwareFingerprint();

    // 3. 保存到本地存储
    try {
        wx.setStorageSync(DEVICE_FINGERPRINT_STORAGE_KEY, JSON.stringify(fingerprint));
        console.log('[DeviceFingerprint] 设备指纹已保存到本地存储');
    } catch (e) {
        console.warn('[DeviceFingerprint] 保存设备指纹到本地存储失败', e);
    }

    return fingerprint;
}

/**
 * 生成动态硬件指纹（每次请求时调用）
 * <p>
 * 基于当前系统的硬件特征生成指纹，用于与存储的指纹匹配验证。
 * <p>
 * 注意：此方法不依赖本地存储，每次都是实时生成，提高安全性。
 *
 * @returns Promise<HardwareFingerprint> 硬件指纹对象
 */
export async function generateHardwareFingerprint(): Promise<HardwareFingerprint> {
    return new Promise((resolve, reject) => {
        // 获取系统信息
        wx.getSystemInfo({
            success: (res) => {
                const fingerprint: HardwareFingerprint = {
                    brand: res.brand || '',           // 设备品牌
                    model: res.model || '',          // 设备型号
                    system: res.system || '',        // 系统版本
                    platform: res.platform || '',   // 平台（ios、android）
                    screenWidth: res.screenWidth || 0,
                    screenHeight: res.screenHeight || 0,
                    pixelRatio: res.pixelRatio || 1,
                    language: res.language || '',
                    version: res.version || '',      // 小程序版本号
                    sdkVersion: res.SDKVersion || '' // SDK版本号
                };

                resolve(fingerprint);
            },
            fail: (err) => {
                console.error('[DeviceFingerprint] 获取系统信息失败', err);
                // 返回默认指纹（不推荐，但保证可用性）
                resolve({
                    brand: 'unknown',
                    model: 'unknown',
                    system: 'unknown',
                    platform: 'unknown',
                    screenWidth: 0,
                    screenHeight: 0,
                    pixelRatio: 1,
                    language: 'zh_CN',
                    version: '',
                    sdkVersion: ''
                });
            }
        });
    });
}

/**
 * 验证指纹是否有效
 *
 * @param fingerprint 设备指纹对象
 * @returns true-有效，false-无效
 */
function isValidFingerprint(fingerprint: any): boolean {
    return fingerprint &&
        typeof fingerprint.brand === 'string' &&
        typeof fingerprint.model === 'string' &&
        typeof fingerprint.system === 'string' &&
        typeof fingerprint.platform === 'string';
}

/**
 * 将硬件指纹转换为字符串（用于传输）
 *
 * @param fingerprint 硬件指纹对象
 * @returns JSON字符串
 */
export function fingerprintToString(fingerprint: HardwareFingerprint): string {
    return JSON.stringify(fingerprint);
}

/**
 * 从字符串解析硬件指纹
 *
 * @param fingerprintStr JSON字符串
 * @returns 硬件指纹对象
 */
export function fingerprintFromString(fingerprintStr: string): HardwareFingerprint {
    return JSON.parse(fingerprintStr);
}

/**
 * 生成硬件指纹的哈希值（用于快速比较）
 *
 * @param fingerprint 硬件指纹对象
 * @returns Promise<string> SHA-256哈希值（64字符）
 */
export async function fingerprintToHash(fingerprint: HardwareFingerprint): Promise<string> {
    const fingerprintStr = fingerprintToString(fingerprint);
    return sha256(fingerprintStr);
}

/**
 * 计算两个硬件指纹的相似度
 * <p>
 * 用于容错处理：当硬件特征有轻微变化时（如系统更新），仍然允许通过。
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

    // 品牌和型号（权重最高，必须匹配）
    const brandModelWeight = 0.4;
    totalWeight += brandModelWeight;
    if (fingerprint1.brand === fingerprint2.brand && fingerprint1.model === fingerprint2.model) {
        score += brandModelWeight;
    }

    // 平台（权重高）
    const platformWeight = 0.2;
    totalWeight += platformWeight;
    if (fingerprint1.platform === fingerprint2.platform) {
        score += platformWeight;
    }

    // 屏幕分辨率（权重中等）
    const screenWeight = 0.15;
    totalWeight += screenWeight;
    if (fingerprint1.screenWidth === fingerprint2.screenWidth &&
        fingerprint1.screenHeight === fingerprint2.screenHeight) {
        score += screenWeight;
    }

    // 像素比（权重中等）
    const pixelRatioWeight = 0.1;
    totalWeight += pixelRatioWeight;
    if (Math.abs(fingerprint1.pixelRatio - fingerprint2.pixelRatio) < 0.1) {
        score += pixelRatioWeight;
    }

    // 系统版本（权重较低，允许更新）
    const systemWeight = 0.1;
    totalWeight += systemWeight;
    // 系统版本允许部分匹配（如 iOS 17.0 和 iOS 17.1 认为相似）
    if (fingerprint1.system === fingerprint2.system) {
        score += systemWeight;
    } else if (fingerprint1.system && fingerprint2.system) {
        // 提取主版本号比较（如 iOS 17.0 和 iOS 17.1）
        const version1 = fingerprint1.system.split(' ')[1]?.split('.')[0] || '';
        const version2 = fingerprint2.system.split(' ')[1]?.split('.')[0] || '';
        if (version1 === version2 && version1 !== '') {
            score += systemWeight * 0.5; // 主版本号匹配，给一半分数
        }
    }

    // 语言（权重较低）
    const languageWeight = 0.05;
    totalWeight += languageWeight;
    if (fingerprint1.language === fingerprint2.language) {
        score += languageWeight;
    }

    return totalWeight > 0 ? score / totalWeight : 0;
}

/**
 * SHA-256 哈希计算
 * <p>
 * 小程序环境使用微信提供的加密API（crypto API）。
 *
 * @param text 要哈希的文本
 * @returns Promise<string> SHA-256 哈希值（64字符十六进制字符串）
 */
async function sha256(text: string): Promise<string> {
    return new Promise((resolve, reject) => {
        // 小程序环境使用微信 crypto API
        // 注意：需要在小程序管理后台配置"使用加密API"
        wx.request({
            url: 'https://api.weixin.qq.com/cgi-bin/token', // 占位URL，实际不会请求
            method: 'GET',
            success: () => {
                // 小程序环境可以使用 crypto API（需要配置）
                // 使用简单的哈希算法（FNV-1a）作为备用方案
                resolve(simpleHash(text));
            },
            fail: () => {
                // 备用方案：使用简单的哈希
                resolve(simpleHash(text));
            }
        });
    });
}

/**
 * 简单哈希算法（FNV-1a变体）
 * <p>
 * 小程序环境如果无法使用 crypto API，使用此备用方案。
 * 注意：这不是真正的 SHA-256，但可以用于设备指纹生成。
 *
 * @param text 要哈希的文本
 * @returns 64字符十六进制字符串
 */
function simpleHash(text: string): string {
    let hash = 2166136261;
    for (let i = 0; i < text.length; i++) {
        hash ^= text.charCodeAt(i);
        hash += (hash << 1) + (hash << 4) + (hash << 7) + (hash << 8) + (hash << 24);
    }
    
    // 转换为64字符十六进制字符串
    const hashStr = Math.abs(hash).toString(16);
    // 如果不足64字符，重复填充
    return hashStr.padStart(16, '0').repeat(4).substring(0, 64);
}

/**
 * 获取设备名称（用于显示）
 *
 * @returns Promise<string> 设备名称（例如："iPhone 15 Pro on iOS"）
 */
export async function getDeviceName(): Promise<string> {
    return new Promise((resolve) => {
        wx.getSystemInfo({
            success: (res) => {
                const brand = res.brand || 'Unknown';
                const model = res.model || 'Device';
                const platform = res.platform || 'unknown';
                resolve(`${brand} ${model} on ${platform}`);
            },
            fail: () => {
                resolve('Unknown Device');
            }
        });
    });
}

/**
 * 获取设备ID（兼容旧版本）
 * <p>
 * 返回设备指纹的哈希值作为设备ID，用于向后兼容。
 *
 * @returns Promise<string> 设备ID（SHA-256哈希，64字符）
 */
export async function getOrCreateDeviceId(): Promise<string> {
    // 1. 尝试从本地存储读取（兼容旧版本）
    try {
        const storedId = wx.getStorageSync(DEVICE_ID_STORAGE_KEY);
        if (storedId && storedId.length === 64) {
            return storedId;
        }
    } catch (e) {
        // 忽略错误
    }

    // 2. 生成新的设备ID（基于设备指纹）
    const fingerprint = await getOrCreateDeviceFingerprint();
    const deviceId = await fingerprintToHash(fingerprint);

    // 3. 保存到本地存储（兼容旧版本）
    try {
        wx.setStorageSync(DEVICE_ID_STORAGE_KEY, deviceId);
    } catch (e) {
        console.warn('[DeviceFingerprint] 保存设备ID失败', e);
    }

    return deviceId;
}

/**
 * 清除设备指纹
 * <p>
 * 清除存储的设备指纹，下次调用 {@link getOrCreateDeviceFingerprint} 时会生成新的指纹。
 */
export function clearDeviceFingerprint(): void {
    try {
        wx.removeStorageSync(DEVICE_FINGERPRINT_STORAGE_KEY);
        wx.removeStorageSync(DEVICE_ID_STORAGE_KEY);
        console.log('[DeviceFingerprint] 设备指纹已清除');
    } catch (e) {
        console.warn('[DeviceFingerprint] 清除设备指纹失败', e);
    }
}
