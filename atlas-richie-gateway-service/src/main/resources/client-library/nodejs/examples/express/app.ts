/**
 * Express.js 框架集成示例
 * 展示如何在Express应用中使用HTTP客户端
 *
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

import express, { Request, Response, NextFunction } from 'express';
import { HttpClient } from '../../framework/http-client';
import { AppUrl } from '../app.url';
import { ResultVO, isSuccess, extractData } from '../../framework/http-client';

// 创建Express应用
const app = express();
app.use(express.json());

// 创建HTTP客户端（单例模式）
const httpClient = new HttpClient({
    baseUrl: process.env.GATEWAY_URL || 'https://your-gateway.com',
    clientId: process.env.CLIENT_ID || 'express-app',
    duplicateSubmitTimeWindow: 3000,
    timeout: 30000
});

// ========== 示例1: 作为中间件 ==========
/**
 * HTTP客户端中间件
 * 将客户端实例添加到请求对象中
 */
export function httpClientMiddleware(req: Request, res: Response, next: NextFunction) {
    (req as any).httpClient = httpClient;
    next();
}

// ========== 示例2: 创建服务类 ==========
/**
 * API服务类
 * 封装业务逻辑的HTTP请求
 */
export class ApiService {
    constructor(private client: HttpClient) {}

    /**
     * 用户登录
     */
    async login<T = any>(username: string, password: string): Promise<ResultVO<T>> {
        return await this.client.request<T>(AppUrl.USER_LOGIN, {
            body: { username, password }
        });
    }

    /**
     * 提交订单
     */
    async submitOrder<T = any>(orderData: any): Promise<ResultVO<T>> {
        return await this.client.request<T>(AppUrl.ORDER_SUBMIT, {
            body: orderData
        });
    }

    /**
     * 获取菜单列表
     */
    async getMenuList<T = any>(): Promise<ResultVO<T>> {
        return await this.client.request<T>(AppUrl.MENU_ALL);
    }

    /**
     * 获取用户信息
     */
    async getUserProfile<T = any>(): Promise<ResultVO<T>> {
        // 注意：token现在从响应头自动获取，无需手动传入
        return await this.client.request<T>(AppUrl.USER_PROFILE);
    }
}

// 创建服务实例
const apiService = new ApiService(httpClient);

// ========== 示例3: Express路由 ==========

/**
 * 登录路由
 * POST /api/login
 */
app.post('/api/login', async (req: Request, res: Response) => {
    try {
        const { username, password } = req.body;
        
        interface User {
            userId: string;
            username: string;
            email: string;
        }
        
        const result = await apiService.login<User>(username, password);
        
        if (isSuccess(result)) {
            const userData = extractData(result);
            // 注意：token现在从响应头自动获取并保存，无需手动处理
            res.json({
                success: true,
                data: userData
            });
        } else {
            res.status(400).json({
                success: false,
                error: result.msg || '登录失败'
            });
        }
    } catch (error: any) {
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * 订单提交路由
 * POST /api/orders
 */
app.post('/api/orders', async (req: Request, res: Response) => {
    try {
        const orderData = req.body;
        
        interface OrderResult {
            orderId: string;
            totalAmount: number;
        }
        
        const result = await apiService.submitOrder<OrderResult>(orderData);
        
        if (isSuccess(result)) {
            const orderData = extractData(result);
            res.json({
                success: true,
                data: orderData
            });
        } else {
            res.status(400).json({
                success: false,
                error: result.msg || '订单提交失败'
            });
        }
    } catch (error: any) {
        if (error.message === 'DUPLICATE_REQUEST' || error.message === 'DUPLICATE_SUBMIT') {
            res.status(429).json({
                success: false,
                error: '重复提交，请稍后再试'
            });
        } else {
            res.status(500).json({
                success: false,
                error: error.message
            });
        }
    }
});

/**
 * 菜单列表路由
 * GET /api/menu
 */
app.get('/api/menu', async (req: Request, res: Response) => {
    try {
        interface Menu {
            id: string;
            name: string;
            items: any[];
        }
        
        const result = await apiService.getMenuList<Menu[]>();
        
        if (isSuccess(result)) {
            const menuData = extractData(result);
            res.json({
                success: true,
                data: menuData
            });
        } else {
            res.status(400).json({
                success: false,
                error: result.msg || '获取菜单失败'
            });
        }
    } catch (error: any) {
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * 用户信息路由
 * GET /api/user/profile
 */
app.get('/api/user/profile', httpClientMiddleware, async (req: Request, res: Response) => {
    try {
        interface User {
            userId: string;
            username: string;
            email: string;
        }
        
        // 注意：token现在从响应头自动获取，无需手动传入
        const result = await apiService.getUserProfile<User>();
        
        if (isSuccess(result)) {
            const userData = extractData(result);
            res.json({
                success: true,
                data: userData
            });
        } else {
            res.status(400).json({
                success: false,
                error: result.msg || '获取用户信息失败'
            });
        }
    } catch (error: any) {
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * 使用中间件访问客户端的路由示例
 */
app.post('/api/custom', httpClientMiddleware, async (req: Request, res: Response) => {
    try {
        const client = (req as any).httpClient as HttpClient;
        
        // 直接使用客户端发送请求
        const result = await client.request(AppUrl.USER_PROFILE, {
            headers: {
                'X-Custom-Header': 'custom-value'
            }
        });
        
        res.json({
            success: true,
            data: result
        });
    } catch (error: any) {
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// ========== 启动服务器 ==========
const PORT = process.env.PORT || 3000;

app.listen(PORT, () => {
    console.log(`Express服务器运行在端口 ${PORT}`);
    console.log('示例路由:');
    console.log('  POST /api/login');
    console.log('  POST /api/orders');
    console.log('  GET  /api/menu');
    console.log('  GET  /api/user/profile');
    console.log('  POST /api/custom');
});

// 优雅关闭
process.on('SIGTERM', () => {
    console.log('收到SIGTERM信号，正在关闭...');
    httpClient.cleanup();
    process.exit(0);
});

export { app, httpClient, apiService };

