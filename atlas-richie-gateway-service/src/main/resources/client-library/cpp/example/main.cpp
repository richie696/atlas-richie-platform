/**
 * main.cpp
 * C++11客户端使用示例
 * 展示如何使用业务代码定义的Url枚举和UrlHelper类
 *
 * Author: richie696
 * Version: 2.0
 * Since: 2025-11-01
 */

#include "../http_client.h"
#include "url.h"  // 导入业务代码定义的Url和UrlHelper
#include <iostream>
#include <string>
#include <thread>
#include <chrono>

using namespace httpclient;
using namespace httpclient::example;

int main() {
    // 创建HTTP客户端
    HttpClientConfig config;
    config.baseUrl = "https://your-gateway.com";
    config.clientId = "cpp-client";
    config.timeout = 30000;
    
    HttpClient client(config);
    
    // ========== 示例1: 登录（加密，不防重复）==========
    std::cout << "=== 示例1: 用户登录 ===" << std::endl;
    
    std::string loginData = R"({"username":"user123","password":"password123"})";
    
    // 使用业务代码定义的Url枚举和UrlHelper
    UrlHelper urlHelper(Url::UserLogin);
    Response response = client.request(urlHelper, loginData);
    
    if (response.isSuccess()) {
        std::cout << "登录成功: " << response.body << std::endl;
    } else {
        std::cout << "登录失败: " << response.body << std::endl;
    }
    
    // ========== 示例2: 订单提交（加密 + 防重复）==========
    std::cout << "\n=== 示例2: 订单提交 ===" << std::endl;
    
    std::string orderData = R"({"items":[{"productId":"P001","quantity":2}],"totalAmount":199.98})";
    
    UrlHelper orderUrlHelper(Url::OrderSubmit);
    response = client.request(orderUrlHelper, orderData);
    
    if (response.isSuccess()) {
        std::cout << "订单提交成功: " << response.body << std::endl;
    } else {
        if (response.statusCode == 429) {
            std::cout << "检测到重复提交，请求被拒绝" << std::endl;
        } else {
            std::cout << "订单提交失败: " << response.body << std::endl;
        }
    }
    
    // ========== 示例3: 获取菜单（公开数据）==========
    std::cout << "\n=== 示例3: 获取菜单列表 ===" << std::endl;
    
    UrlHelper menuUrlHelper(Url::MenuAll);
    response = client.request(menuUrlHelper, "");
    
    if (response.isSuccess()) {
        std::cout << "菜单列表: " << response.body << std::endl;
    } else {
        std::cout << "获取菜单失败: " << response.body << std::endl;
    }
    
    // ========== 示例4: 演示UrlHelper方法 ==========
    std::cout << "\n=== 示例4: 演示UrlHelper方法 ===" << std::endl;
    
    UrlHelper urlHelper2(Url::UserLogin);
    
    std::cout << "枚举名称: " << urlHelper2.getName() << std::endl;
    std::cout << "URL路径: " << urlHelper2.path() << std::endl;
    std::cout << "HTTP方法: " << methodToString(urlHelper2.method()) << std::endl;
    std::cout << "需要加密: " << (urlHelper2.needEncryption() ? "true" : "false") << std::endl;
    std::cout << "需要防重复: " << (urlHelper2.needDuplicateCheck() ? "true" : "false") << std::endl;
    std::cout << "完整URL: " << urlHelper2.getFullUrl(config.baseUrl) << std::endl;
    
    // ========== 示例5: switch匹配（编译器会检查是否穷尽）==========
    std::cout << "\n=== 示例5: switch匹配 ===" << std::endl;
    
    Url urls[] = {Url::UserLogin, Url::OrderSubmit, Url::MenuAll};
    
    for (auto url : urls) {
        UrlHelper helper(url);
        switch (url) {
            case Url::UserLogin:
                std::cout << helper.getName() << " - 认证相关接口" << std::endl;
                break;
            case Url::OrderSubmit:
                std::cout << helper.getName() << " - 订单相关接口" << std::endl;
                break;
            case Url::MenuAll:
                std::cout << helper.getName() << " - 基础数据接口" << std::endl;
                break;
            // 如果遗漏了某个case，某些编译器会警告（但C++11标准不强制）
            default:
                std::cout << helper.getName() << " - 其他接口" << std::endl;
                break;
        }
    }
    
    // ========== 示例6: 异步请求 ==========
    std::cout << "\n=== 示例6: 异步请求 ===" << std::endl;
    
    UrlHelper profileHelper(Url::UserProfile);
    client.requestAsync(profileHelper, "", [](const Response& resp) {
        std::cout << "异步请求完成，状态码: " << resp.statusCode << std::endl;
        std::cout << "响应: " << resp.body << std::endl;
    });
    
    // 等待异步请求完成（实际项目中应该使用更好的同步机制）
    std::this_thread::sleep_for(std::chrono::seconds(2));
    
    return 0;
}

