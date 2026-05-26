// Url.swift
// 业务代码：URL枚举定义示例
//
// Author: richie696
// Version: 1.0
// Since: 2025-11-01

import Foundation

/// 业务代码定义的URL枚举
/// 遵循UrlProtocol协议，实现所有必需的属性
enum Url: UrlProtocol {
    // ========== 认证模块 ==========
    /// 用户登录 - 需要加密，不需要防重复提交（允许快速重试）
    case userLogin
    
    /// 用户注册 - 需要加密和防重复提交
    case userRegister
    
    /// 用户登出
    case userLogout
    
    // ========== 用户模块 ==========
    /// 获取用户信息 - 需要加密
    case userProfile
    
    /// 更新用户信息 - 需要加密和防重复提交
    case userUpdate
    
    // ========== 订单模块 ==========
    /// 订单列表 - 公开数据
    case orderList
    
    /// 提交订单 - 需要加密和防重复提交
    case orderSubmit
    
    /// 取消订单 - 需要防重复提交
    case orderCancel
    
    // ========== 支付模块 ==========
    /// 创建支付 - 最高安全级别
    case paymentCreate
    
    /// 查询支付状态 - 需要加密
    case paymentStatus
    
    // ========== 基础数据 ==========
    /// 菜单列表 - 公开数据
    case menuAll
    
    /// 字典数据 - 公开数据
    case dictList
    
    // ========== 实现UrlProtocol协议 ==========
    
    /// 获取URL路径
    var path: String {
        switch self {
        // 认证模块
        case .userLogin:    return "/api/auth/login"
        case .userRegister: return "/api/auth/register"
        case .userLogout:   return "/api/auth/logout"
        
        // 用户模块
        case .userProfile: return "/api/user/profile"
        case .userUpdate:  return "/api/user/update"
        
        // 订单模块
        case .orderList:    return "/api/order/list"
        case .orderSubmit: return "/api/order/submit"
        case .orderCancel:  return "/api/order/cancel"
        
        // 支付模块
        case .paymentCreate: return "/api/payment/create"
        case .paymentStatus: return "/api/payment/status"
        
        // 基础数据
        case .menuAll:  return "/api/menu/all"
        case .dictList: return "/api/dict/list"
        }
    }
    
    /// 获取HTTP方法
    var method: Method {
        switch self {
        case .userLogin: return .post
        case .userRegister: return .post
        case .userLogout: return .post
        case .userProfile: return .get
        case .userUpdate: return .put
        case .orderList: return .get
        case .orderSubmit: return .post
        case .orderCancel: return .post
        case .paymentCreate: return .post
        case .paymentStatus: return .get
        case .menuAll: return .get
        case .dictList: return .get
        }
    }
    
    /// 是否需要加密
    var needEncryption: Bool {
        switch self {
        case .userLogin: return true
        case .userRegister: return true
        case .userLogout: return false
        case .userProfile: return true
        case .userUpdate: return true
        case .orderList: return false
        case .orderSubmit: return true
        case .orderCancel: return false
        case .paymentCreate: return true
        case .paymentStatus: return true
        case .menuAll: return false
        case .dictList: return false
        }
    }
    
    /// 是否需要防重复提交检查
    var needDuplicateCheck: Bool {
        switch self {
        case .userLogin: return false  // 允许快速重试
        case .userRegister: return true
        case .userLogout: return false
        case .userProfile: return false
        case .userUpdate: return true
        case .orderList: return false
        case .orderSubmit: return true
        case .orderCancel: return true
        case .paymentCreate: return true
        case .paymentStatus: return false
        case .menuAll: return false
        case .dictList: return false
        }
    }
}

// ========== 可选的辅助属性和方法 ==========

extension Url {
    /// 获取枚举名称（用于调试）
    var name: String {
        switch self {
        case .userLogin: return "USER_LOGIN"
        case .userRegister: return "USER_REGISTER"
        case .userLogout: return "USER_LOGOUT"
        case .userProfile: return "USER_PROFILE"
        case .userUpdate: return "USER_UPDATE"
        case .orderList: return "ORDER_LIST"
        case .orderSubmit: return "ORDER_SUBMIT"
        case .orderCancel: return "ORDER_CANCEL"
        case .paymentCreate: return "PAYMENT_CREATE"
        case .paymentStatus: return "PAYMENT_STATUS"
        case .menuAll: return "MENU_ALL"
        case .dictList: return "DICT_LIST"
        }
    }
    
    /// 判断是否为写操作
    var isWriteOperation: Bool {
        switch method {
        case .post, .put, .delete, .patch: return true
        default: return false
        }
    }
}

