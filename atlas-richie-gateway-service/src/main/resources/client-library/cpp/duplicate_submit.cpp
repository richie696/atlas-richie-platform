/**
 * duplicate_submit.cpp
 * 防重复提交模块实现
 *
 * Author: richie696
 * Version: 1.0
 * Since: 2025-11-01
 */

#include "duplicate_submit.h"
#include <sstream>
#include <iomanip>
#include <openssl/md5.h>  // 需要OpenSSL库
#include <cstring>

namespace httpclient {

DuplicateSubmitModule::DuplicateSubmitModule(int64_t timeWindow)
    : timeWindow_(timeWindow)
{
}

DuplicateSubmitModule::~DuplicateSubmitModule() = default;

std::string DuplicateSubmitModule::generateRequestId(
    const std::string& url,
    const std::string& method,
    const std::string& body)
{
    // 计算时间窗口内的timestamp
    auto now = std::chrono::system_clock::now();
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()).count();
    int64_t timestamp = (ms / timeWindow_) * timeWindow_;
    
    // 拼接数据
    std::ostringstream oss;
    oss << url << method << timestamp << body;
    std::string data = oss.str();
    
    // MD5哈希
    return md5Hash(data);
}

bool DuplicateSubmitModule::isDuplicateRequest(const std::string& requestId) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto it = requestQueue_.find(requestId);
    if (it == requestQueue_.end()) {
        return false;
    }
    
    auto now = std::chrono::system_clock::now();
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()).count();
    
    return (ms - it->second.timestamp) < timeWindow_;
}

void DuplicateSubmitModule::recordRequest(const std::string& requestId, const std::string& url) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    auto now = std::chrono::system_clock::now();
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()).count();
    
    requestQueue_[requestId] = RequestInfo{ms, url};
    
    cleanupExpired();
}

void DuplicateSubmitModule::clearRequest(const std::string& requestId) {
    std::lock_guard<std::mutex> lock(mutex_);
    requestQueue_.erase(requestId);
}

void DuplicateSubmitModule::cleanupExpired() {
    auto now = std::chrono::system_clock::now();
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()).count();
    int64_t expiredTime = ms - timeWindow_;
    
    auto it = requestQueue_.begin();
    while (it != requestQueue_.end()) {
        if (it->second.timestamp < expiredTime) {
            it = requestQueue_.erase(it);
        } else {
            ++it;
        }
    }
}

std::string DuplicateSubmitModule::md5Hash(const std::string& data) {
    unsigned char digest[MD5_DIGEST_LENGTH];
    MD5(reinterpret_cast<const unsigned char*>(data.c_str()), data.length(), digest);
    
    std::ostringstream oss;
    for (int i = 0; i < MD5_DIGEST_LENGTH; ++i) {
        oss << std::hex << std::setw(2) << std::setfill('0') 
            << static_cast<int>(digest[i]);
    }
    
    return oss.str();
}

} // namespace httpclient

