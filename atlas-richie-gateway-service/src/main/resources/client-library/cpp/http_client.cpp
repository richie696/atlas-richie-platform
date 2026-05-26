/**
 * http_client.cpp
 * C++11 HTTP客户端实现
 *
 * Author: richie696
 * Version: 1.0
 * Since: 2025-11-01
 */

#include "http_client.h"
#include "ecc_crypto.h"
#include "duplicate_submit.h"
#include "curl_wrapper.h"
#include <sstream>
#include <chrono>
#include <ctime>
#include <thread>

namespace httpclient {

HttpClient::HttpClient(const HttpClientConfig& config)
    : config_(config)
    , eccModule_(std::make_unique<EccCryptoModule>())
    , duplicateModule_(std::make_unique<DuplicateSubmitModule>(config.duplicateSubmitTimeWindow))
{
    if (config_.clientId.empty()) {
        auto now = std::chrono::system_clock::now();
        auto timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
            now.time_since_epoch()).count();
        config_.clientId = "cpp-client-" + std::to_string(timestamp);
    }
}

HttpClient::~HttpClient() = default;

Response HttpClient::request(
    const UrlInterface& url,
    const std::string& body,
    const std::map<std::string, std::string>& headers)
{
    std::string fullUrl = url.getFullUrl(config_.baseUrl);
    Method method = url.method();
    
    // 1. 防重复提交检查
    std::string requestId;
    if (url.needDuplicateCheck()) {
        // 生成请求ID
        requestId = duplicateModule_->generateRequestId(
            fullUrl,
            methodToString(method),
            body
        );
        
        // 检查是否重复
        if (duplicateModule_->isDuplicateRequest(requestId)) {
            Response response;
            response.statusCode = 429;
            response.body = R"({"code":"DUPLICATE_REQUEST","message":"请求正在处理中"})";
            return response;
        }
        
        // 记录请求
        duplicateModule_->recordRequest(requestId, fullUrl);
    }
    
    // 2. 发送请求
    Response response;
    if (url.needEncryption()) {
        response = sendEncryptedRequest(url, body, headers);
    } else {
        response = sendPlainRequest(url, body, headers);
    }
    
    // 3. 清理
    if (!requestId.empty()) {
        duplicateModule_->clearRequest(requestId);
    }
    
    return response;
}

void HttpClient::requestAsync(
    const UrlInterface& url,
    const std::string& body,
    std::function<void(const Response&)> callback,
    const std::map<std::string, std::string>& headers)
{
    // 注意：这里需要复制UrlInterface的实现
    // 实际项目中应该使用智能指针或其他方式传递
    // 简化实现：创建临时副本（需要业务代码支持拷贝）
    // TODO: 改进为使用智能指针
    std::thread([this, &url, body, callback, headers]() {
        Response response = this->request(url, body, headers);
        callback(response);
    }).detach();
}

Response HttpClient::sendEncryptedRequest(
    const UrlInterface& url,
    const std::string& body,
    const std::map<std::string, std::string>& headers)
{
    // 确保已初始化
    if (!eccModule_->isInitialized()) {
        if (!eccModule_->exchangeKeys(config_.baseUrl, config_.clientId)) {
            Response response;
            response.statusCode = 500;
            response.body = R"({"code":"INIT_ERROR","message":"ECC密钥交换失败"})";
            return response;
        }
    }
    
    std::string fullUrl = url.getFullUrl(config_.baseUrl);
    Method method = url.method();
    
    // 构建请求头
    std::map<std::string, std::string> requestHeaders = headers;
    requestHeaders["Content-Type"] = "application/json";
    requestHeaders["X-Client-Id"] = config_.clientId;
    requestHeaders["X-Gateway-KeyId"] = eccModule_->getGatewayKeyId();
    
    // 时间戳
    auto now = std::chrono::system_clock::now();
    auto timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()).count();
    requestHeaders["X-Client-Timestamp"] = std::to_string(timestamp);
    
    // 加密请求体
    std::string encryptedData;
    std::string requestBody = body;
    if (!body.empty() && method != Method::GET) {
        encryptedData = eccModule_->encrypt(body);
        requestHeaders["X-Encrypted-Data"] = encryptedData;
    }
    
    // 发送请求
    Response response = executeRequest(fullUrl, method, requestBody, requestHeaders);
    
    // 处理423状态码（需要重新握手）
    if (response.statusCode == 423) {
        // 解析响应获取重新握手信息
        // 简化实现：直接返回错误，实际需要解析JSON
        // TODO: 解析响应并重新握手
    }
    
    // 解密响应
    if (response.isSuccess() && response.headers.count("X-Response-Encrypted")) {
        if (response.headers.at("X-Response-Encrypted") == "true") {
            std::string decrypted = eccModule_->decrypt(response.body);
            response.body = decrypted;
        }
    }
    
    return response;
}

Response HttpClient::sendPlainRequest(
    const UrlInterface& url,
    const std::string& body,
    const std::map<std::string, std::string>& headers)
{
    std::string fullUrl = url.getFullUrl(config_.baseUrl);
    Method method = url.method();
    
    std::map<std::string, std::string> requestHeaders = headers;
    requestHeaders["Content-Type"] = "application/json";
    
    return executeRequest(fullUrl, method, body, requestHeaders);
}

Response HttpClient::executeRequest(
    const std::string& url,
    Method method,
    const std::string& body,
    const std::map<std::string, std::string>& headers)
{
    CurlWrapper curl;
    curl.setUrl(url);
    curl.setMethod(methodToString(method));
    curl.setBody(body);
    curl.setHeaders(headers);
    curl.setTimeout(config_.timeout);
    
    return curl.execute();
}

} // namespace httpclient

