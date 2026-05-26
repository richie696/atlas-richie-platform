/**
 * 登录页面示例
 * 演示如何使用HTTP客户端进行加密登录
 * 
 * @author richie696
 * @version 1.0
 */

import { HttpClient } from '../../framework/http-client';
import { AppUrl } from '../app.url';
import { isSuccess, extractData } from '../../framework/http-client';

// 创建HTTP客户端实例（建议在app.ts中创建全局实例）
const client = new HttpClient({
    baseUrl: 'https://your-gateway.com',
    clientId: 'my-miniprogram',
    duplicateSubmitTimeWindow: 3000,
    showLoading: true
});

Page({
    data: {
        username: '',
        password: ''
    },

    onLoad() {
        console.log('登录页面加载');
    },

    /**
     * 输入用户名
     */
    onUsernameInput(e: any) {
        this.setData({
            username: e.detail.value
        });
    },

    /**
     * 输入密码
     */
    onPasswordInput(e: any) {
        this.setData({
            password: e.detail.value
        });
    },

    /**
     * 提交登录
     */
    async handleLogin() {
        const { username, password } = this.data;

        if (!username || !password) {
            wx.showToast({
                title: '请输入用户名和密码',
                icon: 'none'
            });
            return;
        }

        try {
            // 发送登录请求（自动加密）
            const result = await client.request<{ token: string; userId: string }>(
                AppUrl.USER_LOGIN,
                {
                    body: {
                        username: username,
                        password: password
                    }
                }
            );

            // 检查是否成功
            if (isSuccess(result)) {
                const data = extractData(result);
                
                // 保存token和userId到storage
                localStorage.setItem('token', data.token);
                localStorage.setItem('userId', data.userId);

                wx.showToast({
                    title: '登录成功',
                    icon: 'success'
                });

                // 跳转到首页
                setTimeout(() => {
                    wx.switchTab({
                        url: AppUrl.PAGE_HOME.value()
                    });
                }, 1500);
            } else {
                wx.showToast({
                    title: result.msg || '登录失败',
                    icon: 'none'
                });
            }
        } catch (error: any) {
            console.error('登录失败:', error);
            
            if (error.message === 'DUPLICATE_REQUEST') {
                wx.showToast({
                    title: '请求正在处理中，请勿重复提交',
                    icon: 'none'
                });
            } else {
                wx.showToast({
                    title: '登录失败，请稍后重试',
                    icon: 'none'
                });
            }
        }
    }
});

