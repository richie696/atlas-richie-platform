//! Actix-web 框架集成示例
//! 展示如何在Actix-web应用中使用HTTP客户端库
//!
//! Author: richie696
//! Version: 1.0
//! Since: 2025-11-01

mod url; // 导入业务代码定义的Url枚举

use actix_web::{web, App, HttpServer, HttpResponse, Result, HttpRequest};
use httpclient::{HttpClient, HttpClientConfig, ApiResult, is_success, extract_data};
use url::Url;
use serde::{Deserialize, Serialize};
use std::sync::Arc;

// ========== 示例1: 创建服务结构体 ==========

/// API服务结构体
/// 封装业务逻辑的HTTP请求
pub struct ApiService {
    client: Arc<HttpClient>,
}

impl ApiService {
    /// 创建API服务实例
    pub fn new(client: Arc<HttpClient>) -> Self {
        Self { client }
    }

    /// 用户登录
    pub async fn login(&self, username: String, password: String) -> Result<ApiResult<serde_json::Value>, Box<dyn std::error::Error>> {
        #[derive(Serialize)]
        struct LoginBody {
            username: String,
            password: String,
        }
        
        let body = LoginBody { username, password };

        self.client
            .request::<LoginBody, serde_json::Value, Url>(Url::UserLogin, Some(body))
            .await
    }

    /// 提交订单
    pub async fn submit_order(&self, order_data: serde_json::Value) -> Result<ApiResult<serde_json::Value>, Box<dyn std::error::Error>> {
        self.client
            .request::<serde_json::Value, serde_json::Value, Url>(Url::OrderSubmit, Some(order_data))
            .await
    }

    /// 获取菜单列表
    pub async fn get_menu_list(&self) -> Result<ApiResult<serde_json::Value>, Box<dyn std::error::Error>> {
        self.client
            .request::<serde_json::Value, serde_json::Value, Url>(Url::MenuAll, None)
            .await
    }

    /// 获取用户信息
    pub async fn get_user_profile(&self) -> Result<ApiResult<serde_json::Value>, Box<dyn std::error::Error>> {
        // 注意：token现在从响应头自动获取，无需手动传入
        self.client
            .request::<serde_json::Value, serde_json::Value, Url>(Url::UserProfile, None)
            .await
    }
}

// ========== 示例2: 请求/响应结构体 ==========

#[derive(Deserialize)]
struct LoginRequest {
    username: String,
    password: String,
}

#[derive(Serialize)]
struct ApiResponse {
    success: bool,
    data: Option<serde_json::Value>,
    error: Option<String>,
}

// ========== 示例3: Actix-web处理器 ==========

/// 登录处理器
/// POST /api/login
async fn login_handler(
    req: web::Json<LoginRequest>,
    service: web::Data<ApiService>,
) -> Result<HttpResponse> {
    match service.login(req.username.clone(), req.password.clone()).await {
        Ok(result) => {
            if is_success(&result) {
                match result.extract_data() {
                    Ok(user_data) => {
                        // 注意：token现在从响应头自动获取并保存，无需手动处理
                        Ok(HttpResponse::Ok().json(ApiResponse {
                            success: true,
                            data: Some(serde_json::to_value(user_data).unwrap_or(serde_json::Value::Null)),
                            error: None,
                        }))
                    }
                    Err(e) => Ok(HttpResponse::BadRequest().json(ApiResponse {
                        success: false,
                        data: None,
                        error: Some(e),
                    })),
                }
            } else {
                Ok(HttpResponse::BadRequest().json(ApiResponse {
                    success: false,
                    data: None,
                    error: result.msg,
                }))
            }
        }
        Err(e) => Ok(HttpResponse::InternalServerError().json(ApiResponse {
            success: false,
            data: None,
            error: Some(e.to_string()),
        })),
    }
}

/// 订单提交处理器
/// POST /api/orders
async fn submit_order_handler(
    req: web::Json<serde_json::Value>,
    service: web::Data<ApiService>,
) -> Result<HttpResponse> {
    match service.submit_order(req.into_inner()).await {
        Ok(result) => {
            if is_success(&result) {
                match result.extract_data() {
                    Ok(order_data) => {
                        Ok(HttpResponse::Ok().json(ApiResponse {
                            success: true,
                            data: Some(serde_json::to_value(order_data).unwrap_or(serde_json::Value::Null)),
                            error: None,
                        }))
                    }
                    Err(e) => Ok(HttpResponse::BadRequest().json(ApiResponse {
                        success: false,
                        data: None,
                        error: Some(e),
                    })),
                }
            } else {
                Ok(HttpResponse::BadRequest().json(ApiResponse {
                    success: false,
                    data: None,
                    error: result.msg,
                }))
            }
        }
        Err(e) => {
            let error_msg = e.to_string();
            let status = if error_msg.contains("DUPLICATE_REQUEST") || error_msg.contains("DUPLICATE_SUBMIT") {
                429 // Too Many Requests
            } else {
                500
            };

            Ok(HttpResponse::build(status.into()).json(ApiResponse {
                success: false,
                data: None,
                error: Some(error_msg),
            }))
        }
    }
}

