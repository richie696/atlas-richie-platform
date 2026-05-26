/**
 * React认证Hook
 * 
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

import { useState, useCallback } from 'react';
import { useHttpClient } from './useHttpClient';
import { AppUrl } from '../app.url';
import { ResultVO, isSuccess, extractData } from '../../../framework/http-client';

interface User {
    userId: string;
    username: string;
    email: string;
}

/**
 * 认证Hook
 * 提供登录、登出等认证功能
 * 
 * @example
 * ```tsx
 * function LoginPage() {
 *   const { login, logout, loading, error, user, isLoggedIn } = useAuth();
 *   
 *   const handleLogin = async () => {
 *     await login('username', 'password');
 *   };
 * }
 * ```
 */
export function useAuth() {
    const client = useHttpClient();
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [user, setUser] = useState<User | null>(null);

    const login = useCallback(async (username: string, password: string) => {
        setLoading(true);
        setError(null);
        try {
            const result: ResultVO<User> = await client.request<User>(AppUrl.USER_LOGIN, {
                body: { username, password }
            });
            
            if (isSuccess(result)) {
                const userData = extractData(result);
                setUser(userData);
                // 注意：token现在从响应头自动获取并保存到localStorage，无需手动处理
                return result;
            } else {
                const errorMsg = result.msg || '登录失败';
                setError(errorMsg);
                throw new Error(errorMsg);
            }
        } catch (err: any) {
            setError(err.message || '登录失败');
            throw err;
        } finally {
            setLoading(false);
        }
    }, [client]);

    const logout = useCallback(async () => {
        try {
            const result: ResultVO<void> = await client.request<void>(AppUrl.USER_LOGOUT);
            if (isSuccess(result)) {
                setUser(null);
                // 清除缓存的请求头（包括token）
                client.clearCachedHeaders();
            }
        } catch (err) {
            console.error('登出失败:', err);
        }
    }, [client]);

    const register = useCallback(async (userData: {
        username: string;
        password: string;
        email: string;
    }) => {
        setLoading(true);
        setError(null);
        try {
            const result: ResultVO<User> = await client.request<User>(AppUrl.USER_REGISTER, {
                body: userData
            });
            
            if (isSuccess(result)) {
                return result;
            } else {
                const errorMsg = result.msg || '注册失败';
                setError(errorMsg);
                throw new Error(errorMsg);
            }
        } catch (err: any) {
            if (err.message === 'DUPLICATE_REQUEST' || err.message === 'DUPLICATE_SUBMIT') {
                setError('注册请求已提交，请勿重复操作');
            } else {
                setError(err.message || '注册失败');
            }
            throw err;
        } finally {
            setLoading(false);
        }
    }, [client]);

    // 检查是否已登录（通过检查缓存的请求头中是否有token）
    const isLoggedIn = useCallback(() => {
        const cachedHeaders = client.getCachedHeaders();
        // 检查是否有x-rd-request-apitoken（网关签发的token）
        return !!cachedHeaders['x-rd-request-apitoken'] || !!cachedHeaders['X-Rd-Request-Apitoken'];
    }, [client]);

    return { login, logout, register, loading, error, user, isLoggedIn };
}

