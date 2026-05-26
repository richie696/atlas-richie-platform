/**
 * url.h
 * C++11强类型枚举实现 - URL配置
 * 
 * Author: richie696
 * Version: 1.0
 * Since: 2025-11-01
 */

#ifndef HTTP_CLIENT_URL_H
#define HTTP_CLIENT_URL_H

#include <string>

namespace httpclient {

/**
 * HTTP方法枚举（C++11强类型枚举）
 */
enum class Method {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH
};

/**
 * 将Method转换为字符串
 */
inline std::string methodToString(Method method) {
    switch (method) {
        case Method::GET:    return "GET";
        case Method::POST:   return "POST";
        case Method::PUT:    return "PUT";
        case Method::DELETE: return "DELETE";
        case Method::PATCH:  return "PATCH";
        default:             return "GET";
    }
}

/**
 * URL接口 - 业务代码的Url类需要实现此接口
 * 
 * 业务代码应定义自己的Url枚举和对应的辅助类，辅助类实现此接口
 * 
 * 使用示例（业务代码）：
 * ```cpp
 * enum class Url {
 *     UserLogin,
 * };
 * 
 * class UrlHelper : public UrlInterface {
 * public:
 *     explicit UrlHelper(Url url) : url_(url) {}
 *     
 *     std::string path() const override {
 *         switch (url_) {
 *             case Url::UserLogin: return "/api/auth/login";
 *         }
 *     }
 *     
 *     Method method() const override {
 *         switch (url_) {
 *             case Url::UserLogin: return Method::POST;
 *         }
 *     }
 *     
 *     bool needEncryption() const override { return true; }
 *     bool needDuplicateCheck() const override { return false; }
 *     
 * private:
 *     Url url_;
 * };
 * ```
 */
class UrlInterface {
public:
    virtual ~UrlInterface() = default;
    
    /**
     * 获取URL路径
     */
    virtual std::string path() const = 0;
    
    /**
     * 获取HTTP方法
     */
    virtual Method method() const = 0;
    
    /**
     * 是否需要加密
     */
    virtual bool needEncryption() const = 0;
    
    /**
     * 是否需要防重复提交检查
     */
    virtual bool needDuplicateCheck() const = 0;
    
    /**
     * 获取完整URL（默认实现）
     */
    virtual std::string getFullUrl(const std::string& baseUrl) const {
        std::string base = baseUrl;
        if (!base.empty() && base.back() == '/') {
            base.pop_back();
        }
        std::string path_str = path();
        if (!path_str.empty() && path_str.front() != '/') {
            path_str = "/" + path_str;
        }
        return base + path_str;
    }
};

} // namespace httpclient

#endif // HTTP_CLIENT_URL_H

