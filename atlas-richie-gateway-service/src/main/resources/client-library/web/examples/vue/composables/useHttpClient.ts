/**
 * Vue HTTP客户端Composable
 * 
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

import { onUnmounted } from 'vue';
import { HttpClient, HttpClientConfig } from '../../../framework/http-client';

let clientInstance: HttpClient | null = null;

/**
 * 使用HTTP客户端的Composable
 * 自动管理客户端生命周期
 * 
 * @param config 客户端配置
 * @returns HTTP客户端实例
 * 
 * @example
 * ```typescript
 * <script setup lang="ts">
 * import { useHttpClient } from './composables/useHttpClient';
 * import { AppUrl } from './app.url';
 * 
 * const client = useHttpClient();
 * 
 * const handleLogin = async () => {
 *   const result = await client.request(AppUrl.USER_LOGIN, {
 *     body: { username: 'user', password: 'pass' }
 *   });
 * };
 * </script>
 * ```
 */
export function useHttpClient(config?: HttpClientConfig) {
    if (!clientInstance) {
        clientInstance = new HttpClient(config || {
            baseUrl: import.meta.env.VITE_GATEWAY_URL || 'https://your-gateway.com',
            clientId: `vue-app-${Date.now()}`,
            duplicateSubmitTimeWindow: 3000,
            showLoading: true
        });
    }

    // 组件卸载时清理资源
    onUnmounted(() => {
        if (clientInstance) {
            clientInstance.cleanup();
            clientInstance = null;
        }
    });

    return clientInstance;
}

