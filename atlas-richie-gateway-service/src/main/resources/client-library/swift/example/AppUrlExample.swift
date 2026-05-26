// UrlExample.swift
// Swift原生enum使用示例
//
// Author: richie696
// Version: 2.0
// Since: 2025-11-01

import Foundation

// 注意：导入业务代码定义的Url枚举（示例中使用example目录下的Url）
// 在实际项目中，应该是：import YourApp.Url

/// 示例：展示如何使用Swift原生enum定义的Url
class UrlExample {
    
    private let client = HttpClient(config: HttpClientConfig(
        baseUrl: "https://your-gateway.com",
        clientId: "ios-app"
    ))
    
    // ========== 示例1: 登录（加密，不防重复）==========
    func exampleLogin(username: String, password: String) async {
        do {
            struct LoginBody: Codable {
                let username: String
                let password: String
            }
            
            struct User: Codable {
                let userId: String
                let username: String
                let email: String
            }
            
            let loginData = LoginBody(username: username, password: password)
            
            // 直接使用enum，简洁优雅！
            let result: ApiResult<User> = try await client.request(Url.userLogin, body: loginData)
            
            if result.isSuccess {
                do {
                    let userData = try result.extractData()
                    print("登录成功: \(userData)")
                    // 注意：token现在从响应头自动获取并保存到UserDefaults，无需手动处理
                } catch {
                    print("数据解析失败: \(error.localizedDescription)")
                }
            } else {
                print("登录失败: \(result.msg ?? "未知错误")")
            }
        } catch HttpClientError.duplicateRequest {
            print("请求正在处理中，请稍候")
        } catch {
            print("登录失败: \(error.localizedDescription)")
        }
    }
    
    // ========== 示例2: 订单提交（加密 + 防重复）==========
    func exampleOrderSubmit(orderData: [String: Any]) async {
        do {
            struct OrderResult: Codable {
                let orderId: String
                let totalAmount: Double
            }
            
            let result: ApiResult<OrderResult> = try await client.request(Url.orderSubmit, body: orderData)
            
            if result.isSuccess {
                do {
                    let orderData = try result.extractData()
                    print("订单提交成功: \(orderData)")
                    print("订单ID: \(orderData.orderId)")
                } catch {
                    print("数据解析失败: \(error.localizedDescription)")
                }
            } else {
                print("订单提交失败: \(result.msg ?? "未知错误")")
            }
        } catch HttpClientError.duplicateRequest {
            print("检测到重复提交，请求被拒绝")
        } catch HttpClientError.duplicateSubmit {
            print("服务器检测到重复提交")
        } catch {
            print("订单提交失败: \(error.localizedDescription)")
        }
    }
    
    // ========== 示例3: 获取菜单（公开数据）==========
    func exampleGetMenu() async {
        do {
            // 公开数据，不需要加密和防重复
            let result = try await client.request(Url.menuAll, body: nil as String?)
            
            print("菜单列表: \(result)")
        } catch {
            print("获取菜单失败: \(error.localizedDescription)")
        }
    }
    
    // ========== 示例4: 演示enum方法和属性 ==========
    func demonstrateEnumFeatures() {
        let url = Url.userLogin
        
        // 获取枚举属性
        print("枚举名称: \(url.name)")
        print("URL路径: \(url.path)")
        print("HTTP方法: \(url.method)")
        print("需要加密: \(url.needEncryption)")
        print("需要防重复: \(url.needDuplicateCheck)")
        print("是否写操作: \(url.isWriteOperation)")
        print("完整URL: \(url.getFullUrl(baseUrl: "https://api.example.com"))")
        
        // switch匹配（穷尽检查）
        switch url {
        case .userLogin:
            print("这是登录接口")
        case .orderSubmit:
            print("这是订单提交接口")
        case .menuAll:
            print("这是菜单接口")
        default:
            print("其他接口")
        }
    }
    
    // ========== 示例5: 模式匹配 ==========
    func demonstratePatternMatching() {
        let urls: [Url] = [.userLogin, .orderSubmit, .menuAll]
        
        for url in urls {
            switch url {
            case .userLogin, .userRegister:
                print("\(url.name) - 认证相关接口")
            case .orderSubmit, .orderCancel:
                print("\(url.name) - 订单相关接口")
            case .menuAll, .dictList:
                print("\(url.name) - 基础数据接口")
            default:
                print("\(url.name) - 其他接口")
            }
        }
    }
}

