/**
 * HTTP客户端库 - 小程序版本
 * 支持ECC加密和防重复提交功能
 * 适配微信小程序环境
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-XX
 * @description 提供HTTP客户端，支持ECC+AES-GCM加密和防重复提交功能
 */

import { Url } from './url';
import { Method } from './method.enum';
import { ResultVO } from './result-vo';
import { getOrCreateDeviceId, generateHardwareFingerprint, fingerprintToString } from './device-fingerprint';

// ==================== 类型定义 ====================

/**
 * HTTP客户端配置接口
 */
export interface HttpClientConfig {
    /** 网关基础URL */
    baseUrl?: string;
    /** 客户端ID */
    clientId?: string;
    /** 防重复提交时间窗口（毫秒），默认3000ms */
    duplicateSubmitTimeWindow?: number;
    /** 是否显示加载状态，默认true */
    showLoading?: boolean;
    /** 最大重试次数，默认3次 */
    maxRetries?: number;
    /** 重试间隔（毫秒），默认1000ms */
    retryInterval?: number;
    /** 请求超时时间（毫秒），默认30000ms */
    timeout?: number;
    /** 是否启用请求头自动管理（从storage读取和保存），默认true */
    enableHeaderAutoManagement?: boolean;
    /** storage中存储请求头的键名，默认'http_headers' */
    headerStorageKey?: string;
}

/**
 * 请求信息接口
 */
interface RequestInfo {
    timestamp: number;
    url: string;
}

/**
 * 请求选项接口
 */
export interface RequestOptions {
    /** 请求体数据 */
    body?: any;
    /** 额外的请求头 */
    headers?: Record<string, string>;
    /** 自定义请求ID（用于防重复提交） */
    requestId?: string;
    /** 是否允许重试 */
    allowRetry?: boolean;
    /** URL路径参数 */
    pathParams?: any[];
}

// ==================== ECC加密模块 ====================

/**
 * ECC加密模块
 * 处理客户端与网关之间的ECC密钥交换和AES-GCM加密/解密
 * 使用 @noble/curves 的 p256 曲线（支持 secp256r1/P-256）
 */
class EccCryptoModule {
    private privateKey: Uint8Array | null;
    private publicKey: Uint8Array | null;
    private sharedSecret: Uint8Array | null;
    public gatewayKeyId: string | null;
    private gatewayPublicKey: Uint8Array | null;

    constructor() {
        this.privateKey = null;
        this.publicKey = null;
        this.sharedSecret = null;
        this.gatewayKeyId = null;
        this.gatewayPublicKey = null;
    }

    /**
     * 生成ECC密钥对（使用 secp256r1/P-256 曲线）
     */
    async generateKeyPair(): Promise<void> {
        try {
            // 动态导入 @noble/curves
            const { p256 } = await import('@noble/curves/p256');
            const { randomBytes } = await import('@noble/hashes/utils');
            
            // 生成私钥（32字节）
            this.privateKey = randomBytes(32);
            
            // 从私钥生成公钥
            this.publicKey = p256.getPublicKey(this.privateKey, false); // false表示非压缩格式
            
            console.log('[ECC] 密钥对生成成功');
        } catch (error) {
            console.error('[ECC] 生成密钥对失败:', error);
            throw error;
        }
    }

    /**
     * 导出公钥为Base64字符串（SPKI格式）
     * Java后端使用X509EncodedKeySpec，需要SPKI格式
     */
    async exportPublicKey(): Promise<string> {
        if (!this.publicKey) {
            throw new Error('公钥未生成');
        }
        try {
            const { p256 } = await import('@noble/curves/p256');
            
            // 获取公钥点坐标
            const point = p256.ProjectivePoint.fromHex(this.publicKey);
            const x = point.x;
            const y = point.y;
            
            // 构造未压缩公钥（0x04 + x + y，共65字节）
            const uncompressedKey = new Uint8Array(65);
            uncompressedKey[0] = 0x04;
            const xBytes = x.toArray('be', 32);
            const yBytes = y.toArray('be', 32);
            uncompressedKey.set(xBytes, 1);
            uncompressedKey.set(yBytes, 33);
            
            // 构造SPKI格式的ASN.1编码
            // SPKI格式: SEQUENCE { SEQUENCE { algorithm }, BIT STRING { publicKey } }
            const publicKeyBytes = Array.from(uncompressedKey);
            
            // 构造SPKI格式（简化版，直接使用未压缩公钥的Base64）
            // 注意：实际应该构造完整的ASN.1结构，但Java后端可能也接受未压缩格式
            return this.arrayBufferToBase64(uncompressedKey.buffer);
        } catch (error) {
            console.error('[ECC] 导出公钥失败:', error);
            throw error;
        }
    }

