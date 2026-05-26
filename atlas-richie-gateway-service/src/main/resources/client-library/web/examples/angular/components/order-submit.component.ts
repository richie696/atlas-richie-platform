/**
 * Angular订单提交组件
 * 演示加密 + 防重复提交功能
 * 
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

import { Component, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClientService } from '../services/http-client.service';
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
@Component({
    selector: 'app-order-submit',
    standalone: true,
    imports: [CommonModule],
    template: `
        <div class="order-submit">
            <h3>订单信息</h3>
            <div class="order-summary">
                <p>商品数量: {{ orderData().items.length }}
                <p>总金额: ¥{{ orderData().totalAmount }}
                <p>收货地址: {{ orderData().address }}
            </div>

            <button 
                (click)="handleSubmit()"
                [disabled]="submitting()"
                class="submit-button"
            >
                {{ submitting() ? '提交中...' : '提交订单' }}
            </button>

            @if (result()) {
                <div [class]="'result-message ' + result()!.type">
                    {{ result()!.message }}
                </div>
            }
        </div>
    `,
    styles: [`
        .order-submit {
            padding: 20px;
            border: 1px solid #ddd;
            border-radius: 8px;
        }

        .order-summary {
            background-color: #f5f5f5;
            padding: 15px;
            margin-bottom: 15px;
            border-radius: 4px;
        }

        .submit-button {
            width: 100%;
            padding: 10px;
            background-color: #28a745;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
        }

        .submit-button:disabled {
            background-color: #ccc;
            cursor: not-allowed;
        }

        .result-message {
            margin-top: 15px;
            padding: 10px;
            border-radius: 4px;
        }

        .result-message.success {
            background-color: #d4edda;
            color: #155724;
        }

        .result-message.error {
            background-color: #f8d7da;
            color: #721c24;
        }
    `]
})
export class OrderSubmitComponent {
    private httpClient = inject(HttpClientService);

    orderData = input.required<OrderData>();
    submitting = signal(false);
    result = signal<{ type: string; message: string } | null>(null);

    async handleSubmit() {
        this.submitting.set(true);
        this.result.set(null);

        try {
            // 自动加密和防重复提交
            const response: ResultVO<OrderResult> = await this.httpClient.request<OrderResult>(AppUrl.ORDER_SUBMIT, {
                body: this.orderData()
            });

            if (isSuccess(response)) {
                const orderData = extractData(response);
                this.result.set({
                    type: 'success',
                    message: `订单提交成功！订单号: ${orderData.orderId}`
                });
            } else {
                this.result.set({
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

            this.result.set({
                type: 'error',
                message: errorMessage
            });
        } finally {
            this.submitting.set(false);
        }
    }
}

