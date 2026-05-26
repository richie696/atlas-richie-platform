// LoginView.swift
// iOS登录界面示例
//
// Author: richie696
// Version: 1.0
// Since: 2025-11-01

import SwiftUI

// 注意：导入业务代码定义的Url枚举（示例中使用example目录下的Url）
// 在实际项目中，应该是：import YourApp.Url
// 导入Result结构
// import YourApp.ApiResult

struct LoginView: View {
    @StateObject private var viewModel = LoginViewModel()
    @State private var username = ""
    @State private var password = ""
    
    var body: some View {
        VStack(spacing: 20) {
            Text("用户登录")
                .font(.largeTitle)
                .fontWeight(.bold)
            
            TextField("用户名", text: $username)
                .textFieldStyle(.roundedBorder)
                .autocapitalization(.none)
            
            SecureField("密码", text: $password)
                .textFieldStyle(.roundedBorder)
            
            if let error = viewModel.errorMessage {
                Text(error)
                    .foregroundColor(.red)
                    .font(.caption)
            }
            
            Button(action: {
                Task {
                    await viewModel.login(username: username, password: password)
                }
            }) {
                if viewModel.isLoading {
                    ProgressView()
                } else {
                    Text("登录")
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.isLoading)
        }
        .padding()
    }
}

// ========== ViewModel ==========

@MainActor
class LoginViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?
    
    private let client = HttpClient(config: HttpClientConfig(
        baseUrl: "https://your-gateway.com",
        clientId: "ios-app"
    ))
    
    func login(username: String, password: String) async {
        isLoading = true
        errorMessage = nil
        
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
            let result: ApiResult<User> = try await client.request(Url.userLogin, body: loginData)
            
            if result.isSuccess {
                do {
                    let userData = try result.extractData()
                    print("登录成功: \(userData)")
                    // 注意：token现在从响应头自动获取并保存到UserDefaults，无需手动处理
                } catch {
                    errorMessage = "数据解析失败: \(error.localizedDescription)"
                }
            } else {
                errorMessage = result.msg ?? "登录失败"
            }
        } catch HttpClientError.duplicateRequest {
            errorMessage = "请求正在处理中，请稍候"
        } catch {
            errorMessage = "登录失败: \(error.localizedDescription)"
        }
        
        isLoading = false
    }
}

