/**
 * Vue认证Composable
 * 
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

import { ref, computed } from 'vue';
import { useHttpClient } from './useHttpClient';
import { AppUrl } from '../app.url';
import { ResultVO, isSuccess, extractData } from '../../../framework/http-client';

interface User {
    userId: string;
    username: string;
    email: string;
}

/**
 * 认证Composable
 * 提供登录、登出等认证功能
 * 
 * @example
 * ```typescript
 * <script setup lang="ts">
 * import { useAuth } from './composables/useAuth';
 * 
 * const { login, logout, loading, error, user, isLoggedIn } = useAuth();
 * 
 * const handleLogin = async () => {
 *   await login('username', 'password');
 * };
 * </script>
 * ```
 */
export function useAuth() {
    const client = useHttpClient();
    
    const loading = ref(false);
    const error = ref<string | null>(null);
    const user = ref<User | null>(null);

    // 检查是否已登录（通过检查缓存的请求头中是否有token）
    const isLoggedIn = computed(() => {
        const cachedHeaders = client.getCachedHeaders();
        // 检查是否有x-rd-request-apitoken（网关签发的token）
        return !!cachedHeaders['x-rd-request-apitoken'] || !!cachedHeaders['X-Rd-Request-Apitoken'];
    });

    /**
     * 用户登录
     */
    const login = async (username: string, password: string) => {
        loading.value = true;
        error.value = null;

        try {
            const result: ResultVO<User> = await client.request<User>(AppUrl.USER_LOGIN, {
                body: { username, password }
            });

            if (isSuccess(result)) {
                const userData = extractData(result);
                user.value = userData;
                // 注意：token现在从响应头自动获取并保存到localStorage，无需手动处理
                return result;
            } else {
                const errorMsg = result.msg || '登录失败';
                error.value = errorMsg;
                throw new Error(errorMsg);
            }
        } catch (err: any) {
            error.value = err.message || '登录失败';
            throw err;
        } finally {
            loading.value = false;
        }
    };

    /**
     * 用户注册
     */
    const register = async (userData: {
        username: string;
        password: string;
        email: string;
    }) => {
        loading.value = true;
        error.value = null;

        try {
            const result: ResultVO<User> = await client.request<User>(AppUrl.USER_REGISTER, {
                body: userData
            });
            
            if (isSuccess(result)) {
                return result;
            } else {
                const errorMsg = result.msg || '注册失败';
                error.value = errorMsg;
                throw new Error(errorMsg);
            }
        } catch (err: any) {
            if (err.message === 'DUPLICATE_REQUEST' || err.message === 'DUPLICATE_SUBMIT') {
                error.value = '注册请求已提交，请勿重复操作';
            } else {
                error.value = err.message || '注册失败';
            }
            throw err;
        } finally {
            loading.value = false;
        }
    };

    /**
     * 用户登出
     */
    const logout = async () => {
        try {
            const result: ResultVO<void> = await client.request<void>(AppUrl.USER_LOGOUT);
            if (isSuccess(result)) {
                user.value = null;
                // 清除缓存的请求头（包括token）
                client.clearCachedHeaders();
            }
        } catch (err) {
            console.error('登出失败:', err);
        }
    };

    return {
        login,
        logout,
        register,
        loading,
        error,
        user,
        isLoggedIn
    };
}