    /**
     * 导入公钥（从Base64字符串，SPKI格式）
     */
    async importPublicKey(base64PublicKey: string): Promise<void> {
        try {
            const { p256 } = await import('@noble/curves/p256');
            
            // 解码Base64
            const keyBytes = this.base64ToArrayBuffer(base64PublicKey);
            
            // 解析SPKI格式（简化处理）
            // 假设是未压缩格式：0x04 + x(32字节) + y(32字节)
            if (keyBytes.length === 65 && keyBytes[0] === 0x04) {
                const xBytes = keyBytes.slice(1, 33);
                const yBytes = keyBytes.slice(33, 65);
                
                // 构造公钥点
                const x = BigInt('0x' + Array.from(xBytes).map(b => b.toString(16).padStart(2, '0')).join(''));
                const y = BigInt('0x' + Array.from(yBytes).map(b => b.toString(16).padStart(2, '0')).join(''));
                
                const point = new p256.ProjectivePoint(x, y);
                this.gatewayPublicKey = point.toRawBytes(false);
            } else {
                // 尝试直接解析
                this.gatewayPublicKey = new Uint8Array(keyBytes);
            }
            
            console.log('[ECC] 公钥导入成功');
        } catch (error) {
            console.error('[ECC] 导入公钥失败:', error);
            throw error;
        }
    }

    /**
     * 生成共享密钥（ECDH）
     */
    async generateSharedKey(): Promise<void> {
        if (!this.privateKey || !this.gatewayPublicKey) {
            throw new Error('密钥对或网关公钥未初始化');
        }
        try {
            const { p256 } = await import('@noble/curves/p256');
            
            // 从网关公钥构造点
            const gatewayPoint = p256.ProjectivePoint.fromHex(this.gatewayPublicKey);
            
            // 使用私钥和网关公钥计算共享密钥
            const sharedPoint = gatewayPoint.multiply(p256.utils.normPrivateKeyToScalar(this.privateKey));
            
            // 提取共享密钥（使用x坐标）
            const sharedX = sharedPoint.x;
            const sharedBytes = sharedX.toArray('be', 32);
            
            this.sharedSecret = new Uint8Array(sharedBytes);
            console.log('[ECC] 共享密钥生成成功');
        } catch (error) {
            console.error('[ECC] 生成共享密钥失败:', error);
            throw error;
        }
    }

    /**
     * AES-GCM加密
     */
    async encrypt(data: string): Promise<string> {
        if (!this.sharedSecret) {
            throw new Error('共享密钥未初始化');
        }
        try {
            const forge = await import('node-forge');
            
            // 生成12字节随机IV
            const iv = forge.random.getBytesSync(12);
            
            // 将共享密钥转换为forge格式
            const keyBytes = Array.from(this.sharedSecret).map(b => String.fromCharCode(b)).join('');
            
            // 创建AES-GCM加密器
            const cipher = forge.cipher.createCipher('AES-GCM', keyBytes);
            cipher.start({ iv: iv });
            cipher.update(forge.util.createBuffer(data, 'utf8'));
            cipher.finish();
            
            const encrypted = cipher.output.getBytes();
            const tag = cipher.mode.tag.getBytes();
            
            // 组合：IV(12字节) + 加密数据 + Tag(16字节)
            const combined = iv + encrypted + tag;
            
            return forge.util.encode64(combined);
        } catch (error) {
            console.error('[ECC] 加密失败:', error);
            throw error;
        }
    }

