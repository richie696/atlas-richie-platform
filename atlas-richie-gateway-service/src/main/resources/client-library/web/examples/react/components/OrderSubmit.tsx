/**
 * React订单提交组件
 * 演示加密 + 防重复提交功能
 * 
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

import React, { useState } from 'react';
import { useHttpClient } from '../hooks/useHttpClient';
import { AppUrl } from '../app.url';
import { ResultVO, isSuccess, extractData } from '../../../framework/http-client';

interface OrderData {
    items: Array<{ productId: string; quantity: number; price: number }>;
    totalAmount: number;
    address: string;
}

interface OrderResult {
    orderId: string;
    totalAmount: number;
}

/**
 * 订单提交组件
 * 自动处理加密和防重复提交
 */
export function OrderSubmit({ orderData }: { orderData: OrderData }) {
    const client = useHttpClient();
    const [submitting, setSubmitting] = useState(false);
    const [result, setResult] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

    const handleSubmit = async () => {
        setSubmitting(true);
        setResult(null);

        try {
            // 自动加密和防重复提交
            const response: ResultVO<OrderResult> = await client.request<OrderResult>(AppUrl.ORDER_SUBMIT, {
                body: orderData
            });

            if (isSuccess(response)) {
                const orderData = extractData(response);
                setResult({
                    type: 'success',
                    message: `订单提交成功！订单号: ${orderData.orderId}`
                });
            } else {
                setResult({
                    type: 'error',
                    message: response.msg || '订单提交失败'
                });
            }
        } catch (err: any) {
            let errorMessage = '订单提交失败';
            
            if (err.message === 'DUPLICATE_REQUEST') {
                errorMessage = '订单正在处理中，请勿重复提交';
            } else if (err.message === 'DUPLICATE_SUBMIT') {
                errorMessage = '服务器检测到重复提交，订单可能已创建';
            } else {
                errorMessage = err.message || '订单提交失败';
            }

            setResult({
                type: 'error',
                message: errorMessage
            });
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="order-submit">
            <h3>订单信息</h3>
            <div className="order-summary">
                <p>商品数量: {orderData.items.length}
                <p>总金额: ¥{orderData.totalAmount}
                <p>收货地址: {orderData.address}
            </div>

            <button 
                onClick={handleSubmit} 
                disabled={submitting}
                className="submit-button"
            >
                {submitting ? '提交中...' : '提交订单'}
            </button>

            {result && (
                <div className={`result-message ${result.type}`}>
                    {result.message}
                </div>
            )}
        </div>
    );
}

export default OrderSubmit;

