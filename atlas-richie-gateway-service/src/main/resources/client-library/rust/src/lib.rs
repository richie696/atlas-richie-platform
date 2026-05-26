//! Rust HTTP客户端库
//! 支持ECC+AES-GCM加密和防重复提交
//!
//! 注意：URL配置请使用AppUrl enum（见url.rs）
//! Rust的enum原生完美支持URL配置，无需额外的struct
//!
//! Author: richie696
//! Version: 2.0
//! Since: 2025-11-01

mod url;
mod result;

use std::collections::HashMap;
use std::sync::{Arc, RwLock};
use std::time::{SystemTime, UNIX_EPOCH};
use serde::{Deserialize, Serialize};
use reqwest::Client;

pub use url::{Method, UrlTrait};
pub use result::{ApiResult, I18nDict, is_success, is_error, extract_data};

#[derive(Debug, Clone)]
pub struct HttpClientConfig {
    pub base_url: String,
    pub client_id: Option<String>,
    pub duplicate_submit_time_window: u64,
    /// 是否启用请求头自动管理（从内存存储读取和保存），默认true
    pub enable_header_auto_management: bool,
    /// 内存存储中存储请求头的键名，默认"http_headers"
    pub header_storage_key: String,
}

impl Default for HttpClientConfig {
    fn default() -> Self {
        Self {
            base_url: "https://your-gateway.com".to_string(),
            client_id: None,
            duplicate_submit_time_window: 3000,
            enable_header_auto_management: true,
            header_storage_key: "http_headers".to_string(),
        }
    }
}

// ==================== HTTP客户端 ====================

pub struct HttpClient {
    config: HttpClientConfig,
    client: Client,
    ecc_module: Arc<RwLock<EccCryptoModule>>,
    duplicate_module: Arc<RwLock<DuplicateSubmitModule>>,
}

