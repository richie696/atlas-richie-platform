//! Rust HTTP客户端使用示例
//! 展示如何使用业务代码定义的Url枚举
//!
//! Author: richie696
//! Version: 2.0
//! Since: 2025-11-01

mod url;  // 导入业务代码定义的Url枚举

use httpclient::{HttpClient, HttpClientConfig, ApiResult};
use url::Url;  // 使用业务代码定义的Url
use serde_json::json;
use serde::Deserialize;
use std::error::Error;

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error>> {
    // 创建HTTP客户端
    let config = HttpClientConfig {
        base_url: "https://your-gateway.com".to_string(),
        client_id: Some("rust-client".to_string()),
        duplicate_submit_time_window: 3000,
    };
    
    let client = HttpClient::new(config);
    
    // ========== 示例1: 登录（加密，不防重复）==========
    println!("=== 示例1: 用户登录 ===");
    
    #[derive(Deserialize)]
    struct User {
        user_id: String,
        username: String,
        email: String,
    }
    
    let login_data = json!({
        "username": "user123",
        "password": "password123"
    });
    
    match client.request::<serde_json::Value, User, Url>(Url::UserLogin, Some(login_data)).await {
        Ok(result) => {
            if result.is_success() {
                match result.extract_data() {
                    Ok(user_data) => {
                        println!("登录成功: {:?}", user_data);
                        // 注意：token现在从响应头自动获取并保存，无需手动处理
                    }
                    Err(e) => {
                        println!("数据提取失败: {}", e);
                    }
                }
            } else {
                println!("登录失败: {}", result.msg.as_deref().unwrap_or("未知错误"));
            }
        }
        Err(e) => {
            println!("登录失败: {}", e);
        }
    }
    
    // ========== 示例2: 订单提交（加密 + 防重复）==========
    println!("\n=== 示例2: 订单提交 ===");
    
    #[derive(Deserialize)]
    struct OrderResult {
        order_id: String,
        total_amount: f64,
    }
    
    let order_data = json!({
        "items": vec![
            json!({"productId": "P001", "quantity": 2})
        ],
        "totalAmount": 199.98
    });
    
    match client.request::<serde_json::Value, OrderResult, Url>(Url::OrderSubmit, Some(order_data)).await {
        Ok(result) => {
            if result.is_success() {
                match result.extract_data() {
                    Ok(order_data) => {
                        println!("订单提交成功: {:?}", order_data);
                        println!("订单ID: {}", order_data.order_id);
                    }
                    Err(e) => {
                        println!("数据提取失败: {}", e);
                    }
                }
            } else {
                println!("订单提交失败: {}", result.msg.as_deref().unwrap_or("未知错误"));
            }
        }
        Err(e) => {
            if e.to_string().contains("DUPLICATE_REQUEST") {
                println!("检测到重复提交，请求被拒绝");
            } else {
                println!("订单提交失败: {}", e);
            }
        }
    }
    
    // ========== 示例3: 获取菜单（公开数据）==========
    println!("\n=== 示例3: 获取菜单列表 ===");
    
    #[derive(Deserialize)]
    struct Menu {
        id: String,
        name: String,
        items: Vec<serde_json::Value>,
    }
    
    match client.request::<serde_json::Value, Vec<Menu>>(Url::MenuAll, None::<serde_json::Value>).await {
        Ok(result) => {
            if result.is_success() {
                match result.extract_data() {
                    Ok(menu_data) => {
                        println!("菜单列表: {:?}", menu_data);
                    }
                    Err(e) => {
                        println!("数据提取失败: {}", e);
                    }
                }
            } else {
                println!("获取菜单失败: {}", result.msg.as_deref().unwrap_or("未知错误"));
            }
        }
        Err(e) => {
            println!("获取菜单失败: {}", e);
        }
    }
    
    // ========== 示例4: 演示enum方法 ==========
    println!("\n=== 示例4: 演示Url enum方法 ===");
    
    let url = Url::UserLogin;
    println!("枚举值: {:?}", url);
    println!("名称: {}", url.name());
    println!("路径: {}", url.path());
    println!("方法: {}", url.method());
    println!("需要加密: {}", url.need_encryption());
    println!("需要防重复: {}", url.need_duplicate_check());
    println!("是否写操作: {}", url.is_write_operation());
    println!("完整URL: {}", url.get_full_url("https://api.example.com"));
    
    Ok(())
}

