/**
 * 小程序应用入口文件示例
 * 演示如何初始化HTTP客户端
 * 
 * @author richie696
 * @version 1.0
 */

import { HttpClient } from './framework/http-client';

// 创建全局HTTP客户端实例
export const httpClient = new HttpClient({
    baseUrl: 'https://your-gateway.com',  // 替换为实际的网关地址
    clientId: 'my-miniprogram',
    duplicateSubmitTimeWindow: 3000,
    showLoading: true,
    enableHeaderAutoManagement: true
});

App({
    onLaunch() {
        console.log('小程序启动');
        
        // 可选：预加载密钥交换（提升首次加密请求的速度）
        // httpClient.initializeEncryption().catch(err => {
        //     console.error('预加载加密失败:', err);
        // });
    },

    onShow() {
        console.log('小程序显示');
    },

    onHide() {
        console.log('小程序隐藏');
    },

    onError(msg: string) {
        console.error('小程序错误:', msg);
    }
});

