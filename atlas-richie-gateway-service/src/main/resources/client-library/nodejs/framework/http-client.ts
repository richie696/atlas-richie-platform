/**
 * HTTP客户端库 - Node.js版本
 * 支持ECC加密和防重复提交功能
 * 适配Java风格的URL枚举系统
 * 适用于Node.js服务器端应用
 *
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 * @description 提供HTTP客户端，支持ECC+AES-GCM加密和防重复提交功能
 */

import { Url } from './url';
import { Method } from './method.enum';
import { ResultVO } from './result-vo';
import * as crypto from 'crypto';
import { getOrCreateDeviceFingerprint, generateHardwareFingerprint, fingerprintToString } from './device-fingerprint';

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
    /** 最大重试次数，默认3次 */
    maxRetries?: number;
    /** 重试间隔（毫秒），默认1000ms */
    retryInterval?: number;
    /** 请求超时时间（毫秒），默认30000ms */
    timeout?: number;
    /** 用户ID存储（可选，用于防重复提交） */
    userId?: string;
    /** 是否启用请求头自动管理（从内存存储读取和保存），默认true */
    enableHeaderAutoManagement?: boolean;
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
    /** 自定义用户ID（覆盖配置中的userId） */
    userId?: string;
}

// ==================== ECC加密模块 ====================

/**
 * ECC加密模块
 * 处理客户端与网关之间的ECC密钥交换和AES-GCM加密/解密
 * 使用Node.js crypto模块替代Web Crypto API
 */
class EccCryptoModule {
    private readonly algorithm = 'prime256v1'; // P-256曲线
    private keyPair: crypto.KeyPairKeyObjectResult | null;
    private sharedKey: Buffer | null;
    private gatewayPublicKey: crypto.KeyObject | null;
    public gatewayKeyId: string | null;

    constructor() {
        this.keyPair = null;
        this.sharedKey = null;
        this.gatewayPublicKey = null;
        this.gatewayKeyId = null;
    }

    async generateKeyPair(): Promise<crypto.KeyPairKeyObjectResult> {
        try {
            this.keyPair = crypto.generateKeyPairSync('ec', {
                namedCurve: this.algorithm,
                publicKeyEncoding: {
                    type: 'spki',
                    format: 'der'
                },
                privateKeyEncoding: {
                    type: 'pkcs8',
                    format: 'der'
                }
            });
            console.log('[ECC] 密钥对生成成功');
            return this.keyPair;
        } catch (error) {
            console.error('[ECC] 生成密钥对失败:', error);
            throw error;
        }
    }

    async exportPublicKey(publicKey: crypto.KeyObject): Promise<string> {
        try {
            const publicKeyDer = publicKey.export({
                type: 'spki',
                format: 'der'
            }) as Buffer;
            return publicKeyDer.toString('base64');
        } catch (error) {
            console.error('[ECC] 导出公钥失败:', error);
            throw error;
        }
    }

    async importPublicKey(base64PublicKey: string): Promise<crypto.KeyObject> {
        try {
            const publicKeyBuffer = Buffer.from(base64PublicKey, 'base64');
            return crypto.createPublicKey({
                key: publicKeyBuffer,
                format: 'der',
                type: 'spki'
            });
        } catch (error) {
            console.error('[ECC] 导入公钥失败:', error);
            throw error;
        }
    }

    async generateSharedKey(remotePublicKey: crypto.KeyObject): Promise<Buffer> {
        if (!this.keyPair) throw new Error('本地密钥对未生成');
        try {
            // 使用ECDH计算共享密钥
            const ecdh = crypto.createECDH(this.algorithm);
            // 从KeyObject导出私钥
            const privateKeyDer = this.keyPair.privateKey.export({
                type: 'pkcs8',
                format: 'der'
            }) as Buffer;
            ecdh.setPrivateKey(privateKeyDer);
            
            // 从远程公钥导出DER格式
            const remotePublicKeyDer = remotePublicKey.export({
                type: 'spki',
                format: 'der'
            }) as Buffer;
            
            this.sharedKey = ecdh.computeSecret(remotePublicKeyDer);
            console.log('[ECC] 共享密钥生成成功');
            return this.sharedKey;
        } catch (error) {
            console.error('[ECC] 生成共享密钥失败:', error);
            throw error;
        }
    }