/// 菜单列表处理器
/// GET /api/menu
async fn menu_list_handler(
    service: web::Data<ApiService>,
) -> Result<HttpResponse> {
    match service.get_menu_list().await {
        Ok(result) => {
            if is_success(&result) {
                match result.extract_data() {
                    Ok(menu_data) => {
                        Ok(HttpResponse::Ok().json(ApiResponse {
                            success: true,
                            data: Some(serde_json::to_value(menu_data).unwrap_or(serde_json::Value::Null)),
                            error: None,
                        }))
                    }
                    Err(e) => Ok(HttpResponse::BadRequest().json(ApiResponse {
                        success: false,
                        data: None,
                        error: Some(e),
                    })),
                }
            } else {
                Ok(HttpResponse::BadRequest().json(ApiResponse {
                    success: false,
                    data: None,
                    error: result.msg,
                }))
            }
        }
        Err(e) => Ok(HttpResponse::InternalServerError().json(ApiResponse {
            success: false,
            data: None,
            error: Some(e.to_string()),
        })),
    }
}

/// 用户信息处理器
/// GET /api/user/profile
async fn user_profile_handler(
    _req: HttpRequest,
    service: web::Data<ApiService>,
) -> Result<HttpResponse> {
    // 注意：token现在从响应头自动获取，无需手动传入
    match service.get_user_profile().await {
        Ok(result) => {
            if is_success(&result) {
                match result.extract_data() {
                    Ok(user_data) => {
                        Ok(HttpResponse::Ok().json(ApiResponse {
                            success: true,
                            data: Some(serde_json::to_value(user_data).unwrap_or(serde_json::Value::Null)),
                            error: None,
                        }))
                    }
                    Err(e) => Ok(HttpResponse::BadRequest().json(ApiResponse {
                        success: false,
                        data: None,
                        error: Some(e),
                    })),
                }
            } else {
                Ok(HttpResponse::BadRequest().json(ApiResponse {
                    success: false,
                    data: None,
                    error: result.msg,
                }))
            }
        }
        Err(e) => Ok(HttpResponse::InternalServerError().json(ApiResponse {
            success: false,
            data: None,
            error: Some(e.to_string()),
        })),
    }
}

/// 使用客户端直接发送请求的处理器示例
/// POST /api/custom
async fn custom_handler(
    req: web::Json<serde_json::Value>,
    client: web::Data<Arc<HttpClient>>,
) -> Result<HttpResponse> {
    // 直接使用客户端发送请求
    match client
        .request::<serde_json::Value, serde_json::Value, Url>(Url::UserProfile, Some(req.into_inner()))
        .await
    {
        Ok(result) => {
            if is_success(&result) {
                match result.extract_data() {
                    Ok(data) => {
                        Ok(HttpResponse::Ok().json(ApiResponse {
                            success: true,
                            data: Some(serde_json::to_value(data).unwrap_or(serde_json::Value::Null)),
                            error: None,
                        }))
                    }
                    Err(e) => Ok(HttpResponse::BadRequest().json(ApiResponse {
                        success: false,
                        data: None,
                        error: Some(e),
                    })),
                }
            } else {
                Ok(HttpResponse::BadRequest().json(ApiResponse {
                    success: false,
                    data: None,
                    error: result.msg,
                }))
            }
        }
        Err(e) => Ok(HttpResponse::InternalServerError().json(ApiResponse {
            success: false,
            data: None,
            error: Some(e.to_string()),
        })),
    }
}

// ========== 主函数 ==========

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    // 创建HTTP客户端（单例模式）
    let config = HttpClientConfig {
        base_url: std::env::var("GATEWAY_URL")
            .unwrap_or_else(|_| "https://your-gateway.com".to_string()),
        client_id: Some(
            std::env::var("CLIENT_ID")
                .unwrap_or_else(|_| "actix-web-app".to_string()),
        ),
        duplicate_submit_time_window: 3000,
        enable_header_auto_management: true,
        header_storage_key: "http_headers".to_string(),
    };

    let client = Arc::new(HttpClient::new(config));
    
    // 创建API服务
    let api_service = web::Data::new(ApiService::new(client.clone()));

    // 创建客户端数据（用于直接访问）
    let client_data = web::Data::from(client);

    println!("Actix-web服务器运行在端口 8080");
    println!("示例路由:");
    println!("  POST /api/login");
    println!("  POST /api/orders");
    println!("  GET  /api/menu");
    println!("  GET  /api/user/profile");
    println!("  POST /api/custom");

    // 启动HTTP服务器
    HttpServer::new(move || {
        App::new()
            .app_data(api_service.clone())
            .app_data(client_data.clone())
            .service(web::resource("/api/login").route(web::post().to(login_handler)))
            .service(web::resource("/api/orders").route(web::post().to(submit_order_handler)))
            .service(web::resource("/api/menu").route(web::get().to(menu_list_handler)))
            .service(web::resource("/api/user/profile").route(web::get().to(user_profile_handler)))
            .service(web::resource("/api/custom").route(web::post().to(custom_handler)))
    })
    .bind("127.0.0.1:8080")?
    .run()
    .await
}

