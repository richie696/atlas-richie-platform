/**
 * curl_wrapper.cpp
 * libcurl包装器实现
 *
 * Author: richie696
 * Version: 1.0
 * Since: 2025-11-01
 */

#include "curl_wrapper.h"
#include <curl/curl.h>
#include <sstream>
#include <iostream>

namespace httpclient {

// 回调函数：写入响应数据
static size_t WriteCallback(void* contents, size_t size, size_t nmemb, void* userp) {
    std::string* str = static_cast<std::string*>(userp);
    size_t totalSize = size * nmemb;
    str->append(static_cast<char*>(contents), totalSize);
    return totalSize;
}

// 回调函数：写入响应头
static size_t HeaderCallback(char* buffer, size_t size, size_t nitems, void* userp) {
    std::map<std::string, std::string>* headers = static_cast<std::map<std::string, std::string>*>(userp);
    std::string header(buffer, size * nitems);
    
    size_t colonPos = header.find(':');
    if (colonPos != std::string::npos) {
        std::string key = header.substr(0, colonPos);
        std::string value = header.substr(colonPos + 1);
        
        // 去除前后空白
        key.erase(0, key.find_first_not_of(" \t\r\n"));
        key.erase(key.find_last_not_of(" \t\r\n") + 1);
        value.erase(0, value.find_first_not_of(" \t\r\n"));
        value.erase(value.find_last_not_of(" \t\r\n") + 1);
        
        (*headers)[key] = value;
    }
    
    return size * nitems;
}

CurlWrapper::CurlWrapper()
    : curl_(curl_easy_init())
    , timeout_(30000)
{
}

CurlWrapper::~CurlWrapper() {
    if (curl_) {
        curl_easy_cleanup(static_cast<CURL*>(curl_));
    }
}

void CurlWrapper::setUrl(const std::string& url) {
    url_ = url;
}

void CurlWrapper::setMethod(const std::string& method) {
    method_ = method;
}

void CurlWrapper::setBody(const std::string& body) {
    body_ = body;
}

void CurlWrapper::setHeaders(const std::map<std::string, std::string>& headers) {
    headers_ = headers;
}

void CurlWrapper::setTimeout(int timeout) {
    timeout_ = timeout;
}

Response CurlWrapper::execute() {
    Response response;
    
    if (!curl_) {
        response.statusCode = 500;
        response.body = R"({"code":"CURL_ERROR","message":"Failed to initialize curl"})";
        return response;
    }
    
    CURL* curl = static_cast<CURL*>(curl_);
    
    // 设置URL
    curl_easy_setopt(curl, CURLOPT_URL, url_.c_str());
    
    // 设置HTTP方法
    if (method_ == "GET") {
        curl_easy_setopt(curl, CURLOPT_HTTPGET, 1L);
    } else if (method_ == "POST") {
        curl_easy_setopt(curl, CURLOPT_POST, 1L);
        if (!body_.empty()) {
            curl_easy_setopt(curl, CURLOPT_POSTFIELDS, body_.c_str());
            curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, body_.length());
        }
    } else if (method_ == "PUT") {
        curl_easy_setopt(curl, CURLOPT_PUT, 1L);
        if (!body_.empty()) {
            curl_easy_setopt(curl, CURLOPT_POSTFIELDS, body_.c_str());
            curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, body_.length());
        }
    } else if (method_ == "DELETE") {
        curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, "DELETE");
    } else if (method_ == "PATCH") {
        curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, "PATCH");
        if (!body_.empty()) {
            curl_easy_setopt(curl, CURLOPT_POSTFIELDS, body_.c_str());
            curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, body_.length());
        }
    }
    
    // 设置请求头
    struct curl_slist* headerList = nullptr;
    for (const auto& pair : headers_) {
        std::ostringstream oss;
        oss << pair.first << ": " << pair.second;
        headerList = curl_slist_append(headerList, oss.str().c_str());
    }
    if (headerList) {
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headerList);
    }
    
    // 设置超时
    curl_easy_setopt(curl, CURLOPT_TIMEOUT_MS, static_cast<long>(timeout_));
    curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT_MS, static_cast<long>(timeout_));
    
    // 设置回调函数
    std::string responseBody;
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteCallback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &responseBody);
    
    curl_easy_setopt(curl, CURLOPT_HEADERFUNCTION, HeaderCallback);
    curl_easy_setopt(curl, CURLOPT_HEADERDATA, &response.headers);
    
    // 执行请求
    CURLcode res = curl_easy_perform(curl);
    
    // 清理请求头
    if (headerList) {
        curl_slist_free_all(headerList);
    }
    
    if (res == CURLE_OK) {
        response.body = responseBody;
        curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &response.statusCode);
    } else {
        response.statusCode = 500;
        std::ostringstream oss;
        oss << R"({"code":"CURL_ERROR","message":")" << curl_easy_strerror(res) << "\"}";
        response.body = oss.str();
    }
    
    return response;
}

} // namespace httpclient