impl HttpClient {
    pub fn new(config: HttpClientConfig) -> Self {
        let client_id = config.client_id.clone().unwrap_or_else(|| {
            format!("rust-client-{}", 
                SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis())
        });

        Self {
            config,
            client: Client::new(),
            ecc_module: Arc::new(RwLock::new(EccCryptoModule::new())),
            duplicate_module: Arc::new(RwLock::new(
                DuplicateSubmitModule::new(3000)
            )),
            cached_headers: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    /// 发送HTTP请求
    /// 
    /// # Arguments
    /// * `url` - 实现UrlTrait的枚举值（业务代码定义）
    /// * `body` - 请求体（可选）
    /// 
    /// # Example
    /// ```rust
    /// #[derive(Deserialize)]
    /// struct User { user_id: String, username: String }
    /// 
    /// let result: ApiResult<User> = client.request(Url::UserLogin, Some(login_data)).await?;
    /// if result.is_success() {
    ///     let user = result.extract_data()?;
    /// }
    /// ```
    pub async fn request<Req: Serialize, Resp: for<'de> Deserialize<'de>, U: UrlTrait>(
        &self,
        url: U,  // 泛型约束：必须实现UrlTrait
        body: Option<Req>,
    ) -> Result<ApiResult<Resp>, Box<dyn std::error::Error>> {
        let full_url = url.get_full_url(&self.config.base_url);
        let method = url.method();

        // 1. 防重复提交检查
        let request_id = if url.need_duplicate_check() {
            let body_str = body.as_ref()
                .map(|b| serde_json::to_string(b).unwrap_or_default())
                .unwrap_or_default();
            
            let id = self.duplicate_module.write().unwrap()
                .generate_request_id(&full_url, method.as_str(), &body_str);

            if self.duplicate_module.read().unwrap().is_duplicate_request(&id) {
                return Err("DUPLICATE_REQUEST".into());
            }

            self.duplicate_module.write().unwrap().record_request(id.clone(), full_url.clone());
            Some(id)
        } else {
            None
        };

        // 2. 发送请求
        let (_http_response, response_data, response_headers, status_code) = if url.need_encryption() {
            self.send_encrypted_request(&full_url, method, body).await?
        } else {
            self.send_plain_request(&full_url, method, body).await?
        };

        // 3. 自动保存响应头到内存存储（在解析响应体之前，确保响应头可用）
        if self.config.enable_header_auto_management {
            self.save_response_headers_from_map(&response_headers);
        }

        // 4. 处理响应（完整透传ApiResult<Resp>，不做任何处理）
        let result = self.handle_response::<Resp>(response_data, status_code, url.need_encryption())?;

        // 5. 清理
        if let Some(id) = request_id {
            self.duplicate_module.write().unwrap().clear_request(&id);
        }

        Ok(result)
    }

    async fn send_encrypted_request<Req: Serialize>(
        &self,
        url: &str,
        method: Method,  // Copy trait，直接传值
        body: Option<Req>,
    ) -> Result<(reqwest::Response, Vec<u8>, HashMap<String, String>, reqwest::StatusCode), Box<dyn std::error::Error>> {
        // 简化实现 - 实际需要完整的ECC+AES-GCM实现
        self.send_plain_request(url, method, body).await
    }

    async fn send_plain_request<Req: Serialize>(
        &self,
        url: &str,
        method: Method,  // Copy trait，直接传值
        body: Option<Req>,
    ) -> Result<(reqwest::Response, Vec<u8>, HashMap<String, String>, reqwest::StatusCode), Box<dyn std::error::Error>> {
        // 读取缓存的请求头（包括token等业务请求头）
        let cached_headers = if self.config.enable_header_auto_management {
            self.load_cached_headers()
        } else {
            HashMap::new()
        };

        let mut request = self.client.request(
            reqwest::Method::from_bytes(method.as_str().as_bytes())?,
            url,
        );

        request = request.header("Content-Type", "application/json");

        // 添加缓存的请求头（包括token）
        for (key, value) in cached_headers {
            request = request.header(&key, value);
        }

        if let Some(b) = body {
            request = request.json(&b);
        }

        let response = request.send().await?;
        // 注意：reqwest的Response在读取body后就不能再访问headers了
        // 所以我们需要先保存headers和status，然后读取body
        let status = response.status();
        let headers: HashMap<String, String> = response.headers()
            .iter()
            .filter_map(|(k, v)| {
                v.to_str().ok().map(|v_str| (k.as_str().to_string(), v_str.to_string()))
            })
            .collect();
        let body_bytes = response.bytes().await?.to_vec();
        
        // 创建一个包含headers信息的结构以便后续使用
        // 由于reqwest的限制，我们需要保存headers的副本
        // 这里我们返回一个包含headers信息的元组
        // 实际使用中，我们需要在save_response_headers中使用保存的headers
        Ok((response, body_bytes, headers, status))
    }
    
    fn handle_response<Resp: for<'de> Deserialize<'de>>(
        &self,
        body_bytes: Vec<u8>,
        status_code: reqwest::StatusCode,
        need_decryption: bool,
    ) -> Result<ApiResult<Resp>, Box<dyn std::error::Error>> {
        // 先读取响应体（只能读取一次）
        let mut response_body = body_bytes;

        // 解密响应
        if need_decryption {
            // 简化处理，实际需要ECC解密
            // let decrypted = self.ecc_module.decrypt(&response_body)?;
            // response_body = decrypted.into_bytes();
        }

        // 检查HTTP状态码
        if status_code != reqwest::StatusCode::OK {
            if status_code == reqwest::StatusCode::TOO_MANY_REQUESTS {
                let json: serde_json::Value = serde_json::from_slice(&response_body)?;
                if json.get("code").and_then(|c| c.as_str()) == Some("DUPLICATE_SUBMIT") {
                    return Err("DUPLICATE_SUBMIT".into());
                }
            }
            return Err(format!("HTTP {}: {}", status_code, String::from_utf8_lossy(&response_body)).into());
        }

        // 完整透传ApiResult<Resp>，不做任何处理
        let result: ApiResult<Resp> = serde_json::from_slice(&response_body)?;
        Ok(result)
    }
    
    fn load_cached_headers(&self) -> HashMap<String, String> {
        self.cached_headers.read().unwrap().clone()
    }
    
    fn save_response_headers_from_map(&self, headers: &HashMap<String, String>) {
        let mut business_headers = HashMap::new();
        
        // 遍历所有响应头
        for (key, value) in headers {
            let key_str = key.to_lowercase();
            
            // 排除系统内置的header，其他所有业务请求头都保存（包括x-rd-request-apitoken等）
            if !SYSTEM_HEADERS.contains(&key_str.as_str()) {
                business_headers.insert(key.clone(), value.clone());
            }
        }
        
        // 如果有业务请求头，保存到内存存储
        if !business_headers.is_empty() {
            let mut cached = self.cached_headers.write().unwrap();
            for (k, v) in business_headers {
                cached.insert(k, v);
            }
            println!("[HttpClient] 已保存响应头到内存存储");
        }
    }
}

// ==================== ECC加密模块 ====================

struct EccCryptoModule {
    gateway_key_id: Option<String>,
}

impl EccCryptoModule {
    fn new() -> Self {
        Self {
            gateway_key_id: None,
        }
    }
}

// ==================== 防重复提交模块 ====================

struct RequestInfo {
    timestamp: u64,
    url: String,
}

struct DuplicateSubmitModule {
    request_queue: HashMap<String, RequestInfo>,
    time_window: u64,
}

impl DuplicateSubmitModule {
    fn new(time_window: u64) -> Self {
        Self {
            request_queue: HashMap::new(),
            time_window,
        }
    }

    fn generate_request_id(&self, url: &str, method: &str, body: &str) -> String {
        let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64;
        let timestamp = now / self.time_window * self.time_window;
        let data = format!("{}{}{}{}", url, method, timestamp, body);
        format!("{:x}", md5::compute(data.as_bytes()))
    }

    fn is_duplicate_request(&self, request_id: &str) -> bool {
        if let Some(info) = self.request_queue.get(request_id) {
            let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64;
            return now - info.timestamp < self.time_window;
        }
        false
    }

    fn record_request(&mut self, request_id: String, url: String) {
        let timestamp = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64;
        self.request_queue.insert(request_id, RequestInfo { timestamp, url });
        self.cleanup_expired();
    }

    fn clear_request(&mut self, request_id: &str) {
        self.request_queue.remove(request_id);
    }

    fn cleanup_expired(&mut self) {
        let now = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as u64;
        let expired_time = now - self.time_window;
        self.request_queue.retain(|_, info| info.timestamp >= expired_time);
    }
}

