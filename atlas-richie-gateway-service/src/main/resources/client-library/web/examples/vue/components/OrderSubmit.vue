<!--
  Vue订单提交组件
  演示加密 + 防重复提交功能

  @author richie696
  @version 1.0
  @since 2025-11-01
-->

<script setup lang="ts">
import { ref } from 'vue';
import { useHttpClient } from '../composables/useHttpClient';
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

const props = defineProps<{
    orderData: OrderData;
}>();

const client = useHttpClient();
const submitting = ref(false);
const result = ref<{ type: 'success' | 'error'; message: string } | null>(null);

const handleSubmit = async () => {
    submitting.value = true;
    result.value = null;

    try {
        // 自动加密和防重复提交
        const response: ResultVO<OrderResult> = await client.request<OrderResult>(AppUrl.ORDER_SUBMIT, {
            body: props.orderData
        });

        if (isSuccess(response)) {
            const orderData = extractData(response);
            result.value = {
                type: 'success',
                message: `订单提交成功！订单号: ${orderData.orderId}`
            };
        } else {
            result.value = {
                type: 'error',
                message: response.msg || '订单提交失败'
            };
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

        result.value = {
            type: 'error',
            message: errorMessage
        };
    } finally {
        submitting.value = false;
    }
};
</script>

<template>
    <div class="order-submit">
        <h3>订单信息</h3>
        <div class="order-summary">
            <p>商品数量: {{ orderData.items.length }}
            <p>总金额: ¥{{ orderData.totalAmount }}
            <p>收货地址: {{ orderData.address }}
        </div>

        <button
            @click="handleSubmit"
            :disabled="submitting"
            class="submit-button"
        >
            {{ submitting ? '提交中...' : '提交订单' }}
        </button>

        <div
            v-if="result"
            :class="['result-message', result.type]"
        >
            {{ result.message }}
        </div>
    </div>
</template>

<style scoped>
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
    background-color: #67c23a;
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
    background-color: #f0f9ff;
    color: #67c23a;
    border: 1px solid #67c23a;
}

.result-message.error {
    background-color: #fef0f0;
    color: #f56c6c;
    border: 1px solid #f56c6c;
}
</style>

