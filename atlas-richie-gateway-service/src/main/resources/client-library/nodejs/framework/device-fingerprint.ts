/**
 * Node.js 设备指纹生成工具（高安全版）
 * <p>
 * 提供高安全性的设备识别方案，适用于 Node.js 服务器端应用。
 * <p>
 * 安全策略：
 * <ol>
 *   <li>使用机器ID（基于硬件特征）作为基础标识</li>
 *   <li>结合系统硬件特征（CPU、内存、网络接口MAC地址等）</li>
 *   <li>使用文件系统持久化（避免每次重启变化）</li>
 *   <li>结合进程ID、启动时间等运行时特征</li>
 *   <li>使用加密存储（避免明文存储敏感信息）</li>
 * </ol>
 * <p>
 * 工作原理：
 * <ul>
 *   <li>首次运行：生成设备指纹，保存到文件系统（加密存储）</li>
 *   <li>后续运行：从文件系统读取设备指纹</li>
 *   <li>每次请求：动态生成硬件指纹（基于系统特征），与存储的指纹匹配</li>
 *   <li>双重验证：存储的设备ID + 动态硬件指纹</li>
 * </ul>
 *
 * @author richie696
 * @version 2.0
 * @since 1.0.0
 */

import * as crypto from 'crypto';
import * as os from 'os';
import * as fs from 'fs';
import * as path from 'path';

/**
 * 硬件指纹特征接口
 */
export interface HardwareFingerprint {
    /** 机器ID（基于硬件特征） */
    machineId: string;
    /** CPU信息（型号、核心数） */
    cpu: string;
    /** 总内存（字节） */
    totalMemory: number;
    /** 网络接口MAC地址（主要接口） */
    macAddress: string;
    /** 主机名 */
    hostname: string;
    /** 平台信息（操作系统类型） */
    platform: string;
    /** 架构信息（x64、arm64等） */
    arch: string;
    /** 操作系统版本 */
    osRelease: string;
    /** 网络接口列表（所有接口的MAC地址） */
    networkInterfaces: string[];
}

/**
 * 设备指纹存储配置
 */
interface FingerprintStorageConfig {
    /** 存储文件路径（默认：应用数据目录） */
    storagePath?: string;
    /** 存储文件名（默认：.rydeen_device_fingerprint） */
    storageFileName?: string;
    /** 加密密钥（用于加密存储，如果提供） */
    encryptionKey?: string;
}

/**
 * 默认存储配置
 */
const DEFAULT_CONFIG: Required<FingerprintStorageConfig> = {
    storagePath: path.join(os.homedir(), '.rydeen'),
    storageFileName: '.device_fingerprint',
    encryptionKey: '' // 默认不加密，生产环境建议提供
};

/**
 * 获取或创建设备指纹
 * <p>
 * 执行流程：
 * <ol>
 *   <li>尝试从文件系统读取设备指纹</li>
 *   <li>如果不存在，生成新的设备指纹</li>
 *   <li>保存到文件系统（可选加密）</li>
 *   <li>返回设备指纹</li>
 * </ol>
 *
 * @param config 存储配置（可选）
 * @returns Promise<HardwareFingerprint> 设备指纹对象
 *
 * @example
 * ```typescript
 * const fingerprint = await getOrCreateDeviceFingerprint();
 * console.log('机器ID:', fingerprint.machineId);
 * ```
 */
export async function getOrCreateDeviceFingerprint(
    config?: FingerprintStorageConfig
): Promise<HardwareFingerprint> {
    const finalConfig = { ...DEFAULT_CONFIG, ...config };

    // 1. 尝试从文件系统读取
    const storedFingerprint = await loadFingerprintFromFile(finalConfig);
    if (storedFingerprint) {
        return storedFingerprint;
    }

    // 2. 生成新的设备指纹
    const fingerprint = await generateHardwareFingerprint();

    // 3. 保存到文件系统
    await saveFingerprintToFile(fingerprint, finalConfig);

    return fingerprint;
}

