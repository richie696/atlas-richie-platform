/**
 * 业务代码：URL枚举定义
 * Node.js示例
 *
 * @author richie696
 * @version 1.0
 * @since 2025-11-01
 */

import { Url, Method } from '../framework/http-client';

export class AppUrl {
    // ========== 认证模块 ==========
    /** 用户登录 - 需要加密，不需要防重复提交（允许快速重试） */
    public static readonly USER_LOGIN = new Url(
        'USER_LOGIN',
        '/api/auth/login',
        Method.POST,
        true,   // needEncryption
        false   // needDuplicateCheck
    );

    /** 用户注册 - 需要加密和防重复提交 */
    public static readonly USER_REGISTER = new Url(
        'USER_REGISTER',
        '/api/auth/register',
        Method.POST,
        true,
        true
    );

    /** 用户登出 */
    public static readonly USER_LOGOUT = new Url(
        'USER_LOGOUT',
        '/api/auth/logout',
        Method.POST,
        false,
        false
    );

    // ========== 用户模块 ==========
    /** 获取用户信息 - 需要加密 */
    public static readonly USER_PROFILE = new Url(
        'USER_PROFILE',
        '/api/user/profile',
        Method.GET,
        true,
        false
    );

    /** 更新用户信息 - 需要加密和防重复提交 */
    public static readonly USER_UPDATE = new Url(
        'USER_UPDATE',
        '/api/user/update',
        Method.PUT,
        true,
        true
    );

    // ========== 订单模块 ==========
    /** 订单列表 - 公开数据 */
    public static readonly ORDER_LIST = new Url(
        'ORDER_LIST',
        '/api/order/list',
        Method.GET,
        false,
        false
    );

    /** 提交订单 - 需要加密和防重复提交 */
    public static readonly ORDER_SUBMIT = new Url(
        'ORDER_SUBMIT',
        '/api/order/submit',
        Method.POST,
        true,
        true
    );

    /** 取消订单 - 需要防重复提交 */
    public static readonly ORDER_CANCEL = new Url(
        'ORDER_CANCEL',
        '/api/order/cancel',
        Method.POST,
        false,
        true
    );

    // ========== 支付模块 ==========
    /** 创建支付 - 最高安全级别 */
    public static readonly PAYMENT_CREATE = new Url(
        'PAYMENT_CREATE',
        '/api/payment/create',
        Method.POST,
        true,
        true
    );

    /** 查询支付状态 - 需要加密 */
    public static readonly PAYMENT_STATUS = new Url(
        'PAYMENT_STATUS',
        '/api/payment/status',
        Method.GET,
        true,
        false
    );

    // ========== 基础数据 ==========
    /** 菜单列表 - 公开数据 */
    public static readonly MENU_ALL = new Url(
        'MENU_ALL',
        '/api/menu/all',
        Method.GET,
        false,
        false
    );

    /** 字典数据 - 公开数据 */
    public static readonly DICT_LIST = new Url(
        'DICT_LIST',
        '/api/dict/list',
        Method.GET,
        false,
        false
    );
}

