/**
 * HTTP客户端库 - 支持ECC加密和防重复提交功能
 * 适配Java风格的URL枚举系统
 * 适用于Capacitor + Electron的混合应用
 * 支持Android、iOS、Windows、Linux和macOS
 *
 * @author richie696
 * @version 3.0
 * @since 2025-11-01
 * @description 提供HTTP客户端，支持ECC+AES-GCM加密和防重复提交功能
 */

import { Url } from './url';
import { Method } from './method.enum';
import { ResultVO } from './result-vo';
import { getOrCreateDeviceId } from './device-id';
import { generateHardwareFingerprint, fingerprintToString } from './device-fingerprint';

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
    /** 是否启用请求头自动管理（从localStorage读取和保存），默认true */
    enableHeaderAutoManagement?: boolean;
    /** localStorage中存储请求头的键名，默认'http_headers' */
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
 */
class EccCryptoModule {
    private readonly algorithm: EcKeyGenParams;
    private keyPair: CryptoKeyPair | null;
    private sharedKey: CryptoKey | null;
    private gatewayPublicKey: CryptoKey | null;
    public gatewayKeyId: string | null;

    constructor() {
        this.algorithm = {
            name: 'ECDH',
            namedCurve: 'P-256'
        };
        this.keyPair = null;
        this.sharedKey = null;
        this.gatewayPublicKey = null;
        this.gatewayKeyId = null;
    }

    async generateKeyPair(): Promise<CryptoKeyPair> {
        try {
            this.keyPair = await window.crypto.subtle.generateKey(
                this.algorithm,
                true,
                ['deriveKey', 'deriveBits']
            );
            console.log('[ECC] 密钥对生成成功');
            return this.keyPair;
        } catch (error) {
            console.error('[ECC] 生成密钥对失败:', error);
            throw error;
        }
    }

    async exportPublicKey(publicKey: CryptoKey): Promise<string> {
        try {
            const exported = await window.crypto.subtle.exportKey('spki', publicKey);
            return btoa(String.fromCharCode(...new Uint8Array(exported)));
        } catch (error) {
            console.error('[ECC] 导出公钥失败:', error);
            throw error;
        }
    }

    async importPublicKey(base64PublicKey: string): Promise<CryptoKey> {
        try {
            const binaryString = atob(base64PublicKey);
            const bytes = new Uint8Array(binaryString.length);
            for (let i = 0; i < binaryString.length; i++) {
                bytes[i] = binaryString.charCodeAt(i);
            }
            return await window.crypto.subtle.importKey(
                'spki',
                bytes,
                this.algorithm,
                true,
                []
            );
        } catch (error) {
            console.error('[ECC] 导入公钥失败:', error);
            throw error;
        }
    }

    async generateSharedKey(remotePublicKey: CryptoKey): Promise<CryptoKey> {
        if (!this.keyPair) throw new Error('本地密钥对未生成');
        try {
            this.sharedKey = await window.crypto.subtle.deriveKey(
                {
                    name: 'ECDH',
                    public: remotePublicKey
                },
                this.keyPair.privateKey,
                {
                    name: 'AES-GCM',
                    length: 256
                },
                true,
                ['encrypt', 'decrypt']
            );
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
            const iv = window.crypto.getRandomValues(new Uint8Array(12));
            const encodedData = new TextEncoder().encode(data);
            const encryptedData = await window.crypto.subtle.encrypt(
                {
                    name: 'AES-GCM',
                    iv: iv
                },
                this.sharedKey,
                encodedData
            );
            const combined = new Uint8Array(iv.length + encryptedData.byteLength);
            combined.set(iv);
            combined.set(new Uint8Array(encryptedData), iv.length);
            return btoa(String.fromCharCode(...combined));
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
            const combined = new Uint8Array(atob(encryptedData).split('').map(char => char.charCodeAt(0)));
            const iv = combined.slice(0, 12);
            const data = combined.slice(12);
            const decryptedData = await window.crypto.subtle.decrypt(
                {
                    name: 'AES-GCM',
                    iv: iv
                },
                this.sharedKey,
                data
            );
            return new TextDecoder().decode(decryptedData);
        } catch (error) {
            console.error('[ECC] 解密失败:', error);
            throw error;
        }
    }

