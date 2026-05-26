//! Result 通用响应结构
//! 对应服务端 Java ResultVO<T>
//!
//! Author: richie696
//! Version: 1.0
//! Since: 2025-11-11

use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// 国际化字典类型
pub type I18nDict = HashMap<String, HashMap<String, String>>;

/// 通用响应结果结构
/// 对应服务端 ResultVO<T>
///
/// 使用示例：
/// ```rust
/// #[derive(Deserialize)]
/// struct User {
///     id: u32,
///     name: String,
/// }
///
/// let result: ApiResult<User> = client.request(Url::UserInfo, None).await?;
/// if result.is_success() {
///     println!("用户信息: {:?}", result.data);
/// } else {
///     println!("错误: {}", result.msg.as_deref().unwrap_or("未知错误"));
/// }
/// ```
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ApiResult<T> {
    /// 结果数据
    pub data: Option<T>,
    /// 结果代码，成功通常为 "200" 或 "SUCCESS"
    pub code: String,
    /// 错误信息或提示信息
    #[serde(skip_serializing_if = "Option::is_none")]
    pub msg: Option<String>,
    /// 国际化字典
    #[serde(skip_serializing_if = "Option::is_none")]
    pub i18n_dict: Option<I18nDict>,
    /// 时间戳（毫秒）
    pub timestamp: i64,
}

impl<T> ApiResult<T> {
    /// 判断是否成功
    pub fn is_success(&self) -> bool {
        self.code == "200" || self.code == "SUCCESS" || self.code == "0"
    }
    
    /// 判断是否失败
    pub fn is_error(&self) -> bool {
        !self.is_success()
    }
    
    /// 提取数据，如果失败则返回错误
    pub fn extract_data(self) -> Result<T, String> {
        if !self.is_success() {
            let msg = self.msg.unwrap_or_else(|| "操作失败".to_string());
            return Err(format!("操作失败，错误代码: {}, 错误信息: {}", self.code, msg));
        }
        
        self.data.ok_or_else(|| "数据为空".to_string())
    }
}

/// 判断ApiResult是否成功（辅助函数）
pub fn is_success<T>(result: &ApiResult<T>) -> bool {
    result.is_success()
}

/// 判断ApiResult是否失败（辅助函数）
pub fn is_error<T>(result: &ApiResult<T>) -> bool {
    result.is_error()
}

/// 从ApiResult中提取数据（辅助函数）
pub fn extract_data<T>(result: ApiResult<T>) -> Result<T, String> {
    result.extract_data()
}

