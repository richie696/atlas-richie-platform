/**
 * Url.kt
 * 业务代码：URL枚举定义示例
 *
 * @author richie696
 * @version 2.0
 * @since 2025-11-01
 */

package com.richie.example

import com.richie.httpclient.Method
import com.richie.httpclient.UrlInterface

/**
 * 业务代码定义的URL枚举
 * 实现UrlInterface接口
 * 
 * Kotlin的enum class可以完美实现接口，每个枚举值都可以有独立的属性值
 */
enum class Url : UrlInterface {
    // ========== 认证模块 ==========
    /** 用户登录 - 需要加密，不需要防重复提交（允许快速重试） */
    UserLogin {
        override val path = "/api/auth/login"
        override val method = Method.POST
        override val needEncryption = true
        override val needDuplicateCheck = false
    },
    
    /** 用户注册 - 需要加密和防重复提交 */
    UserRegister {
        override val path = "/api/auth/register"
        override val method = Method.POST
        override val needEncryption = true
        override val needDuplicateCheck = true
    },
    
    /** 用户登出 */
    UserLogout {
        override val path = "/api/auth/logout"
        override val method = Method.POST
        override val needEncryption = false
        override val needDuplicateCheck = false
    },
    
    // ========== 用户模块 ==========
    /** 获取用户信息 - 需要加密 */
    UserProfile {
        override val path = "/api/user/profile"
        override val method = Method.GET
        override val needEncryption = true
        override val needDuplicateCheck = false
    },
    
    /** 更新用户信息 - 需要加密和防重复提交 */
    UserUpdate {
        override val path = "/api/user/update"
        override val method = Method.PUT
        override val needEncryption = true
        override val needDuplicateCheck = true
    },
    
    // ========== 订单模块 ==========
    /** 订单列表 - 公开数据 */
    OrderList {
        override val path = "/api/order/list"
        override val method = Method.GET
        override val needEncryption = false
        override val needDuplicateCheck = false
    },
    
    /** 提交订单 - 需要加密和防重复提交 */
    OrderSubmit {
        override val path = "/api/order/submit"
        override val method = Method.POST
        override val needEncryption = true
        override val needDuplicateCheck = true
    },
    
    /** 取消订单 - 需要防重复提交 */
    OrderCancel {
        override val path = "/api/order/cancel"
        override val method = Method.POST
        override val needEncryption = false
        override val needDuplicateCheck = true
    },
    
    // ========== 支付模块 ==========
    /** 创建支付 - 最高安全级别 */
    PaymentCreate {
        override val path = "/api/payment/create"
        override val method = Method.POST
        override val needEncryption = true
        override val needDuplicateCheck = true
    },
    
    /** 查询支付状态 - 需要加密 */
    PaymentStatus {
        override val path = "/api/payment/status"
        override val method = Method.GET
        override val needEncryption = true
        override val needDuplicateCheck = false
    },
    
    // ========== 基础数据 ==========
    /** 菜单列表 - 公开数据 */
    MenuAll {
        override val path = "/api/menu/all"
        override val method = Method.GET
        override val needEncryption = false
        override val needDuplicateCheck = false
    },
    
    /** 字典数据 - 公开数据 */
    DictList {
        override val path = "/api/dict/list"
        override val method = Method.GET
        override val needEncryption = false
        override val needDuplicateCheck = false
    };
    
    // ========== 可选的辅助方法 ==========
    
    /**
     * 获取枚举名称（用于调试）
     */
    val enumName: String
        get() = name
    
    /**
     * 判断是否为写操作
     */
    val isWriteOperation: Boolean
        get() = method == Method.POST || method == Method.PUT || 
                method == Method.DELETE || method == Method.PATCH
}