/**
 * 生成动态硬件指纹（每次请求时调用）
 * <p>
 * 基于当前系统的硬件特征生成指纹，用于与存储的指纹匹配验证。
 * <p>
 * 注意：此方法不依赖文件系统，每次都是实时生成，提高安全性。
 *
 * @returns Promise<HardwareFingerprint> 硬件指纹对象
 */
export async function generateHardwareFingerprint(): Promise<HardwareFingerprint> {
    const fingerprint: HardwareFingerprint = {
        machineId: await getMachineId(),
        cpu: getCpuInfo(),
        totalMemory: os.totalmem(),
        macAddress: getPrimaryMacAddress(),
        hostname: os.hostname(),
        platform: os.platform(),
        arch: os.arch(),
        osRelease: os.release(),
        networkInterfaces: getAllMacAddresses()
    };

    return fingerprint;
}

/**
 * 获取机器ID（基于硬件特征）
 * <p>
 * 优先使用系统提供的机器ID，如果不可用，则基于硬件特征生成。
 *
 * @returns Promise<string> 机器ID
 */
async function getMachineId(): Promise<string> {
    try {
        // 方案1：尝试使用 node-machine-id（如果可用）
        // 注意：需要安装 npm install node-machine-id
        try {
            const machineId = require('node-machine-id');
            return await machineId.machineId();
        } catch (e) {
            // node-machine-id 不可用，使用备用方案
        }

        // 方案2：基于硬件特征生成机器ID
        const components = [
            os.hostname(),
            os.platform(),
            os.arch(),
            os.totalmem().toString(),
            os.cpus().length.toString(),
            getPrimaryMacAddress()
        ];

        const machineIdString = components.join('|');
        return crypto.createHash('sha256').update(machineIdString).digest('hex').substring(0, 32);
    } catch (e) {
        // 如果所有方案都失败，使用随机UUID（不推荐，但保证可用性）
        console.warn('[DeviceFingerprint] 无法获取机器ID，使用随机UUID', e);
        return crypto.randomUUID();
    }
}

/**
 * 获取CPU信息
 *
 * @returns CPU信息字符串（型号 + 核心数）
 */
function getCpuInfo(): string {
    const cpus = os.cpus();
    if (cpus.length === 0) {
        return 'unknown';
    }

    const firstCpu = cpus[0];
    return `${firstCpu.model}|${cpus.length}`;
}

/**
 * 获取主要网络接口的MAC地址
 *
 * @returns MAC地址（如果不存在返回空字符串）
 */
function getPrimaryMacAddress(): string {
    const interfaces = os.networkInterfaces();
    
    // 优先选择非内部接口（eth0、en0等）
    for (const name of Object.keys(interfaces)) {
        const iface = interfaces[name];
        if (!iface) continue;

        for (const addr of iface) {
            if (!addr.internal && addr.mac && addr.mac !== '00:00:00:00:00:00') {
                return addr.mac;
            }
        }
    }

    // 如果没有找到非内部接口，使用第一个有效的MAC地址
    for (const name of Object.keys(interfaces)) {
        const iface = interfaces[name];
        if (!iface) continue;

        for (const addr of iface) {
            if (addr.mac && addr.mac !== '00:00:00:00:00:00') {
                return addr.mac;
            }
        }
    }

    return '';
}

/**
 * 获取所有网络接口的MAC地址
 *
 * @returns MAC地址数组
 */