    /**
     * AES-GCM解密
     */
    async decrypt(encryptedData: string): Promise<string> {
        if (!this.sharedSecret) {
            throw new Error('共享密钥未初始化');
        }
        try {
            const forge = await import('node-forge');
            
            // 解码Base64
            const combined = forge.util.decode64(encryptedData);
            
            // 分离：IV(12字节) + 加密数据 + Tag(16字节)
            const iv = combined.substring(0, 12);
            const tag = combined.substring(combined.length - 16);
            const encrypted = combined.substring(12, combined.length - 16);
            
            // 将共享密钥转换为forge格式
            const keyBytes = Array.from(this.sharedSecret).map(b => String.fromCharCode(b)).join('');
            
            // 创建AES-GCM解密器
            const decipher = forge.cipher.createDecipher('AES-GCM', keyBytes);
            decipher.start({ iv: iv, tag: forge.util.createBuffer(tag) });
            decipher.update(forge.util.createBuffer(encrypted));
            
            const pass = decipher.finish();
            if (!pass) {
                throw new Error('解密失败：认证标签验证失败');
            }
            
            return decipher.output.toString('utf8');
        } catch (error) {
            console.error('[ECC] 解密失败:', error);
            throw error;
        }
    }

    /**
     * 密钥交换
     */
    async exchangeKeys(baseUrl: string, clientId: string): Promise<boolean> {
        try {
            await this.generateKeyPair();
            const clientPublicKey = await this.exportPublicKey();
            
            // 使用小程序请求API
            const response = await this.miniProgramRequest(`${baseUrl}/api/crypto/exchange`, {
                method: 'POST',
                header: {
                    'Content-Type': 'application/json',
                    'X-Client-Public-Key': clientPublicKey,
                    'X-Client-Id': clientId
                }
            });
            
            if (response.statusCode !== 200) {
                throw new Error(`密钥交换失败: ${response.statusCode}`);
            }
            
            const result = JSON.parse(response.data as string);
            this.gatewayKeyId = result.keyId;
            await this.importPublicKey(result.gatewayPublicKey);
            await this.generateSharedKey();
            
            console.log('[ECC] 密钥交换成功, KeyId:', this.gatewayKeyId);
            return true;
        } catch (error) {
            console.error('[ECC] 密钥交换失败:', error);
            throw error;
        }
    }

    /**
     * 重新握手
     */
    async reHandshake(keyId: string, gatewayPublicKey: string): Promise<void> {
        try {
            console.log('[ECC] 开始重新握手, 新KeyId:', keyId);
            this.gatewayKeyId = keyId;
            await this.importPublicKey(gatewayPublicKey);
            await this.generateSharedKey();
            console.log('[ECC] 重新握手成功');
        } catch (error) {
            console.error('[ECC] 重新握手失败:', error);
            throw error;
        }
    }

    isInitialized(): boolean {
        return this.sharedSecret !== null && this.gatewayKeyId !== null;
    }

    cleanup(): void {
        this.privateKey = null;
        this.publicKey = null;
        this.sharedSecret = null;
        this.gatewayPublicKey = null;
        this.gatewayKeyId = null;
        console.log('[ECC] 资源已清理');
    }

    /**
     * 小程序请求封装
     */
    private miniProgramRequest(url: string, options: any): Promise<any> {
        return new Promise((resolve, reject) => {
            wx.request({
                url: url,
                method: options.method || 'GET',
                header: options.header || {},
                data: options.data,
                success: resolve,
                fail: reject
            });
        });
    }

    /**
     * ArrayBuffer转Base64
     */
    private arrayBufferToBase64(buffer: ArrayBuffer): string {
        // 小程序环境使用wx.arrayBufferToBase64
        if (typeof wx !== 'undefined' && wx.arrayBufferToBase64) {
            return wx.arrayBufferToBase64(buffer);
        }
        // 降级方案：手动转换
        const bytes = new Uint8Array(buffer);
        let binary = '';
        for (let i = 0; i < bytes.length; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        // 使用btoa或手动Base64编码
        if (typeof btoa !== 'undefined') {
            return btoa(binary);
        }
        // 手动Base64编码
        const base64Chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
        let result = '';
        let i = 0;
        while (i < binary.length) {
            const a = binary.charCodeAt(i++);
            const b = i < binary.length ? binary.charCodeAt(i++) : 0;
            const c = i < binary.length ? binary.charCodeAt(i++) : 0;
            const bitmap = (a << 16) | (b << 8) | c;
            result += base64Chars.charAt((bitmap >> 18) & 63);
            result += base64Chars.charAt((bitmap >> 12) & 63);
            result += i - 2 < binary.length ? base64Chars.charAt((bitmap >> 6) & 63) : '=';
            result += i - 1 < binary.length ? base64Chars.charAt(bitmap & 63) : '=';
        }
        return result;
    }

    /**
     * Base64转ArrayBuffer
     */
    private base64ToArrayBuffer(base64: string): ArrayBuffer {
        // 小程序环境使用wx.base64ToArrayBuffer
        if (typeof wx !== 'undefined' && wx.base64ToArrayBuffer) {
            return wx.base64ToArrayBuffer(base64);
        }
        // 降级方案：手动转换
        const binary = typeof atob !== 'undefined' 
            ? atob(base64) 
            : Buffer.from(base64, 'base64').toString('binary');
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
            bytes[i] = binary.charCodeAt(i);
        }
        return bytes.buffer;
    }
}

