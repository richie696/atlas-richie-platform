/**
 * 应用URL配置示例
 * 定义所有API端点和页面路由
 * 
 * @author richie696
 * @version 1.0
 */

import { Url } from '../framework/url';
import { Method } from '../framework/method.enum';

/**
 * 应用URL枚举类
 * 集中管理所有API端点和页面路由
 */
export class AppUrl {
    // ==================== 页面路由 ====================
    
    /** 首页 */
    public static readonly PAGE_HOME = new Url(
        'PAGE_HOME',
        '/pages/index/index',
        Method.NAVIGATOR
    );

    /** 登录页 */
    public static readonly PAGE_LOGIN = new Url(
        'PAGE_LOGIN',
        '/pages/login/login',
        Method.NAVIGATOR
    );

    // ==================== 公开API（不加密，不防重复） ====================
    
    /** 获取菜单列表 */
    public static readonly MENU_ALL = new Url(
        'MENU_ALL',
        '/api/menu/all',
        Method.GET
    );

    /** 获取字典数据 */
    public static readonly DICT_LIST = new Url(
        'DICT_LIST',
        '/api/dict/list',
        Method.GET
    );

    // ==================== 加密API（加密，不防重复） ====================
    
    /** 用户登录 */
    public static readonly USER_LOGIN = new Url(
        'USER_LOGIN',
        '/api/auth/login',
        Method.POST,
        true,   // ✅ 加密
        false   // ❌ 不防重复（允许快速重试）
    );

    /** 获取用户信息 */
    public static readonly USER_INFO = new Url(
        'USER_INFO',
        '/api/user/info',
        Method.GET,
        true,   // ✅ 加密
        false   // ❌ 不防重复
    );

    /** 获取用户详情 */
    public static readonly USER_DETAIL = new Url(
        'USER_DETAIL',
        '/api/user/{}',  // 支持路径参数
        Method.GET,
        true,   // ✅ 加密
        false   // ❌ 不防重复
    );

    // ==================== 防重复API（不加密，防重复） ====================
    
    /** 文件上传 */
    public static readonly FILE_UPLOAD = new Url(
        'FILE_UPLOAD',
        '/api/file/upload',
        Method.POST,
        false,  // ❌ 不加密（二进制数据）
        true    // ✅ 防重复
    );

    // ==================== 最高安全API（加密 + 防重复） ====================
    
    /** 创建支付订单 */
    public static readonly PAYMENT_CREATE = new Url(
        'PAYMENT_CREATE',
        '/api/payment/create',
        Method.POST,
        true,   // ✅ 加密
        true    // ✅ 防重复提交
    );

    /** 提交订单 */
    public static readonly ORDER_SUBMIT = new Url(
        'ORDER_SUBMIT',
        '/api/order/submit',
        Method.POST,
        true,   // ✅ 加密
        true    // ✅ 防重复提交
    );

    /** 用户注册 */
    public static readonly USER_REGISTER = new Url(
        'USER_REGISTER',
        '/api/auth/register',
        Method.POST,
        true,   // ✅ 加密
        true    // ✅ 防重复提交
    );
}