    async encrypt(data: string): Promise<string> {
        if (!this.sharedKey) {
            throw new Error('共享密钥未初始化');
        }
        try {
            // 使用共享密钥的前32字节作为AES-256密钥
            const key = this.sharedKey.slice(0, 32);
            const iv = crypto.randomBytes(12);
            const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
            
            const encrypted = Buffer.concat([
                cipher.update(data, 'utf8'),
                cipher.final()
            ]);
            
            const authTag = cipher.getAuthTag();
            
            // 组合: IV (12字节) + 加密数据 + AuthTag (16字节)
            const combined = Buffer.concat([iv, encrypted, authTag]);
            return combined.toString('base64');
        } catch (error) {
            console.error('[ECC] 加密失败:', error);
            throw error;
        }
    }

    async decrypt(encryptedData: string): Promise<string> {
        if (!this.sharedKey) {
            throw new Error('共享密钥未初始化');
        }
        try {
            const combined = Buffer.from(encryptedData, 'base64');
            
            // 提取IV、加密数据和AuthTag
            const iv = combined.slice(0, 12);
            const authTag = combined.slice(-16);
            const encrypted = combined.slice(12, -16);
            
            // 使用共享密钥的前32字节作为AES-256密钥
            const key = this.sharedKey.slice(0, 32);
            const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
            decipher.setAuthTag(authTag);
            
            const decrypted = Buffer.concat([
                decipher.update(encrypted),
                decipher.final()
            ]);
            
            return decrypted.toString('utf8');
        } catch (error) {
            console.error('[ECC] 解密失败:', error);
            throw error;
        }
    }

    async exchangeKeys(baseUrl: string, clientId: string): Promise<boolean> {
        try {
            await this.generateKeyPair();
            const clientPublicKey = await this.exportPublicKey(this.keyPair!.publicKey as crypto.KeyObject);
            
            const response = await fetch(`${baseUrl}/api/crypto/exchange`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Client-Public-Key': clientPublicKey,
                    'X-Client-Id': clientId
                }
            });
            
            if (!response.ok) {
                throw new Error(`密钥交换失败: ${response.status}`);
            }
            