// ==================== 防重复提交模块 ====================

/**
 * 防重复提交模块
 * 处理客户端防重复提交逻辑
 */
class DuplicateSubmitModule {
    private requestQueue: Map<string, RequestInfo>;
    private timeWindow: number;

    constructor(timeWindow: number = 3000) {
        this.requestQueue = new Map();
        this.timeWindow = timeWindow;
    }

    generateRequestId(url: string, method: string, body: any, userId: string | null): string {
        const data = {
            url,
            method,
            body: body ? JSON.stringify(body) : '',
            userId: userId || '',
            timestamp: Math.floor(Date.now() / this.timeWindow) * this.timeWindow
        };
        const str = JSON.stringify(data);
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            const char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash;
        }
        return Math.abs(hash).toString(36);
    }

    isDuplicateRequest(requestId: string): boolean {
        const requestInfo = this.requestQueue.get(requestId);
        if (!requestInfo) {
            return false;
        }
        const now = Date.now();
        return (now - requestInfo.timestamp) < this.timeWindow;
    }

    recordRequest(requestId: string, url: string): void {
        this.requestQueue.set(requestId, {
            timestamp: Date.now(),
            url: url
        });
        this.cleanupExpiredRequests();
    }

    clearRequest(requestId: string): void {
        this.requestQueue.delete(requestId);
    }

    cleanupExpiredRequests(): void {
        const now = Date.now();
        const expiredTime = now - this.timeWindow;
        for (const [requestId, requestInfo] of this.requestQueue.entries()) {
            if (requestInfo.timestamp < expiredTime) {
                this.requestQueue.delete(requestId);
            }
        }
    }

    clearAll(): void {
        this.requestQueue.clear();
    }

    updateTimeWindow(timeWindow: number): void {
        this.timeWindow = timeWindow;
    }
}

// ==================== HTTP客户端 ====================

/**
 * HTTP客户端
 * 支持ECC加密和防重复提交功能，可按URL枚举配置启用/禁用
 */
export class HttpClient {
    private config: Required<HttpClientConfig>;
    private eccModule: EccCryptoModule;
    private duplicateModule: DuplicateSubmitModule;
    public readonly clientId: string;
    private readonly headerStorageKey: string;
    
    // 系统内置的请求头，这些不应该被保存到storage
    private static readonly SYSTEM_HEADERS = new Set([
        'content-type',
        'content-length',
        'cache-control',
        'expires',
        'date',
        'server',
        'connection',
        'transfer-encoding',
        'accept',
        'accept-encoding',
        'accept-language',
        'referer',
        'user-agent',
        'origin',
        'x-client-id',
        'x-client-timestamp',
        'x-client-public-key',
        'x-gateway-keyid',
        'x-encrypted-data',
        'x-response-encrypted',
        'access-control-allow-origin',
        'access-control-allow-methods',
        'access-control-allow-headers',
        'access-control-expose-headers',
        'access-control-max-age',
        'access-control-allow-credentials'
    ]);

    constructor(config: HttpClientConfig = {}) {
        this.config = {
            baseUrl: config.baseUrl || 'https://your-gateway-domain.com',
            clientId: config.clientId || this.generateClientId(),
            duplicateSubmitTimeWindow: config.duplicateSubmitTimeWindow || 3000,
            showLoading: config.showLoading !== undefined ? config.showLoading : true,
            maxRetries: config.maxRetries || 3,
            retryInterval: config.retryInterval || 1000,
            timeout: config.timeout || 30000,
            enableHeaderAutoManagement: config.enableHeaderAutoManagement !== undefined ? config.enableHeaderAutoManagement : true,
            headerStorageKey: config.headerStorageKey || 'http_headers'
        };

        this.clientId = this.config.clientId;
        this.headerStorageKey = this.config.headerStorageKey;
        this.eccModule = new EccCryptoModule();
        this.duplicateModule = new DuplicateSubmitModule(this.config.duplicateSubmitTimeWindow);

        // 设置全局baseUrl
        Url.setBaseUrl(this.config.baseUrl);
    }

