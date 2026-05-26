/**
 * Angular HTTP客户端服务
 * 
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

import { Injectable, inject, DestroyRef } from '@angular/core';
import { environment } from '../environments/environment';
import { HttpClient } from '../../../framework/http-client';
import { Url } from '../../../framework/url';
import { RequestOptions } from '../../../framework/http-client';

/**
 * HTTP客户端服务
 * Angular 17+ Standalone Service
 * 
 * @example
 * ```typescript
 * @Component({
 *   selector: 'app-login',
 *   standalone: true
 * })
 * export class LoginComponent {
 *   private httpClient = inject(HttpClientService);
 *   
 *   async login() {
 *     const result = await this.httpClient.request(AppUrl.USER_LOGIN, {
 *       body: { username: 'user', password: 'pass' }
 *     });
 *   }
 * }
 * ```
 */
@Injectable({
    providedIn: 'root'
})
export class HttpClientService {
    private client: HttpClient;
    private destroyRef = inject(DestroyRef);

    constructor() {
        this.client = new HttpClient({
            baseUrl: environment.gatewayUrl,
            clientId: `angular-app-${Date.now()}`,
            duplicateSubmitTimeWindow: 3000,
            showLoading: true
        });

        // 自动清理资源
        this.destroyRef.onDestroy(() => {
            this.client.cleanup();
        });
    }

    /**
     * 发送HTTP请求
     */
    async request<T = any>(url: Url, options?: RequestOptions): Promise<ResultVO<T>> {
        return await this.client.request<T>(url, options);
    }

    /**
     * 获取缓存的请求头
     */
    getCachedHeaders(): Record<string, string> {
        return this.client.getCachedHeaders();
    }

    /**
     * 清除缓存的请求头
     */
    clearCachedHeaders(): void {
        this.client.clearCachedHeaders();
    }

    /**
     * 预加载加密
     */
    async initializeEncryption(): Promise<void> {
        await this.client.initializeEncryption();
    }

    /**
     * 更新配置
     */
    updateConfig(config: any): void {
        this.client.updateConfig(config);
    }
}

