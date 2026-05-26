/**
 * React HTTP客户端Hook
 * 
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

import { useMemo, useEffect } from 'react';
import { HttpClient, HttpClientConfig } from '../../../framework/http-client';

/**
 * 使用HTTP客户端的Hook
 * 自动管理客户端生命周期
 * 
 * @param config 客户端配置
 * @returns HTTP客户端实例
 * 
 * @example
 * ```tsx
 * function MyComponent() {
 *   const client = useHttpClient();
 *   
 *   const handleLogin = async () => {
 *     const result = await client.request(AppUrl.USER_LOGIN, {
 *       body: { username: 'user', password: 'pass' }
 *     });
 *   };
 * }
 * ```
 */
export function useHttpClient(config?: HttpClientConfig) {
    const client = useMemo(() => {
        return new HttpClient(config || {
            baseUrl: process.env.REACT_APP_GATEWAY_URL || 'https://your-gateway.com',
            clientId: `react-app-${Date.now()}`,
            duplicateSubmitTimeWindow: 3000,
            showLoading: true
        });
    }, [config]);

    // 组件卸载时清理资源
    useEffect(() => {
        return () => {
            client.cleanup();
        };
    }, [client]);

    return client;
}

