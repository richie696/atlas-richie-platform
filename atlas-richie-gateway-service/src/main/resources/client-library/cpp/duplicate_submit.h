/**
 * duplicate_submit.h
 * 防重复提交模块头文件
 *
 * Author: richie696
 * Version: 1.0
 * Since: 2025-11-01
 */

#ifndef DUPLICATE_SUBMIT_H
#define DUPLICATE_SUBMIT_H

#include <string>
#include <mutex>
#include <map>
#include <chrono>

namespace httpclient {

/**
 * 请求信息
 */
struct RequestInfo {
    int64_t timestamp;
    std::string url;
};

/**
 * 防重复提交模块
 */
class DuplicateSubmitModule {
public:
    explicit DuplicateSubmitModule(int64_t timeWindow);
    ~DuplicateSubmitModule();
    
    /**
     * 生成请求ID
     */
    std::string generateRequestId(
        const std::string& url,
        const std::string& method,
        const std::string& body
    );
    
    /**
     * 检查是否重复请求
     */
    bool isDuplicateRequest(const std::string& requestId);
    
    /**
     * 记录请求
     */
    void recordRequest(const std::string& requestId, const std::string& url);
    
    /**
     * 清除请求记录
     */
    void clearRequest(const std::string& requestId);

private:
    int64_t timeWindow_;  // 时间窗口（毫秒）
    std::map<std::string, RequestInfo> requestQueue_;
    std::mutex mutex_;
    
    /**
     * 清理过期请求
     */
    void cleanupExpired();
    
    /**
     * MD5哈希
     */
    std::string md5Hash(const std::string& data);
};

} // namespace httpclient

#endif // DUPLICATE_SUBMIT_H

