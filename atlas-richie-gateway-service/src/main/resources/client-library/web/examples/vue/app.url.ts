/**
 * Vue应用URL配置
 * 定义所有的API端点和页面路由
 * 
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

import { Url, Method } from '../../framework/url';

/**
 * Vue应用URL枚举类
 */
export class AppUrl {
    // ==================== 页面路由 ====================
    
    public static readonly PAGE_HOME = new Url(
        'PAGE_HOME',
        '/',
        Method.NAVIGATOR
    );

    public static readonly PAGE_LOGIN = new Url(
        'PAGE_LOGIN',
        '/login',
        Method.NAVIGATOR
    );

    public static readonly PAGE_DASHBOARD = new Url(
        'PAGE_DASHBOARD',
        '/dashboard',
        Method.NAVIGATOR
    );

    // ==================== 认证模块 ====================
    
    /**
     * 用户登录
     * - 需要加密：保护用户名和密码
     * - 不需要防重复提交：允许用户快速重试
     */
    public static readonly USER_LOGIN = new Url(
        'USER_LOGIN',
        '/api/auth/login',
        Method.POST,
        true,   // needEncryption
        false   // needDuplicateCheck
    );

    /**
     * 用户注册
     * - 需要加密：保护用户信息
     * - 需要防重复提交：防止重复注册
     */
    public static readonly USER_REGISTER = new Url(
        'USER_REGISTER',
        '/api/auth/register',
        Method.POST,
        true,
        true
    );

    public static readonly USER_LOGOUT = new Url(
        'USER_LOGOUT',
        '/api/auth/logout',
        Method.POST,
        false,
        false
    );

    // ==================== 用户模块 ====================
    
    public static readonly USER_PROFILE = new Url(
        'USER_PROFILE',
        '/api/user/profile',
        Method.GET,
        true,
        false
    );

    public static readonly USER_UPDATE = new Url(
        'USER_UPDATE',
        '/api/user/update',
        Method.PUT,
        true,
        true
    );

    // ==================== 订单模块 ====================
    
    public static readonly ORDER_LIST = new Url(
        'ORDER_LIST',
        '/api/order/list',
        Method.GET,
        false,
        false
    );

    public static readonly ORDER_SUBMIT = new Url(
        'ORDER_SUBMIT',
        '/api/order/submit',
        Method.POST,
        true,
        true
    );

    // ==================== 支付模块 ====================
    
    public static readonly PAYMENT_CREATE = new Url(
        'PAYMENT_CREATE',
        '/api/payment/create',
        Method.POST,
        true,
        true
    );

    // ==================== 基础数据 ====================
    
    public static readonly MENU_ALL = new Url(
        'MENU_ALL',
        '/api/menu/all',
        Method.GET,
        false,
        false
    );
}

