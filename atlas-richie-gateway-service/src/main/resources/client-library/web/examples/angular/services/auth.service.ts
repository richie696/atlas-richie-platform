/**
 * Angular认证服务
 * 
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

import { Injectable, inject, signal } from '@angular/core';
import { HttpClientService } from './http-client.service';
import { AppUrl } from '../app.url';
import { ResultVO, isSuccess, extractData } from '../../../framework/http-client';

interface User {
    userId: string;
    username: string;
    email: string;
}

/**
 * 认证服务
 * 使用Angular 17+ Signals实现响应式状态管理
 * 
 * @example
 * ```typescript
 * @Component({
 *   selector: 'app-login',
 *   standalone: true
 * })
 * export class LoginComponent {
 *   private authService = inject(AuthService);
 *   
 *   loading = this.authService.loading;
 *   error = this.authService.error;
 *   
 *   async login() {
 *     await this.authService.login('username', 'password');
 *   }
 * }
 * ```
 */
@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private httpClient = inject(HttpClientService);

    // Signals for reactive state
    loading = signal(false);
    error = signal<string | null>(null);
    user = signal<User | null>(null);

    /**
     * 用户登录
     */
    async login(username: string, password: string): Promise<ResultVO<User>> {
        this.loading.set(true);
        this.error.set(null);

        try {
            const result: ResultVO<User> = await this.httpClient.request<User>(AppUrl.USER_LOGIN, {
                body: { username, password }
            });

            if (isSuccess(result)) {
                const userData = extractData(result);
                this.user.set(userData);
                // 注意：token现在从响应头自动获取并保存到localStorage，无需手动处理
                return result;
            } else {
                const errorMsg = result.msg || '登录失败';
                this.error.set(errorMsg);
                throw new Error(errorMsg);
            }
        } catch (err: any) {
            this.error.set(err.message || '登录失败');
            throw err;
        } finally {
            this.loading.set(false);
        }
    }

    /**
     * 用户注册
     */
    async register(userData: {
        username: string;
        password: string;
        email: string;
    }): Promise<ResultVO<User>> {
        this.loading.set(true);
        this.error.set(null);

        try {
            const result: ResultVO<User> = await this.httpClient.request<User>(AppUrl.USER_REGISTER, {
                body: userData
            });
            
            if (isSuccess(result)) {
                return result;
            } else {
                const errorMsg = result.msg || '注册失败';
                this.error.set(errorMsg);
                throw new Error(errorMsg);
            }
        } catch (err: any) {
            if (err.message === 'DUPLICATE_REQUEST' || err.message === 'DUPLICATE_SUBMIT') {
                this.error.set('注册请求已提交，请勿重复操作');
            } else {
                this.error.set(err.message || '注册失败');
            }
            throw err;
        } finally {
            this.loading.set(false);
        }
    }

    /**
     * 用户登出
     */
    async logout(): Promise<void> {
        try {
            const result: ResultVO<void> = await this.httpClient.request<void>(AppUrl.USER_LOGOUT);
            if (isSuccess(result)) {
                this.user.set(null);
                // 清除缓存的请求头（包括token）
                this.httpClient.clearCachedHeaders();
            }
        } catch (err) {
            console.error('登出失败:', err);
        }
    }

    /**
     * 检查登录状态
     */
    isLoggedIn(): boolean {
        const cachedHeaders = this.httpClient.getCachedHeaders();
        // 检查是否有x-rd-request-apitoken（网关签发的token）
        return !!cachedHeaders['x-rd-request-apitoken'] || !!cachedHeaders['X-Rd-Request-Apitoken'];
    }
}

