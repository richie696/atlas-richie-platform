//! url.rs
//! 业务代码：URL枚举定义示例
//!
//! Author: richie696
//! Version: 1.0
//! Since: 2025-11-01

use httpclient::{Method, UrlTrait};
use std::fmt;

/// 业务代码定义的URL枚举
/// 实现UrlTrait trait
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Url {
    // ========== 认证模块 ==========
    /// 用户登录 - 需要加密，不需要防重复提交（允许快速重试）
    UserLogin,
    
    /// 用户注册 - 需要加密和防重复提交
    UserRegister,
    
    /// 用户登出
    UserLogout,
    
    // ========== 用户模块 ==========
    /// 获取用户信息 - 需要加密
    UserProfile,
    
    /// 更新用户信息 - 需要加密和防重复提交
    UserUpdate,
    
    // ========== 订单模块 ==========
    /// 订单列表 - 公开数据
    OrderList,
    
    /// 提交订单 - 需要加密和防重复提交
    OrderSubmit,
    
    /// 取消订单 - 需要防重复提交
    OrderCancel,
    
    // ========== 支付模块 ==========
    /// 创建支付 - 最高安全级别
    PaymentCreate,
    
    /// 查询支付状态 - 需要加密
    PaymentStatus,
    
    // ========== 基础数据 ==========
    /// 菜单列表 - 公开数据
    MenuAll,
    
    /// 字典数据 - 公开数据
    DictList,
}

// 实现UrlTrait trait
impl UrlTrait for Url {
    /// 获取URL路径
    fn path(&self) -> &'static str {
        match self {
            // 认证模块
            Url::UserLogin => "/api/auth/login",
            Url::UserRegister => "/api/auth/register",
            Url::UserLogout => "/api/auth/logout",
            
            // 用户模块
            Url::UserProfile => "/api/user/profile",
            Url::UserUpdate => "/api/user/update",
            
            // 订单模块
            Url::OrderList => "/api/order/list",
            Url::OrderSubmit => "/api/order/submit",
            Url::OrderCancel => "/api/order/cancel",
            
            // 支付模块
            Url::PaymentCreate => "/api/payment/create",
            Url::PaymentStatus => "/api/payment/status",
            
            // 基础数据
            Url::MenuAll => "/api/menu/all",
            Url::DictList => "/api/dict/list",
        }
    }
    
    /// 获取HTTP方法
    fn method(&self) -> Method {
        match self {
            Url::UserLogin => Method::Post,
            Url::UserRegister => Method::Post,
            Url::UserLogout => Method::Post,
            Url::UserProfile => Method::Get,
            Url::UserUpdate => Method::Put,
            Url::OrderList => Method::Get,
            Url::OrderSubmit => Method::Post,
            Url::OrderCancel => Method::Post,
            Url::PaymentCreate => Method::Post,
            Url::PaymentStatus => Method::Get,
            Url::MenuAll => Method::Get,
            Url::DictList => Method::Get,
        }
    }
    
    /// 是否需要加密
    fn need_encryption(&self) -> bool {
        match self {
            Url::UserLogin => true,
            Url::UserRegister => true,
            Url::UserLogout => false,
            Url::UserProfile => true,
            Url::UserUpdate => true,
            Url::OrderList => false,
            Url::OrderSubmit => true,
            Url::OrderCancel => false,
            Url::PaymentCreate => true,
            Url::PaymentStatus => true,
            Url::MenuAll => false,
            Url::DictList => false,
        }
    }
    
    /// 是否需要防重复提交检查
    fn need_duplicate_check(&self) -> bool {
        match self {
            Url::UserLogin => false,  // 允许快速重试
            Url::UserRegister => true,
            Url::UserLogout => false,
            Url::UserProfile => false,
            Url::UserUpdate => true,
            Url::OrderList => false,
            Url::OrderSubmit => true,
            Url::OrderCancel => true,
            Url::PaymentCreate => true,
            Url::PaymentStatus => false,
            Url::MenuAll => false,
            Url::DictList => false,
        }
    }
}

// 可选的辅助方法
impl Url {
    /// 获取枚举名称（用于调试）
    pub fn name(&self) -> &'static str {
        match self {
            Url::UserLogin => "USER_LOGIN",
            Url::UserRegister => "USER_REGISTER",
            Url::UserLogout => "USER_LOGOUT",
            Url::UserProfile => "USER_PROFILE",
            Url::UserUpdate => "USER_UPDATE",
            Url::OrderList => "ORDER_LIST",
            Url::OrderSubmit => "ORDER_SUBMIT",
            Url::OrderCancel => "ORDER_CANCEL",
            Url::PaymentCreate => "PAYMENT_CREATE",
            Url::PaymentStatus => "PAYMENT_STATUS",
            Url::MenuAll => "MENU_ALL",
            Url::DictList => "DICT_LIST",
        }
    }
    
    /// 判断是否为写操作
    pub fn is_write_operation(&self) -> bool {
        matches!(self.method(), Method::Post | Method::Put | Method::Delete | Method::Patch)
    }
}

impl fmt::Display for Url {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "Url::{}(path={}, method={}, encryption={}, duplicateCheck={})",
            self.name(),
            self.path(),
            self.method(),
            self.need_encryption(),
            self.need_duplicate_check()
        )
    }
}

