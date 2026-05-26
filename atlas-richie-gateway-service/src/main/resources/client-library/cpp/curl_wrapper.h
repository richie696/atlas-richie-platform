/**
 * curl_wrapper.h
 * libcurl包装器头文件
 *
 * Author: richie696
 * Version: 1.0
 * Since: 2025-11-01
 */

#ifndef CURL_WRAPPER_H
#define CURL_WRAPPER_H

#include "http_client.h"
#include <string>
#include <map>

namespace httpclient {

/**
 * libcurl包装器
 */
class CurlWrapper {
public:
    CurlWrapper();
    ~CurlWrapper();
    
    /**
     * 设置URL
     */
    void setUrl(const std::string& url);
    
    /**
     * 设置HTTP方法
     */
    void setMethod(const std::string& method);
    
    /**
     * 设置请求体
     */
    void setBody(const std::string& body);
    
    /**
     * 设置请求头
     */
    void setHeaders(const std::map<std::string, std::string>& headers);
    
    /**
     * 设置超时时间（毫秒）
     */
    void setTimeout(int timeout);
    
    /**
     * 执行请求
     */
    Response execute();

private:
    void* curl_;  // CURL*类型，使用void*避免暴露libcurl头文件
    
    std::string url_;
    std::string method_;
    std::string body_;
    std::map<std::string, std::string> headers_;
    int timeout_;
};

} // namespace httpclient

#endif // CURL_WRAPPER_H

