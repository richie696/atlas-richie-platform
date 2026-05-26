// Package example 业务代码：URL枚举定义示例
// 
// Go语言没有原生枚举，但可以通过接口 + 类型别名 + 方法集来实现类似效果
//
// Author: richie696
// Version: 2.0
// Since: 2025-11-01
package example

import "your-module/httpclient"

// Url URL类型（Go风格：类型别名 + 常量）
// 使用int作为底层类型，通过常量定义枚举值
type Url int

// URL枚举常量（Go惯用法：iota自动递增）
const (
	// ========== 认证模块 ==========
	// UserLogin 用户登录 - 需要加密，不需要防重复提交（允许快速重试）
	UserLogin Url = iota
	
	// UserRegister 用户注册 - 需要加密和防重复提交
	UserRegister
	
	// UserLogout 用户登出
	UserLogout
	
	// ========== 用户模块 ==========
	// UserProfile 获取用户信息 - 需要加密
	UserProfile
	
	// UserUpdate 更新用户信息 - 需要加密和防重复提交
	UserUpdate
	
	// ========== 订单模块 ==========
	// OrderList 订单列表 - 公开数据
	OrderList
	
	// OrderSubmit 提交订单 - 需要加密和防重复提交
	OrderSubmit
	
	// OrderCancel 取消订单 - 需要防重复提交
	OrderCancel
	
	// ========== 支付模块 ==========
	// PaymentCreate 创建支付 - 最高安全级别
	PaymentCreate
	
	// PaymentStatus 查询支付状态 - 需要加密
	PaymentStatus
	
	// ========== 基础数据 ==========
	// MenuAll 菜单列表 - 公开数据
	MenuAll
	
	// DictList 字典数据 - 公开数据
	DictList
)

// Path 实现UrlInterface接口：获取URL路径
func (u Url) Path() string {
	switch u {
	// 认证模块
	case UserLogin:
		return "/api/auth/login"
	case UserRegister:
		return "/api/auth/register"
	case UserLogout:
		return "/api/auth/logout"
	
	// 用户模块
	case UserProfile:
		return "/api/user/profile"
	case UserUpdate:
		return "/api/user/update"
	
	// 订单模块
	case OrderList:
		return "/api/order/list"
	case OrderSubmit:
		return "/api/order/submit"
	case OrderCancel:
		return "/api/order/cancel"
	
	// 支付模块
	case PaymentCreate:
		return "/api/payment/create"
	case PaymentStatus:
		return "/api/payment/status"
	
	// 基础数据
	case MenuAll:
		return "/api/menu/all"
	case DictList:
		return "/api/dict/list"
	
	default:
		return ""
	}
}

// Method 实现UrlInterface接口：获取HTTP方法
func (u Url) Method() httpclient.Method {
	switch u {
	case UserLogin:
		return httpclient.POST
	case UserRegister:
		return httpclient.POST
	case UserLogout:
		return httpclient.POST
	case UserProfile:
		return httpclient.GET
	case UserUpdate:
		return httpclient.PUT
	case OrderList:
		return httpclient.GET
	case OrderSubmit:
		return httpclient.POST
	case OrderCancel:
		return httpclient.POST
	case PaymentCreate:
		return httpclient.POST
	case PaymentStatus:
		return httpclient.GET
	case MenuAll:
		return httpclient.GET
	case DictList:
		return httpclient.GET
	default:
		return httpclient.GET
	}
}

// NeedEncryption 实现UrlInterface接口：是否需要加密
func (u Url) NeedEncryption() bool {
	switch u {
	case UserLogin:
		return true
	case UserRegister:
		return true
	case UserLogout:
		return false
	case UserProfile:
		return true
	case UserUpdate:
		return true
	case OrderList:
		return false
	case OrderSubmit:
		return true
	case OrderCancel:
		return false
	case PaymentCreate:
		return true
	case PaymentStatus:
		return true
	case MenuAll:
		return false
	case DictList:
		return false
	default:
		return false
	}
}

// NeedDuplicateCheck 实现UrlInterface接口：是否需要防重复提交检查
func (u Url) NeedDuplicateCheck() bool {
	switch u {
	case UserLogin:
		return false // 允许快速重试
	case UserRegister:
		return true
	case UserLogout:
		return false
	case UserProfile:
		return false
	case UserUpdate:
		return true
	case OrderList:
		return false
	case OrderSubmit:
		return true
	case OrderCancel:
		return true
	case PaymentCreate:
		return true
	case PaymentStatus:
		return false
	case MenuAll:
		return false
	case DictList:
		return false
	default:
		return false
	}
}

// GetFullUrl 实现UrlInterface接口：获取完整URL
// 可以使用默认实现，或自定义实现
func (u Url) GetFullUrl(baseUrl string) string {
	return httpclient.GetFullUrl(u, baseUrl)
}

// ========== 可选的辅助方法 ==========

// Name 获取枚举名称（用于调试）
func (u Url) Name() string {
	switch u {
	case UserLogin:
		return "USER_LOGIN"
	case UserRegister:
		return "USER_REGISTER"
	case UserLogout:
		return "USER_LOGOUT"
	case UserProfile:
		return "USER_PROFILE"
	case UserUpdate:
		return "USER_UPDATE"
	case OrderList:
		return "ORDER_LIST"
	case OrderSubmit:
		return "ORDER_SUBMIT"
	case OrderCancel:
		return "ORDER_CANCEL"
	case PaymentCreate:
		return "PAYMENT_CREATE"
	case PaymentStatus:
		return "PAYMENT_STATUS"
	case MenuAll:
		return "MENU_ALL"
	case DictList:
		return "DICT_LIST"
	default:
		return "UNKNOWN"
	}
}

// IsWriteOperation 判断是否为写操作
func (u Url) IsWriteOperation() bool {
	method := u.Method()
	return method == httpclient.POST || method == httpclient.PUT ||
		method == httpclient.DELETE || method == httpclient.PATCH
}

// String 实现Stringer接口
func (u Url) String() string {
	return u.Name()
}