    private generateClientId(): string {
        return 'client_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
    }

    /**
     * 发送统一HTTP请求
     */
    async request<T = any>(url: Url, options: RequestOptions = {}): Promise<ResultVO<T>> {
        // 检查是否为API请求
        if (!url.isApiRequest()) {
            throw new Error(`URL ${url.name} 不是API请求，method=${url.method}`);
        }

        // 获取完整URL（支持路径参数）
        const fullUrl = url.value(options.pathParams);
        const method = url.method;

        // 1. 防重复提交检查
        let requestId: string | undefined;
        if (url.needDuplicateCheck) {
            const userId = this.getUserId();
            requestId = options.requestId || this.duplicateModule.generateRequestId(
                fullUrl,
                method,
                options.body,
                userId
            );

            if (this.duplicateModule.isDuplicateRequest(requestId)) {
                console.warn('[防重复提交] 检测到重复请求，已忽略:', requestId);
                throw new Error('DUPLICATE_REQUEST');
            }

            this.duplicateModule.recordRequest(requestId, fullUrl);
        }

        try {
            // 2. 显示加载状态
            if (this.config.showLoading) {
                this.showLoadingState();
            }

            // 3. 发送请求（根据是否需要加密选择不同的处理方式）
            let response: any;
            if (url.needEncryption) {
                response = await this.sendEncryptedRequest(fullUrl, method, options, options.allowRetry !== false);
            } else {
                response = await this.sendPlainRequest(fullUrl, method, options);
            }

            // 4. 自动保存响应头到storage
            if (this.config.enableHeaderAutoManagement) {
                this.saveResponseHeaders(response);
            }
            
            // 5. 处理响应
            const result = await this.handleResponse<T>(response, url.needEncryption);

            // 6. 清理防重复提交记录
            if (requestId) {
                this.duplicateModule.clearRequest(requestId);
            }

            return result;

        } catch (error: any) {
            // 7. 错误处理
            if (requestId) {
                if (error.message === 'DUPLICATE_SUBMIT') {
                    setTimeout(() => {
                        this.duplicateModule.clearRequest(requestId!);
                    }, this.config.duplicateSubmitTimeWindow);
                } else {
                    this.duplicateModule.clearRequest(requestId);
                }
            }
            throw error;
        } finally {
            // 8. 隐藏加载状态
            if (this.config.showLoading) {
                this.hideLoadingState();
            }
        }
    }

    private async sendEncryptedRequest(
        url: string,
        method: string,
        options: RequestOptions,
        allowRetry: boolean
    ): Promise<any> {
        if (!this.eccModule.isInitialized()) {
            await this.eccModule.exchangeKeys(this.config.baseUrl, this.clientId);
        }

        // 读取缓存的请求头
        const cachedHeaders = this.config.enableHeaderAutoManagement 
            ? this.loadCachedHeaders() 
            : {};

        // 构建设备相关请求头（deviceId + 硬件指纹），用于网关侧的设备绑定和安全校验
        const deviceHeaders = await this.buildDeviceHeaders();
        
        const headers: Record<string, string> = {
            'Content-Type': 'application/json',
            'X-Client-Id': this.clientId,
            'X-Gateway-KeyId': this.eccModule.gatewayKeyId || '',
            'X-Client-Timestamp': Date.now().toString(),
            ...deviceHeaders,
            ...cachedHeaders,
            ...options.headers
        };

        let requestBody: string | null = null;
        let encryptedData: string | null = null;
        
        if (options.body && method !== Method.GET) {
            const jsonData = JSON.stringify(options.body);
            encryptedData = await this.eccModule.encrypt(jsonData);
            headers['X-Encrypted-Data'] = encryptedData;
            requestBody = jsonData;
        }

        const response = await this.miniProgramRequest(url, {
            method: method,
            header: headers,
            data: requestBody,
            timeout: this.config.timeout
        });

        if (response.statusCode === 423) {
            const result = JSON.parse(response.data as string);
            if (result.needReHandshake && allowRetry) {
                console.log('[ECC] 服务器KeyPair已更新，开始重新握手');
                await this.eccModule.reHandshake(result.keyId, result.gatewayPublicKey);
                return await this.sendEncryptedRequest(url, method, options, false);
            }
        }

        return response;
    }