            const result = await response.json();
            this.gatewayKeyId = result.keyId;
            this.gatewayPublicKey = await this.importPublicKey(result.gatewayPublicKey);
            await this.generateSharedKey(this.gatewayPublicKey);
            console.log('[ECC] 密钥交换成功, KeyId:', this.gatewayKeyId);
            return true;
        } catch (error) {
            console.error('[ECC] 密钥交换失败:', error);
            throw error;
        }
    }

    async reHandshake(keyId: string, gatewayPublicKey: string): Promise<void> {
        try {
            console.log('[ECC] 开始重新握手, 新KeyId:', keyId);
            this.gatewayKeyId = keyId;
            this.gatewayPublicKey = await this.importPublicKey(gatewayPublicKey);
            await this.generateSharedKey(this.gatewayPublicKey);
            console.log('[ECC] 重新握手成功');
        } catch (error) {
            console.error('[ECC] 重新握手失败:', error);
            throw error;
        }
    }

    isInitialized(): boolean {
        return this.sharedKey !== null && this.gatewayKeyId !== null;
    }

    cleanup(): void {
        this.keyPair = null;
        this.sharedKey = null;
        this.gatewayPublicKey = null;
        this.gatewayKeyId = null;
        console.log('[ECC] 资源已清理');
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
        const hash = crypto.createHash('md5').update(str).digest('hex');
        return hash;
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
 *
 * @example
 * ```typescript
 * // 1. 创建客户端实例
 * const client = new HttpClient({
 *   baseUrl: 'https://your-gateway.com',
 *   clientId: 'my-client-001',
 *   userId: 'user123'
 * });
 *
 * // 2. 发送请求（自动根据URL枚举的配置处理）
 * const response = await client.request(AppUrl.PAYMENT_CREATE, {
 *   body: { amount: 100, currency: 'CNY' }
 * });
 * ```
 */
export class HttpClient {
    private config: Required<Omit<HttpClientConfig, 'userId'>> & Pick<HttpClientConfig, 'userId'>;
    private eccModule: EccCryptoModule;
    private duplicateModule: DuplicateSubmitModule;
    public readonly clientId: string;
    private cachedHeaders: Record<string, string>;
    
    // 系统内置的请求头，这些不应该被保存
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
            maxRetries: config.maxRetries || 3,
            retryInterval: config.retryInterval || 1000,
            timeout: config.timeout || 30000,
            userId: config.userId,
            enableHeaderAutoManagement: config.enableHeaderAutoManagement !== undefined ? config.enableHeaderAutoManagement : true
        };

        this.clientId = this.config.clientId;
        this.cachedHeaders = {};
        this.eccModule = new EccCryptoModule();
        this.duplicateModule = new DuplicateSubmitModule(this.config.duplicateSubmitTimeWindow);

        // 设置全局baseUrl
        Url.setBaseUrl(this.config.baseUrl);
    }

    private generateClientId(): string {
        return 'nodejs-client_' + Date.now() + '_' + Math.random().toString(36).substring(2, 11);
    }

    /**
     * 发送统一HTTP请求
     * 根据URL枚举配置自动应用加密和防重复提交功能
     * 
     * @template T 响应数据的类型（ResultVO<T>中的T）
     * @param url URL枚举对象
     * @param options 请求选项
     * @returns 返回完整的ResultVO<T>对象
     * 
     * @example
     * ```typescript
     * interface User {
     *   id: number;
     *   name: string;
     * }
     * 
     * const result: ResultVO<User> = await client.request<User>(AppUrl.USER_INFO);
     * if (isSuccess(result)) {
     *   console.log('用户信息:', result.data);
     * }
     * ```
     */
    async request<T = any>(url: Url, options: RequestOptions = {}): Promise<ResultVO<T>> {
        // 检查是否为API请求
        if (!url.isApiRequest()) {
            throw new Error(`URL ${url.name} 不是API请求，method=${url.method}`);
        }

        // 获取完整URL（支持路径参数）
        const fullUrl = url.value(options.pathParams);
        const method = url.method;

        // 获取用户ID（优先使用options中的值）
        const userId = options.userId || this.config.userId || null;

        // 1. 防重复提交检查
        let requestId: string | undefined;
        if (url.needDuplicateCheck) {
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
            // 2. 发送请求（根据是否需要加密选择不同的处理方式）
            let response: Response;
            if (url.needEncryption) {
                response = await this.sendEncryptedRequest(fullUrl, method, options, options.allowRetry !== false);
            } else {
                response = await this.sendPlainRequest(fullUrl, method, options, userId);
            }

            // 3. 自动保存响应头到内存存储（在解析响应体之前，确保响应头可用）
            if (this.config.enableHeaderAutoManagement) {
                this.saveResponseHeaders(response);
            }

            // 4. 处理响应（完整透传ResultVO<T>，不做任何处理）
            const result = await this.handleResponse<T>(response, url.needEncryption);

            // 5. 清理防重复提交记录
            if (requestId) {
                this.duplicateModule.clearRequest(requestId);
            }

            return result;

        } catch (error: any) {
            // 6. 错误处理
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
        }
    }

    private async sendEncryptedRequest(
        url: string,
        method: string,
        options: RequestOptions,
        allowRetry: boolean
    ): Promise<Response> {
        if (!this.eccModule.isInitialized()) {
            await this.eccModule.exchangeKeys(this.config.baseUrl, this.clientId);
        }

        // 读取缓存的请求头（包括token等业务请求头）
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
            ...cachedHeaders,  // 先合并缓存的请求头（包括token）
            ...options.headers  // 用户自定义的请求头优先级最高
        };

        let requestBody: string | null = null;
        if (options.body && method !== Method.GET) {
            const jsonData = JSON.stringify(options.body);
            headers['X-Encrypted-Data'] = await this.eccModule.encrypt(jsonData);
            requestBody = jsonData;
        }

        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), this.config.timeout);

        try {
            const response = await fetch(url, {
                method: method,
                headers: headers,
                body: requestBody,
                signal: controller.signal
            });

            if (response.status === 423) {
                const result = await response.json();
                if (result.needReHandshake && allowRetry) {
                    console.log('[ECC] 服务器KeyPair已更新，开始重新握手');
                    await this.eccModule.reHandshake(result.keyId, result.gatewayPublicKey);
                    return await this.sendEncryptedRequest(url, method, options, false);
                }
            }

            return response;
        } finally {
            clearTimeout(timeoutId);
        }
    }

    private async sendPlainRequest(
        url: string,
        method: string,
        options: RequestOptions,
        userId: string | null
    ): Promise<Response> {
        // 读取缓存的请求头（包括token等业务请求头）
        const cachedHeaders = this.config.enableHeaderAutoManagement 
            ? this.loadCachedHeaders() 
            : {};

        // 构建设备相关请求头（deviceId + 硬件指纹），用于网关侧的设备绑定和安全校验
        const deviceHeaders = await this.buildDeviceHeaders();

        const headers: Record<string, string> = {
            'Content-Type': 'application/json',
            'X-Client-Timestamp': Date.now().toString(),
            ...deviceHeaders,
            ...cachedHeaders,  // 先合并缓存的请求头（包括token）
            ...options.headers  // 用户自定义的请求头优先级最高
        };

        if (userId) {
            headers['X-User-Id'] = userId;
        }

        let requestBody: string | null = null;
        if (options.body && method !== Method.GET) {
            requestBody = JSON.stringify(options.body);
        }

        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), this.config.timeout);

        try {
            return await fetch(url, {
                method: method,
                headers: headers,
                body: requestBody,
                signal: controller.signal
            });
        } finally {
            clearTimeout(timeoutId);
        }
    }

    private async handleResponse<T>(response: Response, needDecryption: boolean): Promise<ResultVO<T>> {
        // 先读取响应体（只能读取一次）
        const responseText = await response.text();
        
        if (!response.ok) {
            const error: any = new Error(`HTTP ${response.status}: ${response.statusText}`);
            error.status = response.status;
            error.response = response;

            if (response.status === 429) {
                let errorData: any = {};
                try {
                    if (needDecryption && response.headers.get('X-Response-Encrypted') === 'true') {
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
                    console.error('[HttpClient] 请求过于频繁，请稍后再试');
                }
            }

            throw error;
        }

        // 完整透传ResultVO<T>，不做任何处理
        if (needDecryption && response.headers.get('X-Response-Encrypted') === 'true') {
            const decryptedText = await this.eccModule.decrypt(responseText);
            return JSON.parse(decryptedText) as ResultVO<T>;
        }

        // 完整透传ResultVO<T>，不做任何处理
        return responseText ? JSON.parse(responseText) as ResultVO<T> : ({} as ResultVO<T>);
    }

    /**
     * 更新配置
     */
    public updateConfig(newConfig: Partial<HttpClientConfig>): void {
        if (newConfig.baseUrl) {
            this.config.baseUrl = newConfig.baseUrl;
            Url.setBaseUrl(newConfig.baseUrl);
        }
        if (newConfig.clientId) {
            this.config.clientId = newConfig.clientId;
        }
        if (newConfig.duplicateSubmitTimeWindow) {
            this.config.duplicateSubmitTimeWindow = newConfig.duplicateSubmitTimeWindow;
            this.duplicateModule.updateTimeWindow(newConfig.duplicateSubmitTimeWindow);
        }
        if (newConfig.timeout !== undefined) {
            this.config.timeout = newConfig.timeout;
        }
        if (newConfig.maxRetries !== undefined) {
            this.config.maxRetries = newConfig.maxRetries;
        }
        if (newConfig.retryInterval !== undefined) {
            this.config.retryInterval = newConfig.retryInterval;
        }
        if (newConfig.userId !== undefined) {
            this.config.userId = newConfig.userId;
        }
        if (newConfig.enableHeaderAutoManagement !== undefined) {
            this.config.enableHeaderAutoManagement = newConfig.enableHeaderAutoManagement;
        }
    }

    /**
     * 设置用户ID
     */
    public setUserId(userId: string | null): void {
        this.config.userId = userId || undefined;
    }

    /**
     * 从内存存储读取缓存的请求头
     * 返回一个对象，包含所有缓存的业务请求头
     */
    private loadCachedHeaders(): Record<string, string> {
        return { ...this.cachedHeaders };
    }

    /**
     * 保存响应头到内存存储
     * 只保存业务相关的请求头，排除系统内置的header
     * 网关签发的token（x-rd-request-apitoken）等业务请求头会自动保存
     */
    private saveResponseHeaders(response: Response): void {
        try {
            const businessHeaders: Record<string, string> = {};
            
            // 遍历所有响应头
            response.headers.forEach((value, key) => {
                const lowerKey = key.toLowerCase();
                
                // 排除系统内置的header，其他所有业务请求头都保存（包括x-rd-request-apitoken等）
                if (!HttpClient.SYSTEM_HEADERS.has(lowerKey)) {
                    businessHeaders[key] = value;
                }
            });

            // 如果有业务请求头，保存到内存存储
            if (Object.keys(businessHeaders).length > 0) {
                // 合并新的响应头（响应头的值会覆盖现有的值）
                this.cachedHeaders = {
                    ...this.cachedHeaders,
                    ...businessHeaders
                };
                console.log('[HttpClient] 已保存响应头到内存存储:', businessHeaders);
            }
        } catch (error) {
            console.warn('[HttpClient] 保存响应头到内存存储失败:', error);
        }
    }

    /**
     * 手动设置请求头到缓存
     * 这些请求头会在下次请求时自动添加到请求headers中
     * 
     * @param headers 要缓存的请求头
     */
    public setCachedHeaders(headers: Record<string, string>): void {
        this.cachedHeaders = {
            ...this.cachedHeaders,
            ...headers
        };
        console.log('[HttpClient] 已设置缓存的请求头:', headers);
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
        this.cachedHeaders = {};
        console.log('[HttpClient] 已清除所有缓存的请求头');
    }

    /**
     * 移除指定的缓存请求头
     * 
     * @param headerName 要移除的请求头名称
     */
    public removeCachedHeader(headerName: string): void {
        delete this.cachedHeaders[headerName];
        console.log('[HttpClient] 已移除缓存的请求头:', headerName);
    }

    /**
     * 清理资源
     */
    public cleanup(): void {
        this.eccModule.cleanup();
        this.duplicateModule.clearAll();
        console.log('[HttpClient] 资源已清理');
    }

    /**
     * 初始化加密模块
     */
    public async initializeEncryption(): Promise<void> {
        await this.eccModule.exchangeKeys(this.config.baseUrl, this.clientId);
    }

    /**
     * 构建设备相关请求头
     * <p>
     * 自动生成并附加以下请求头：
     * - X-Device-Id：设备ID（基于机器ID生成并持久化）
     * - X-Hardware-Fingerprint：硬件指纹（每次请求动态生成）
     *
     * @returns 设备相关请求头对象
     */
    private async buildDeviceHeaders(): Promise<Record<string, string>> {
        const headers: Record<string, string> = {};

        try {
            const storedFingerprint = await getOrCreateDeviceFingerprint();
            if (storedFingerprint.machineId) {
                headers['X-Device-Id'] = storedFingerprint.machineId;
            }
        } catch (e) {
            console.warn('[HttpClient] 获取设备指纹失败（stored）:', e);
        }

        try {
            const dynamicFingerprint = await generateHardwareFingerprint();
            const fingerprintJson = fingerprintToString(dynamicFingerprint);
            if (fingerprintJson) {
                headers['X-Hardware-Fingerprint'] = fingerprintJson;
            }
        } catch (e) {
            console.warn('[HttpClient] 生成动态硬件指纹失败:', e);
        }

        return headers;
    }
}

// ==================== 导出类型和枚举 ====================

export { Url, UrlOptions } from './url';
export { Method } from './method.enum';
export { Enum } from './abstract.enums';
export { ResultVO, I18nDict, isSuccess, isError, extractData } from './result-vo';