function getAllMacAddresses(): string[] {
    const interfaces = os.networkInterfaces();
    const macAddresses: string[] = [];

    for (const name of Object.keys(interfaces)) {
        const iface = interfaces[name];
        if (!iface) continue;

        for (const addr of iface) {
            if (addr.mac && addr.mac !== '00:00:00:00:00:00' && !macAddresses.includes(addr.mac)) {
                macAddresses.push(addr.mac);
            }
        }
    }

    return macAddresses.sort(); // 排序确保一致性
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
 * @returns SHA-256哈希值（64字符）
 */
export function fingerprintToHash(fingerprint: HardwareFingerprint): string {
    const fingerprintStr = fingerprintToString(fingerprint);
    return crypto.createHash('sha256').update(fingerprintStr).digest('hex');
}

/**
 * 计算两个硬件指纹的相似度
 * <p>
 * 用于容错处理：当硬件特征有轻微变化时（如网络接口变化），仍然允许通过。
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

    // 机器ID（权重最高，必须匹配）
    const machineIdWeight = 0.4;
    totalWeight += machineIdWeight;
    if (fingerprint1.machineId === fingerprint2.machineId) {
        score += machineIdWeight;
    }

    // CPU信息（权重高）
    const cpuWeight = 0.2;
    totalWeight += cpuWeight;
    if (fingerprint1.cpu === fingerprint2.cpu) {
        score += cpuWeight;
    }

    // 总内存（权重中等，允许小幅变化）
    const memoryWeight = 0.1;
    totalWeight += memoryWeight;
    const memoryDiff = Math.abs(fingerprint1.totalMemory - fingerprint2.totalMemory);
    const memoryTolerance = fingerprint1.totalMemory * 0.01; // 允许1%的误差
    if (memoryDiff <= memoryTolerance) {
        score += memoryWeight;
    }

    // 主要MAC地址（权重中等）
    const macWeight = 0.15;
    totalWeight += macWeight;
    if (fingerprint1.macAddress === fingerprint2.macAddress) {
        score += macWeight;
    }

    // 主机名（权重较低）
    const hostnameWeight = 0.05;
    totalWeight += hostnameWeight;
    if (fingerprint1.hostname === fingerprint2.hostname) {
        score += hostnameWeight;
    }

    // 平台和架构（权重较低）
    const platformWeight = 0.05;
    totalWeight += platformWeight;
    if (fingerprint1.platform === fingerprint2.platform && fingerprint1.arch === fingerprint2.arch) {
        score += platformWeight;
    }

    // 网络接口列表（权重较低，允许部分匹配）
    const networkWeight = 0.05;
    totalWeight += networkWeight;
    const commonInterfaces = fingerprint1.networkInterfaces.filter(mac =>
        fingerprint2.networkInterfaces.includes(mac)
    );
    const networkSimilarity = fingerprint1.networkInterfaces.length > 0
        ? commonInterfaces.length / Math.max(fingerprint1.networkInterfaces.length, fingerprint2.networkInterfaces.length)
        : 1;
    score += networkWeight * networkSimilarity;

    return totalWeight > 0 ? score / totalWeight : 0;
}

/**
 * 从文件系统加载设备指纹
 *
 * @param config 存储配置
 * @returns 设备指纹对象，如果不存在返回 null
 */
async function loadFingerprintFromFile(
    config: Required<FingerprintStorageConfig>
): Promise<HardwareFingerprint | null> {
    const filePath = path.join(config.storagePath, config.storageFileName);

    try {
        if (!fs.existsSync(filePath)) {
            return null;
        }

        let content = fs.readFileSync(filePath, 'utf8');

        // 如果配置了加密密钥，解密内容
        if (config.encryptionKey) {
            content = decryptContent(content, config.encryptionKey);
        }

        return fingerprintFromString(content);
    } catch (e) {
        console.warn('[DeviceFingerprint] 从文件加载设备指纹失败', e);
        return null;
    }
}

/**
 * 保存设备指纹到文件系统
 *
 * @param fingerprint 设备指纹对象
 * @param config 存储配置
 */
async function saveFingerprintToFile(
    fingerprint: HardwareFingerprint,
    config: Required<FingerprintStorageConfig>
): Promise<void> {
    const filePath = path.join(config.storagePath, config.storageFileName);

    try {
        // 确保目录存在
        if (!fs.existsSync(config.storagePath)) {
            fs.mkdirSync(config.storagePath, { recursive: true });
        }

        let content = fingerprintToString(fingerprint);

        // 如果配置了加密密钥，加密内容
        if (config.encryptionKey) {
            content = encryptContent(content, config.encryptionKey);
        }

        // 保存到文件（设置权限：仅所有者可读写）
        fs.writeFileSync(filePath, content, { mode: 0o600 });
        console.log('[DeviceFingerprint] 设备指纹已保存到文件:', filePath);
    } catch (e) {
        console.error('[DeviceFingerprint] 保存设备指纹到文件失败', e);
        throw e;
    }
}

/**
 * 加密内容（AES-256-GCM）
 *
 * @param content 原始内容
 * @param key 加密密钥（32字节，如果不足会自动补齐）
 * @returns 加密后的Base64字符串
 */
function encryptContent(content: string, key: string): string {
    // 将密钥转换为32字节
    const keyBuffer = crypto.createHash('sha256').update(key).digest();

    // 生成随机IV（12字节）
    const iv = crypto.randomBytes(12);

    // 创建加密器
    const cipher = crypto.createCipheriv('aes-256-gcm', keyBuffer, iv);

    // 加密内容
    let encrypted = cipher.update(content, 'utf8');
    encrypted = Buffer.concat([encrypted, cipher.final()]);

    // 获取认证标签
    const authTag = cipher.getAuthTag();

    // 组合：IV(12字节) + 加密数据 + Tag(16字节)
    const combined = Buffer.concat([iv, encrypted, authTag]);

    return combined.toString('base64');
}

/**
 * 解密内容（AES-256-GCM）
 *
 * @param encryptedContent 加密的Base64字符串
 * @param key 加密密钥
 * @returns 解密后的原始内容
 */
function decryptContent(encryptedContent: string, key: string): string {
    // 将密钥转换为32字节
    const keyBuffer = crypto.createHash('sha256').update(key).digest();

    // 解码Base64
    const combined = Buffer.from(encryptedContent, 'base64');

    // 分离：IV(12字节) + 加密数据 + Tag(16字节)
    const iv = combined.slice(0, 12);
    const authTag = combined.slice(-16);
    const encrypted = combined.slice(12, -16);

    // 创建解密器
    const decipher = crypto.createDecipheriv('aes-256-gcm', keyBuffer, iv);
    decipher.setAuthTag(authTag);

    // 解密内容
    let decrypted = decipher.update(encrypted);
    decrypted = Buffer.concat([decrypted, decipher.final()]);

    return decrypted.toString('utf8');
}

/**
 * 获取设备名称（用于显示）
 *
 * @returns 设备名称（例如："Node.js Server on Windows PC (hostname)"）
 */
export function getDeviceName(): string {
    const platform = os.platform();
    const hostname = os.hostname();

    let platformName = '';
    if (platform === 'win32') {
        platformName = 'Windows PC';
    } else if (platform === 'darwin') {
        platformName = 'Mac';
    } else if (platform === 'linux') {
        platformName = 'Linux PC';
    } else {
        platformName = 'Server';
    }

    return `Node.js Server on ${platformName} (${hostname})`;
}

/**
 * 清除设备指纹
 * <p>
 * 清除存储的设备指纹文件，下次调用 {@link getOrCreateDeviceFingerprint} 时会生成新的指纹。
 *
 * @param config 存储配置（可选）
 */
export async function clearDeviceFingerprint(config?: FingerprintStorageConfig): Promise<void> {
    const finalConfig = { ...DEFAULT_CONFIG, ...config };
    const filePath = path.join(finalConfig.storagePath, finalConfig.storageFileName);

    try {
        if (fs.existsSync(filePath)) {
            fs.unlinkSync(filePath);
            console.log('[DeviceFingerprint] 设备指纹已清除');
        }
    } catch (e) {
        console.warn('[DeviceFingerprint] 清除设备指纹失败', e);
    }
}