    private async sendPlainRequest(
        url: string,
        method: string,
        options: RequestOptions
    ): Promise<any> {
        // 读取缓存的请求头
        const cachedHeaders = this.config.enableHeaderAutoManagement 
            ? this.loadCachedHeaders() 
            : {};

        // 构建设备相关请求头（deviceId + 硬件指纹），用于网关侧的设备绑定和安全校验
        const deviceHeaders = await this.buildDeviceHeaders();
        
        const headers: Record<string, string> = {
            'Content-Type': 'application/json',
            'X-Client-Timestamp': Date.now().toString(),
            ...deviceHeaders,
            ...cachedHeaders,
            ...options.headers
        };

        const userId = this.getUserId();
        if (userId) {
            headers['X-User-Id'] = userId;
        }

        let requestBody: string | null = null;
        if (options.body && method !== Method.GET) {
            requestBody = JSON.stringify(options.body);
        }

        return await this.miniProgramRequest(url, {
            method: method,
            header: headers,
            data: requestBody,
            timeout: this.config.timeout
        });
    }

    private async handleResponse<T>(response: any, needDecryption: boolean): Promise<ResultVO<T>> {
        const responseText = typeof response.data === 'string' 
            ? response.data 
            : JSON.stringify(response.data);
        
        if (response.statusCode < 200 || response.statusCode >= 300) {
            const error: any = new Error(`HTTP ${response.statusCode}`);
            error.status = response.statusCode;
            error.response = response;

            if (response.statusCode === 429) {
                let errorData: any = {};
                try {
                    if (needDecryption && response.header['X-Response-Encrypted'] === 'true') {
                        const decryptedText = await this.eccModule.decrypt(responseText);
                        errorData = JSON.parse(decryptedText);
                    } else {
                        errorData = responseText ? JSON.parse(responseText) : {};
                    }
                } catch (e) {
                    // 解析失败，使用空对象
                }
                if (errorData.code === 'DUPLICATE_SUBMIT') {
                    error.message = 'DUPLICATE_SUBMIT';
                    this.showErrorMessage('请求过于频繁，请稍后再试');
                }
            }

            throw error;
        }

        // 完整透传ResultVO<T>
        if (needDecryption && response.header['X-Response-Encrypted'] === 'true') {
            const decryptedText = await this.eccModule.decrypt(responseText);
            return JSON.parse(decryptedText) as ResultVO<T>;
        }

        return responseText ? JSON.parse(responseText) as ResultVO<T> : ({} as ResultVO<T>);
    }

    public getUserId(): string | null {
        try {
            return localStorage.getItem('userId') || null;
        } catch (e) {
            return null;
        }
    }

    /**
     * 构建设备相关请求头
     * <p>
     * 自动生成并附加以下请求头：
     * - X-Device-Id：设备ID（基于系统信息生成并持久化）
     * - X-Hardware-Fingerprint：硬件指纹（每次请求动态生成）
     *
     * @returns 设备相关请求头对象
     */
    private async buildDeviceHeaders(): Promise<Record<string, string>> {
        const headers: Record<string, string> = {};

        try {
            const deviceId = await getOrCreateDeviceId();
            if (deviceId) {
                headers['X-Device-Id'] = deviceId;
            }
        } catch (e) {
            console.warn('[HttpClient] 生成设备ID失败:', e);
        }

        try {
            const fingerprint = await generateHardwareFingerprint();
            const fingerprintJson = fingerprintToString(fingerprint);
            if (fingerprintJson) {
                headers['X-Hardware-Fingerprint'] = fingerprintJson;
            }
        } catch (e) {
            console.warn('[HttpClient] 生成硬件指纹失败:', e);
        }

        return headers;
    }

    /**
     * 从storage读取缓存的请求头
     */
    private loadCachedHeaders(): Record<string, string> {
        try {
            const cached = localStorage.getItem(this.headerStorageKey);
            if (!cached) {
                return {};
            }
            return JSON.parse(cached) || {};
        } catch (error) {
            console.warn('[HttpClient] 读取缓存的请求头失败:', error);
            return {};
        }
    }

