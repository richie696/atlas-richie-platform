/**
 * http_client.h
 * C++11 HTTP客户端库
 * 支持ECC+AES-GCM加密和防重复提交
 *
 * Author: richie696
 * Version: 1.0
 * Since: 2025-11-01
 */

#ifndef HTTP_CLIENT_H
#define HTTP_CLIENT_H

#include "url.h"
#include <string>
#include <memory>
#include <map>
#include <functional>
#include <mutex>
#include <chrono>
#include <thread>

namespace httpclient {

// 前向声明
class EccCryptoModule;
class DuplicateSubmitModule;

/**
 * HTTP客户端配置
 */
struct HttpClientConfig {
    std::string baseUrl = "https://your-gateway.com";
    std::string clientId = "";
    int64_t duplicateSubmitTimeWindow = 3000; // 毫秒
    int timeout = 30000; // 毫秒
    
    HttpClientConfig() = default;
    HttpClientConfig(const std::string& url) : baseUrl(url) {}
};

/**
 * 响应结果
 */
struct Response {
    int statusCode = 0;
    std::string body;
    std::map<std::string, std::string> headers;
    
    bool isSuccess() const {
        return statusCode >= 200 && statusCode < 300;
    }
};

/**
 * HTTP客户端
 */
class HttpClient {
public:
    /**
     * 构造函数
     */
    explicit HttpClient(const HttpClientConfig& config = HttpClientConfig());
    
    /**
     * 析构函数
     */
    ~HttpClient();
    
    // 禁止拷贝和赋值
    HttpClient(const HttpClient&) = delete;
    HttpClient& operator=(const HttpClient&) = delete;
    
    /**
     * 发送HTTP请求
     * 
     * @param url UrlInterface接口的实现（业务代码定义）
     * @param body 请求体JSON字符串（可选）
     * @param headers 额外请求头（可选）
     * @return Response 响应结果
     */
    Response request(
        const UrlInterface& url,
        const std::string& body = "",
        const std::map<std::string, std::string>& headers = {}
    );
    
    /**
     * 异步发送HTTP请求（使用回调）
     * 
     * @param url UrlInterface接口的实现（业务代码定义）
     * @param body 请求体JSON字符串（可选）
     * @param callback 回调函数，接收Response
     * @param headers 额外请求头（可选）
     */
    void requestAsync(
        const UrlInterface& url,
        const std::string& body,
        std::function<void(const Response&)> callback,
        const std::map<std::string, std::string>& headers = {}
    );

private:
    HttpClientConfig config_;
    std::unique_ptr<EccCryptoModule> eccModule_;
    std::unique_ptr<DuplicateSubmitModule> duplicateModule_;
    
    /**
     * 发送加密请求
     */
    Response sendEncryptedRequest(
        AppUrl url,
        const std::string& body,
        const std::map<std::string, std::string>& headers
    );
    
    /**
     * 发送普通请求
     */
    Response sendPlainRequest(
        AppUrl url,
        const std::string& body,
        const std::map<std::string, std::string>& headers
    );
    
    /**
     * 执行HTTP请求（底层实现）
     */
    Response executeRequest(
        const std::string& url,
        Method method,
        const std::string& body,
        const std::map<std::string, std::string>& headers
    );
};

} // namespace httpclient

#endif // HTTP_CLIENT_H

