/**
 * Node.js HTTP客户端使用示例
 *
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

import { HttpClient } from '../framework/http-client';
import { AppUrl } from './app.url';
import { ResultVO, isSuccess, extractData } from '../framework/http-client';

interface User {
    userId: string;
    username: string;
    email: string;
}

async function main() {
    // 创建HTTP客户端
    const client = new HttpClient({
        baseUrl: 'https://your-gateway.com',
        clientId: 'nodejs-app-client',
        userId: 'user123',
        duplicateSubmitTimeWindow: 3000,
        timeout: 30000
    });

    try {
        // ========== 示例1: 用户登录（加密，不防重复）==========
        console.log('\n=== 示例1: 用户登录 ===');
        
        const loginResult: ResultVO<User> = await client.request<User>(AppUrl.USER_LOGIN, {
            body: {
                username: 'testuser',
                password: 'testpass'
            }
        });
        
        if (isSuccess(loginResult)) {
            const userData = extractData(loginResult);
            console.log('登录成功:', userData);
            // 注意：token现在从响应头自动获取并保存到内存存储，无需手动处理
        } else {
            console.error('登录失败:', loginResult.msg);
        }

        // ========== 示例2: 订单提交（加密 + 防重复）==========
        console.log('\n=== 示例2: 订单提交 ===');
        
        try {
            interface OrderResult {
                orderId: string;
                totalAmount: number;
            }
            
            const orderResult: ResultVO<OrderResult> = await client.request<OrderResult>(AppUrl.ORDER_SUBMIT, {
                body: {
                    items: [
                        { productId: 'P001', quantity: 2, price: 99.99 }
                    ],
                    totalAmount: 199.98,
                    address: '北京市朝阳区xxx路xxx号'
                }
            });
            
            if (isSuccess(orderResult)) {
                const orderData = extractData(orderResult);
                console.log('订单提交成功:', orderData);
            } else {
                console.error('订单提交失败:', orderResult.msg);
            }
        } catch (error: any) {
            if (error.message === 'DUPLICATE_REQUEST') {
                console.log('检测到重复提交，请求被拒绝');
            } else if (error.message === 'DUPLICATE_SUBMIT') {
                console.log('服务器检测到重复提交');
            } else {
                console.error('订单提交失败:', error);
            }
        }

        // ========== 示例3: 获取菜单（公开数据）==========
        console.log('\n=== 示例3: 获取菜单列表 ===');
        
        interface Menu {
            id: string;
            name: string;
            items: any[];
        }
        
        const menuResult: ResultVO<Menu[]> = await client.request<Menu[]>(AppUrl.MENU_ALL);
        if (isSuccess(menuResult)) {
            const menuData = extractData(menuResult);
            console.log('菜单列表:', menuData);
        } else {
            console.error('获取菜单失败:', menuResult.msg);
        }

        // ========== 示例4: 获取用户信息（加密）==========
        console.log('\n=== 示例4: 获取用户信息 ===');
        
        const profileResult: ResultVO<User> = await client.request<User>(AppUrl.USER_PROFILE);
        if (isSuccess(profileResult)) {
            const userData = extractData(profileResult);
            console.log('用户信息:', userData);
        } else {
            console.error('获取用户信息失败:', profileResult.msg);
        }

    } catch (error) {
        console.error('请求失败:', error);
    } finally {
        // 清理资源
        client.cleanup();
    }
}

// 运行示例
if (require.main === module) {
    main().catch(console.error);
}

export { main };

