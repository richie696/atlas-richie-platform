/**
 * 支付页面示例
 * 演示如何使用HTTP客户端进行加密支付（最高安全级别）
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
    duplicateSubmitTimeWindow: 5000, // 支付操作使用更长的时间窗口
    showLoading: true
});

Page({
    data: {
        amount: 0,
        orderId: ''
    },

    onLoad(options: any) {
        if (options.orderId) {
            this.setData({
                orderId: options.orderId
            });
        }
    },

    /**
     * 输入金额
     */
    onAmountInput(e: any) {
        this.setData({
            amount: parseFloat(e.detail.value) || 0
        });
    },

    /**
     * 创建支付订单
     */
    async handleCreatePayment() {
        const { amount, orderId } = this.data;

        if (!amount || amount <= 0) {
            wx.showToast({
                title: '请输入有效的支付金额',
                icon: 'none'
            });
            return;
        }

        try {
            // 发送支付请求（自动加密 + 防重复提交）
            const result = await client.request<{ paymentId: string; payUrl: string }>(
                AppUrl.PAYMENT_CREATE,
                {
                    body: {
                        orderId: orderId,
                        amount: amount,
                        currency: 'CNY'
                    }
                }
            );

            // 检查是否成功
            if (isSuccess(result)) {
                const data = extractData(result);
                
                wx.showToast({
                    title: '支付订单创建成功',
                    icon: 'success'
                });

                // 跳转到支付页面或调用支付API
                console.log('支付ID:', data.paymentId);
                console.log('支付URL:', data.payUrl);
            } else {
                wx.showToast({
                    title: result.msg || '创建支付订单失败',
                    icon: 'none'
                });
            }
        } catch (error: any) {
            console.error('创建支付订单失败:', error);
            
            if (error.message === 'DUPLICATE_REQUEST') {
                wx.showToast({
                    title: '请求正在处理中，请勿重复提交',
                    icon: 'none'
                });
            } else if (error.message === 'DUPLICATE_SUBMIT') {
                wx.showToast({
                    title: '服务器检测到重复提交，请稍后再试',
                    icon: 'none'
                });
            } else {
                wx.showToast({
                    title: '创建支付订单失败，请稍后重试',
                    icon: 'none'
                });
            }
        }
    }
});

