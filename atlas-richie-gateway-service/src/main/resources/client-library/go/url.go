// Package httpclient Go风格的URL配置
//
// Author: richie696
// Version: 2.0
// Since: 2025-11-01
package httpclient

import "strings"

// Method HTTP方法类型
type Method string

// HTTP方法常量
const (
	GET    Method = "GET"
	POST   Method = "POST"
	PUT    Method = "PUT"
	DELETE Method = "DELETE"
	PATCH  Method = "PATCH"
)

// UrlInterface URL接口 - 业务代码需要实现此接口
//
// Go语言没有枚举，但可以通过接口 + 类型别名 + 方法集来实现类似效果
//
// 业务代码实现示例：
// ```go
// type Url int
//
// const (
//     UserLogin Url = iota
//     OrderSubmit
// )
//
// func (u Url) Path() string {
//     switch u {
//     case UserLogin:
//         return "/api/auth/login"
//     case OrderSubmit:
//         return "/api/order/submit"
//     }
//     return ""
// }
//
// func (u Url) Method() Method { return POST }
// func (u Url) NeedEncryption() bool { return true }
// func (u Url) NeedDuplicateCheck() bool { return false }
// ```
type UrlInterface interface {
	// Path 获取URL路径
	Path() string
	
	// Method 获取HTTP方法
	Method() Method
	
	// NeedEncryption 是否需要加密
	NeedEncryption() bool
	
	// NeedDuplicateCheck 是否需要防重复提交检查
	NeedDuplicateCheck() bool
	
	// GetFullUrl 获取完整URL（可选实现，提供默认实现）
	GetFullUrl(baseUrl string) string
}

// GetFullUrl 默认实现GetFullUrl方法
// 业务代码可以实现此方法，或者使用此默认实现
func GetFullUrl(url UrlInterface, baseUrl string) string {
	base := strings.TrimSuffix(baseUrl, "/")
	path := url.Path()
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}
	return base + path
}