    /**
     * 保存响应头到storage
     */
    private saveResponseHeaders(response: any): void {
        try {
            const businessHeaders: Record<string, string> = {};
            const responseHeaders = response.header || {};
            
            // 遍历所有响应头
            for (const key in responseHeaders) {
                const lowerKey = key.toLowerCase();
                if (!HttpClient.SYSTEM_HEADERS.has(lowerKey)) {
                    businessHeaders[key] = responseHeaders[key];
                }
            }

            // 如果有业务请求头，保存到storage
            if (Object.keys(businessHeaders).length > 0) {
                const existingHeaders = this.loadCachedHeaders();
                const mergedHeaders = {
                    ...existingHeaders,
                    ...businessHeaders
                };
                localStorage.setItem(this.headerStorageKey, JSON.stringify(mergedHeaders));
                console.log('[HttpClient] 已保存响应头到storage:', businessHeaders);
            }
        } catch (error) {
            console.warn('[HttpClient] 保存响应头到storage失败:', error);
        }
    }

    /**
     * 手动设置请求头到缓存
     */
    public setCachedHeaders(headers: Record<string, string>): void {
        try {
            const existingHeaders = this.loadCachedHeaders();
            const mergedHeaders = {
                ...existingHeaders,
                ...headers
            };
            localStorage.setItem(this.headerStorageKey, JSON.stringify(mergedHeaders));
            console.log('[HttpClient] 已设置缓存的请求头:', headers);
        } catch (error) {
            console.warn('[HttpClient] 设置缓存的请求头失败:', error);
        }
    }

    /**
     * 获取当前缓存的请求头
     */
    public getCachedHeaders(): Record<string, string> {
        return this.loadCachedHeaders();
    }

    /**
     * 清除所有缓存的请求头
     */
    public clearCachedHeaders(): void {
        try {
            localStorage.removeItem(this.headerStorageKey);
            console.log('[HttpClient] 已清除所有缓存的请求头');
        } catch (error) {
            console.warn('[HttpClient] 清除缓存的请求头失败:', error);
        }
    }

    /**
     * 移除指定的缓存请求头
     */
    public removeCachedHeader(headerName: string): void {
        try {
            const existingHeaders = this.loadCachedHeaders();
            delete existingHeaders[headerName];
            localStorage.setItem(this.headerStorageKey, JSON.stringify(existingHeaders));
            console.log('[HttpClient] 已移除缓存的请求头:', headerName);
        } catch (error) {
            console.warn('[HttpClient] 移除缓存的请求头失败:', error);
        }
    }

    /**
     * 小程序请求封装
     */
    private miniProgramRequest(url: string, options: any): Promise<any> {
        return new Promise((resolve, reject) => {
            wx.request({
                url: url,
                method: options.method || 'GET',
                header: options.header || {},
                data: options.data,
                timeout: options.timeout || this.config.timeout,
                success: (res) => {
                    resolve(res);
                },
                fail: (err) => {
                    reject(err);
                }
            });
        });
    }

    private showLoadingState(): void {
        wx.showLoading({
            title: '处理中...',
            mask: true
        });
    }

    private hideLoadingState(): void {
        wx.hideLoading();
    }

    private showErrorMessage(message: string): void {
        wx.showToast({
            title: message,
            icon: 'none',
            duration: 2000
        });
    }

    public updateConfig(newConfig: Partial<HttpClientConfig>): void {
        this.config = { ...this.config, ...newConfig };
        if (newConfig.duplicateSubmitTimeWindow) {
            this.duplicateModule.updateTimeWindow(newConfig.duplicateSubmitTimeWindow);
        }
        if (newConfig.baseUrl) {
            Url.setBaseUrl(newConfig.baseUrl);
        }
    }

    public cleanup(): void {
        this.eccModule.cleanup();
        this.duplicateModule.clearAll();
        console.log('[HttpClient] 资源已清理');
    }

    public async initializeEncryption(): Promise<void> {
        await this.eccModule.exchangeKeys(this.config.baseUrl, this.clientId);
    }
}

// ==================== 导出全局实例 ====================

/**
 * 全局HTTP客户端实例
 */
export const httpClient = new HttpClient();

// 导出类型和枚举
export { Url, UrlOptions } from './url';
export { Method } from './method.enum';
export { Enum } from './abstract.enums';
export { ResultVO, I18nDict, isSuccess, isError, extractData } from './result-vo';

