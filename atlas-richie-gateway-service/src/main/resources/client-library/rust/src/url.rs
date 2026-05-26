//! Rust原生enum实现 - URL配置
//! 
//! Rust的enum非常强大，可以直接定义URL枚举，每个URL是一个枚举值
//! 无需像TypeScript那样自定义Enum基类
//!
//! Author: richie696
//! Version: 2.0
//! Since: 2025-11-01

use std::fmt;

/// HTTP方法枚举（Rust原生enum）
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Method {
    Get,
    Post,
    Put,
    Delete,
    Patch,
}

impl Method {
    /// 转换为字符串
    pub fn as_str(&self) -> &'static str {
        match self {
            Method::Get => "GET",
            Method::Post => "POST",
            Method::Put => "PUT",
            Method::Delete => "DELETE",
            Method::Patch => "PATCH",
        }
    }
}

impl fmt::Display for Method {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.as_str())
    }
}

/// URL trait - 业务代码的Url枚举需要实现此trait
/// 
/// 业务代码应定义自己的Url枚举，并实现以下方法：
/// - path(): URL路径
/// - method(): HTTP方法
/// - need_encryption(): 是否需要加密
/// - need_duplicate_check(): 是否需要防重复提交检查
/// 
/// 使用示例（业务代码）：
/// ```rust
/// #[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
/// enum Url {
///     UserLogin,
/// }
/// 
/// impl UrlTrait for Url {
///     fn path(&self) -> &'static str {
///         match self {
///             Url::UserLogin => "/api/auth/login",
///         }
///     }
///     
///     fn method(&self) -> Method {
///         match self {
///             Url::UserLogin => Method::Post,
///         }
///     }
///     
///     fn need_encryption(&self) -> bool {
///         match self {
///             Url::UserLogin => true,
///         }
///     }
///     
///     fn need_duplicate_check(&self) -> bool {
///         match self {
///             Url::UserLogin => false,
///         }
///     }
/// }
/// ```
pub trait UrlTrait {
    /// 获取URL路径
    fn path(&self) -> &'static str;
    
    /// 获取HTTP方法
    fn method(&self) -> Method;
    
    /// 是否需要加密
    fn need_encryption(&self) -> bool;
    
    /// 是否需要防重复提交检查
    fn need_duplicate_check(&self) -> bool;
    
    /// 获取完整URL（默认实现）
    fn get_full_url(&self, base_url: &str) -> String {
        format!("{}{}", base_url.trim_end_matches('/'), self.path())
    }
}