    async exchangeKeys(baseUrl: string, clientId: string): Promise<boolean> {
        try {
            await this.generateKeyPair();
            const clientPublicKey = await this.exportPublicKey(this.keyPair!.publicKey);
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
 *
 * @example
 * ```typescript
 * // 1. 创建客户端实例
 * const client = new HttpClient({
 *   baseUrl: 'https://your-gateway.com',
 *   clientId: 'my-client-001'
 * });
 *
 * // 2. 发送请求（自动根据URL枚举的配置处理）
 * const response = await client.request(AppUrl.PAYMENT_CREATE, {
 *   body: { amount: 100, currency: 'CNY' }
 * });
 * ```
 */
export class HttpClient {
    private config: Required<HttpClientConfig>;
    private eccModule: EccCryptoModule;
    private duplicateModule: DuplicateSubmitModule;
    public readonly clientId: string;
    private readonly headerStorageKey: string;
    
    // 系统内置的请求头，这些不应该被保存到localStorage
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
            let response: Response;
            if (url.needEncryption) {
                response = await this.sendEncryptedRequest(fullUrl, method, options, options.allowRetry !== false);
            } else {
                response = await this.sendPlainRequest(fullUrl, method, options);
            }

            // 4. 自动保存响应头到localStorage（在解析响应体之前，确保响应头可用）
            if (this.config.enableHeaderAutoManagement) {
                this.saveResponseHeaders(response);
            }
            
            // 5. 处理响应（完整透传ResultVO<T>，不做任何处理）
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

        const response = await fetch(url, {
            method: method,
            headers: headers,
            body: requestBody,
            signal: AbortSignal.timeout(this.config.timeout)
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
    }

    private async sendPlainRequest(
        url: string,
        method: string,
        options: RequestOptions
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

        const userId = this.getUserId();
        if (userId) {
            headers['X-User-Id'] = userId;
        }

        let requestBody: string | null = null;
        if (options.body && method !== Method.GET) {
            requestBody = JSON.stringify(options.body);
        }

        return await fetch(url, {
            method: method,
            headers: headers,
            body: requestBody,
            signal: AbortSignal.timeout(this.config.timeout)
        });
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
                    this.showErrorMessage('请求过于频繁，请稍后再试');
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

    public getUserId(): string | null {
        if (typeof window !== 'undefined') {
            return localStorage.getItem('userId') || sessionStorage.getItem('userId');
        }
        return null;
    }

    /**
     * 构建设备相关请求头
     * <p>
     * 自动生成并附加以下请求头：
     * - X-Device-Id：设备ID（基于浏览器指纹生成并持久化）
     * - X-Hardware-Fingerprint：硬件指纹（每次请求动态生成）
     *
     * @returns 设备相关请求头对象
     */
    private async buildDeviceHeaders(): Promise<Record<string, string>> {
        if (typeof window === 'undefined') {
            // 非浏览器环境（如Node.js SSR）不生成设备指纹
            return {};
        }

        const headers: Record<string, string> = {};

        try {
            const deviceId = await getOrCreateDeviceId();
            if (deviceId) {
                headers['X-Device-Id'] = deviceId;
            }
        } catch (e) {
            // 忽略设备ID生成失败，避免影响主流程
            console.warn('[HttpClient] 生成设备ID失败:', e);
        }

        try {
            const fingerprint = await generateHardwareFingerprint();
            const fingerprintJson = fingerprintToString(fingerprint);
            if (fingerprintJson) {
                headers['X-Hardware-Fingerprint'] = fingerprintJson;
            }
        } catch (e) {
            // 忽略硬件指纹生成失败，避免影响主流程
            console.warn('[HttpClient] 生成硬件指纹失败:', e);
        }

        return headers;
    }

    /**
     * 从localStorage读取缓存的请求头
     * 返回一个对象，包含所有缓存的业务请求头
     */
    private loadCachedHeaders(): Record<string, string> {
        if (typeof window === 'undefined') {
            return {};
        }

        try {
            const cached = localStorage.getItem(this.headerStorageKey);
            if (!cached) {
                return {};
            }

            const headers = JSON.parse(cached);
            return headers || {};
        } catch (error) {
            console.warn('[HttpClient] 读取缓存的请求头失败:', error);
            return {};
        }
    }

    /**
     * 保存响应头到localStorage
     * 只保存业务相关的请求头，排除系统内置的header
     * 网关签发的token（x-rd-request-apitoken）等业务请求头会自动保存
     */
    private saveResponseHeaders(response: Response): void {
        if (typeof window === 'undefined') {
            return;
        }

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

            // 如果有业务请求头，保存到localStorage
            if (Object.keys(businessHeaders).length > 0) {
                // 读取现有的缓存
                const existingHeaders = this.loadCachedHeaders();
                
                // 合并新的响应头（响应头的值会覆盖现有的值）
                const mergedHeaders = {
                    ...existingHeaders,
                    ...businessHeaders
                };

                localStorage.setItem(this.headerStorageKey, JSON.stringify(mergedHeaders));
                console.log('[HttpClient] 已保存响应头到localStorage:', businessHeaders);
            }
        } catch (error) {
            console.warn('[HttpClient] 保存响应头到localStorage失败:', error);
        }
    }

    /**
     * 手动设置请求头到缓存
     * 这些请求头会在下次请求时自动添加到请求headers中
     * 
     * @param headers 要缓存的请求头
     */
    public setCachedHeaders(headers: Record<string, string>): void {
        if (typeof window === 'undefined') {
            return;
        }

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
        if (typeof window !== 'undefined') {
            localStorage.removeItem(this.headerStorageKey);
            console.log('[HttpClient] 已清除所有缓存的请求头');
        }
    }

    /**
     * 移除指定的缓存请求头
     * 
     * @param headerName 要移除的请求头名称
     */
    public removeCachedHeader(headerName: string): void {
        if (typeof window === 'undefined') {
            return;
        }

        try {
            const existingHeaders = this.loadCachedHeaders();
            delete existingHeaders[headerName];
            localStorage.setItem(this.headerStorageKey, JSON.stringify(existingHeaders));
            console.log('[HttpClient] 已移除缓存的请求头:', headerName);
        } catch (error) {
            console.warn('[HttpClient] 移除缓存的请求头失败:', error);
        }
    }

    private showLoadingState(): void {
        if (typeof window !== 'undefined' && (window as any).Capacitor && (window as any).Capacitor.Plugins) {
            (window as any).Capacitor.Plugins.Loading.show({
                message: '处理中...'
            });
        } else if (typeof window !== 'undefined') {
            const loading = document.getElementById('loading');
            if (loading) {
                loading.style.display = 'block';
            }
        }
    }

    private hideLoadingState(): void {
        if (typeof window !== 'undefined' && (window as any).Capacitor && (window as any).Capacitor.Plugins) {
            (window as any).Capacitor.Plugins.Loading.hide();
        } else if (typeof window !== 'undefined') {
            const loading = document.getElementById('loading');
            if (loading) {
                loading.style.display = 'none';
            }
        }
    }

    private showErrorMessage(message: string): void {
        if (typeof window !== 'undefined' && (window as any).Capacitor && (window as any).Capacitor.Plugins) {
            (window as any).Capacitor.Plugins.Toast.show({
                text: message,
                duration: 'short',
                position: 'center'
            });
        } else if (typeof window !== 'undefined') {
            alert(message);
        }
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
 * 可以直接使用，也可以创建自定义实例
 */
export const httpClient = new HttpClient();

// 导出类型和枚举
export { Url, UrlOptions } from './url';
export { Method } from './method.enum';
export { Enum } from './abstract.enums';
export { ResultVO, I18nDict, isSuccess, isError, extractData } from './result-vo';

