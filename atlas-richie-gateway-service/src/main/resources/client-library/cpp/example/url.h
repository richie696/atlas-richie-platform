/**
 * url.h
 * 业务代码：URL枚举和辅助类定义示例
 *
 * Author: richie696
 * Version: 1.0
 * Since: 2025-11-01
 */

#ifndef EXAMPLE_URL_H
#define EXAMPLE_URL_H

#include "../url.h"
#include <string>

namespace httpclient {
namespace example {

/**
 * 业务代码定义的URL枚举
 */
enum class Url {
    // ========== 认证模块 ==========
    UserLogin,
    UserRegister,
    UserLogout,
    
    // ========== 用户模块 ==========
    UserProfile,
    UserUpdate,
    
    // ========== 订单模块 ==========
    OrderList,
    OrderSubmit,
    OrderCancel,
    
    // ========== 支付模块 ==========
    PaymentCreate,
    PaymentStatus,
    
    // ========== 基础数据 ==========
    MenuAll,
    DictList
};

/**
 * Url辅助类 - 实现UrlInterface接口
 */
class UrlHelper : public UrlInterface {
public:
    explicit UrlHelper(Url url) : url_(url) {}
    
    /**
     * 实现UrlInterface接口
     */
    std::string path() const override {
        switch (url_) {
            // 认证模块
            case Url::UserLogin:    return "/api/auth/login";
            case Url::UserRegister: return "/api/auth/register";
            case Url::UserLogout:   return "/api/auth/logout";
            
            // 用户模块
            case Url::UserProfile: return "/api/user/profile";
            case Url::UserUpdate:  return "/api/user/update";
            
            // 订单模块
            case Url::OrderList:    return "/api/order/list";
            case Url::OrderSubmit: return "/api/order/submit";
            case Url::OrderCancel:  return "/api/order/cancel";
            
            // 支付模块
            case Url::PaymentCreate: return "/api/payment/create";
            case Url::PaymentStatus: return "/api/payment/status";
            
            // 基础数据
            case Url::MenuAll:  return "/api/menu/all";
            case Url::DictList: return "/api/dict/list";
        }
    }
    
    Method method() const override {
        switch (url_) {
            case Url::UserLogin:    return Method::POST;
            case Url::UserRegister: return Method::POST;
            case Url::UserLogout:   return Method::POST;
            case Url::UserProfile:  return Method::GET;
            case Url::UserUpdate:   return Method::PUT;
            case Url::OrderList:    return Method::GET;
            case Url::OrderSubmit:  return Method::POST;
            case Url::OrderCancel:  return Method::POST;
            case Url::PaymentCreate: return Method::POST;
            case Url::PaymentStatus: return Method::GET;
            case Url::MenuAll:      return Method::GET;
            case Url::DictList:     return Method::GET;
        }
    }
    
    bool needEncryption() const override {
        switch (url_) {
            case Url::UserLogin:    return true;
            case Url::UserRegister: return true;
            case Url::UserLogout:   return false;
            case Url::UserProfile:  return true;
            case Url::UserUpdate:   return true;
            case Url::OrderList:    return false;
            case Url::OrderSubmit:  return true;
            case Url::OrderCancel:  return false;
            case Url::PaymentCreate: return true;
            case Url::PaymentStatus: return true;
            case Url::MenuAll:      return false;
            case Url::DictList:     return false;
        }
    }
    
    bool needDuplicateCheck() const override {
        switch (url_) {
            case Url::UserLogin:    return false;  // 允许快速重试
            case Url::UserRegister: return true;
            case Url::UserLogout:   return false;
            case Url::UserProfile:  return false;
            case Url::UserUpdate:   return true;
            case Url::OrderList:    return false;
            case Url::OrderSubmit:  return true;
            case Url::OrderCancel:  return true;
            case Url::PaymentCreate: return true;
            case Url::PaymentStatus: return false;
            case Url::MenuAll:      return false;
            case Url::DictList:     return false;
        }
    }
    
    /**
     * 获取枚举名称（辅助方法）
     */
    std::string getName() const {
        switch (url_) {
            case Url::UserLogin:    return "USER_LOGIN";
            case Url::UserRegister: return "USER_REGISTER";
            case Url::UserLogout:   return "USER_LOGOUT";
            case Url::UserProfile: return "USER_PROFILE";
            case Url::UserUpdate:   return "USER_UPDATE";
            case Url::OrderList:    return "ORDER_LIST";
            case Url::OrderSubmit:  return "ORDER_SUBMIT";
            case Url::OrderCancel:  return "ORDER_CANCEL";
            case Url::PaymentCreate: return "PAYMENT_CREATE";
            case Url::PaymentStatus: return "PAYMENT_STATUS";
            case Url::MenuAll:      return "MENU_ALL";
            case Url::DictList:     return "DICT_LIST";
        }
    }
    
    /**
     * 获取枚举值
     */
    Url getUrl() const {
        return url_;
    }

private:
    Url url_;
};

} // namespace example
} // namespace httpclient

#endif // EXAMPLE_URL_H

