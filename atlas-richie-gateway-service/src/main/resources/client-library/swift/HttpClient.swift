// Swift HTTP客户端
// 支持ECC+AES-GCM加密和防重复提交
//
// Author: richie696
// Version: 1.0
// Since: 2025-11-01

import Foundation
import CryptoKit
// 设备指纹工具（用于自动附加设备ID和硬件指纹头）
// 注意：使用前请确保将 DeviceFingerprint.swift 文件包含到工程中

// ==================== Swift原生enum ====================

/// HTTP方法枚举（Swift原生enum + RawValue）
enum Method: String, CaseIterable {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case delete = "DELETE"
    case patch = "PATCH"
}

// ==================== URL协议定义（业务代码需要遵循）====================

/// URL协议 - 业务代码的Url枚举需要遵循此协议
/// 
/// 业务代码应定义自己的Url枚举，并实现以下属性：
/// - path: URL路径
/// - method: HTTP方法
/// - needEncryption: 是否需要加密
/// - needDuplicateCheck: 是否需要防重复提交检查
/// 
/// 使用示例（业务代码）：
/// ```swift
/// enum Url: UrlProtocol {
///     case userLogin
///     
///     var path: String {
///         switch self {
///         case .userLogin: return "/api/auth/login"
///         }
///     }
///     
///     var method: Method { .post }
///     var needEncryption: Bool { true }
///     var needDuplicateCheck: Bool { false }
/// }
/// ```
protocol UrlProtocol {
    /// URL路径
    var path: String { get }
    
    /// HTTP方法
    var method: Method { get }
    
    /// 是否需要加密
    var needEncryption: Bool { get }
    
    /// 是否需要防重复提交检查
    var needDuplicateCheck: Bool { get }
    
    /// 获取完整URL（可选实现，库提供默认实现）
    func getFullUrl(baseUrl: String) -> String
}

/// 默认实现getFullUrl方法
extension UrlProtocol {
    func getFullUrl(baseUrl: String) -> String {
        let trimmedBase = baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let trimmedPath = path.hasPrefix("/") ? path : "/" + path
        return trimmedBase + trimmedPath
    }
}

// ==================== HTTP客户端配置 ====================

struct HttpClientConfig {
    var baseUrl: String = "https://your-gateway.com"
    var clientId: String = "ios-client-\(Date().timeIntervalSince1970)"
    var duplicateSubmitTimeWindow: Int64 = 3000
    var timeout: TimeInterval = 30.0
    /// 是否启用请求头自动管理（从UserDefaults读取和保存），默认true
    var enableHeaderAutoManagement: Bool = true
    /// UserDefaults中存储请求头的键名，默认"http_headers"
    var headerStorageKey: String = "http_headers"
}

// ==================== HTTP客户端 ====================

class HttpClient {
    private let config: HttpClientConfig
    private let eccModule: EccCryptoModule
    private let duplicateModule: DuplicateSubmitModule
    private let urlSession: URLSession
    
    // 系统内置的请求头，这些不应该被保存
    private static let systemHeaders: Set<String> = [
        "content-type", "content-length", "cache-control", "expires",
        "date", "server", "connection", "transfer-encoding",
        "accept", "accept-encoding", "accept-language", "referer",
        "user-agent", "origin",
        "x-client-id", "x-client-timestamp", "x-client-public-key",
        "x-gateway-keyid", "x-encrypted-data", "x-response-encrypted",
        "access-control-allow-origin", "access-control-allow-methods",
        "access-control-allow-headers", "access-control-expose-headers",
        "access-control-max-age", "access-control-allow-credentials"
    ]
    
    init(config: HttpClientConfig = HttpClientConfig()) {
        self.config = config
        self.eccModule = EccCryptoModule()
        self.duplicateModule = DuplicateSubmitModule(timeWindow: config.duplicateSubmitTimeWindow)
        
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = config.timeout
        self.urlSession = URLSession(configuration: configuration)
    }
    
    /// 发送HTTP请求
    /// 
    /// - Parameters:
    ///   - url: 遵循UrlProtocol的枚举值（业务代码定义）
    ///   - body: 请求体（可选）
    /// - Returns: 完整的ApiResult<T>对象
    /// - Throws: HttpClientError
    func request<T: Codable, U: UrlProtocol>(_ url: U, body: T? = nil as String?) async throws -> ApiResult<T> {
        let fullUrl = url.getFullUrl(baseUrl: config.baseUrl)
        
        // 1. 防重复提交检查
        var requestId: String?
        if url.needDuplicateCheck {
            let bodyStr = body != nil ? (try? JSONEncoder().encode(body).base64EncodedString()) ?? "" : ""
            let id = duplicateModule.generateRequestId(url: fullUrl, method: url.method.rawValue, body: bodyStr)
            
            if duplicateModule.isDuplicateRequest(requestId: id) {
                throw HttpClientError.duplicateRequest
            }
            
            duplicateModule.recordRequest(requestId: id, url: fullUrl)
            requestId = id
        }
        
        // 2. 发送请求
        let httpResponse: HTTPURLResponse
        let responseData: Data
        if url.needEncryption {
            (httpResponse, responseData) = try await sendEncryptedRequest(url: fullUrl, method: url.method, body: body)
        } else {
            (httpResponse, responseData) = try await sendPlainRequest(url: fullUrl, method: url.method, body: body)
        }
        
        // 3. 自动保存响应头到UserDefaults（在解析响应体之前，确保响应头可用）
        if config.enableHeaderAutoManagement {
            saveResponseHeaders(httpResponse: httpResponse)
        }
        
        // 4. 处理响应（完整透传ApiResult<T>，不做任何处理）
        let result = try handleResponse(data: responseData, httpResponse: httpResponse, needDecryption: url.needEncryption)
        
        // 5. 清理
        if let id = requestId {
            duplicateModule.clearRequest(requestId: id)
        }
        
        return result
    }
    
    private func sendEncryptedRequest<T: Codable>(url: String, method: Method, body: T?) async throws -> (HTTPURLResponse, Data) {
        // 确保已初始化
        if !eccModule.isInitialized() {
            try await eccModule.exchangeKeys(baseUrl: config.baseUrl, clientId: config.clientId)
        }
        
        guard let requestUrl = URL(string: url) else {
            throw HttpClientError.invalidUrl
        }
        
        // 读取缓存的请求头（包括token等业务请求头）
        let cachedHeaders = config.enableHeaderAutoManagement ? loadCachedHeaders() : [:]
        
        // 构建设备相关请求头（deviceId + 硬件指纹），用于网关侧的设备绑定和安全校验
        let deviceHeaders = buildDeviceHeaders()
        
        var request = URLRequest(url: requestUrl)
        request.httpMethod = method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(config.clientId, forHTTPHeaderField: "X-Client-Id")
        request.setValue(eccModule.gatewayKeyId, forHTTPHeaderField: "X-Gateway-KeyId")
        request.setValue("\(Date().timeIntervalSince1970 * 1000)", forHTTPHeaderField: "X-Client-Timestamp")
        
        // 添加设备相关请求头（deviceId + 硬件指纹）
        for (key, value) in deviceHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }
        
        // 添加缓存的请求头（包括token）
        for (key, value) in cachedHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }
        
        // 加密请求体
        if let body = body, method != .get {
            let jsonData = try JSONEncoder().encode(body)
            let jsonString = String(data: jsonData, encoding: .utf8)!
            let encryptedData = try eccModule.encrypt(plaintext: jsonString)
            request.setValue(encryptedData, forHTTPHeaderField: "X-Encrypted-Data")
            request.httpBody = jsonData
        }
        
        let (data, response) = try await urlSession.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw HttpClientError.invalidResponse
        }
        
        // 处理423状态码
        if httpResponse.statusCode == 423 {
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            if let needReHandshake = json?["needReHandshake"] as? Bool, needReHandshake,
               let keyId = json?["keyId"] as? String,
               let gatewayPublicKey = json?["gatewayPublicKey"] as? String {
                try await eccModule.reHandshake(keyId: keyId, gatewayPublicKey: gatewayPublicKey)
                return try await sendEncryptedRequest(url: url, method: method, body: body)
            }
        }
        
        return (httpResponse, data)
    }
    
    private func sendPlainRequest<T: Codable>(url: String, method: Method, body: T?) async throws -> (HTTPURLResponse, Data) {
        guard let requestUrl = URL(string: url) else {
            throw HttpClientError.invalidUrl
        }
        
        // 读取缓存的请求头（包括token等业务请求头）
        let cachedHeaders = config.enableHeaderAutoManagement ? loadCachedHeaders() : [:]
        
        // 构建设备相关请求头（deviceId + 硬件指纹），用于网关侧的设备绑定和安全校验
        let deviceHeaders = buildDeviceHeaders()
        
        var request = URLRequest(url: requestUrl)
        request.httpMethod = method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        // 添加设备相关请求头（deviceId + 硬件指纹）
        for (key, value) in deviceHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }
        
        // 添加缓存的请求头（包括token）
        for (key, value) in cachedHeaders {
            request.setValue(value, forHTTPHeaderField: key)
        }
        
        if let body = body, method != .get {
            request.httpBody = try JSONEncoder().encode(body)
        }
        
        let (data, response) = try await urlSession.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw HttpClientError.invalidResponse
        }
        
        return (httpResponse, data)
    }
    
    private func handleResponse<T: Codable>(data: Data, httpResponse: HTTPURLResponse, needDecryption: Bool) throws -> ApiResult<T> {
        // 先读取响应体（只能读取一次）
        var responseData = data
        
        // 解密响应
        if needDecryption, let encrypted = httpResponse.value(forHTTPHeaderField: "X-Response-Encrypted"), 
           encrypted == "true" {
            let encryptedString = String(data: data, encoding: .utf8)!
            let decrypted = try eccModule.decrypt(ciphertext: encryptedString)
            responseData = decrypted.data(using: .utf8)!
        }
        
        if httpResponse.statusCode != 200 {
            if httpResponse.statusCode == 429 {
                if let json = try? JSONSerialization.jsonObject(with: responseData) as? [String: Any],
                   let code = json["code"] as? String, code == "DUPLICATE_SUBMIT" {
                    throw HttpClientError.duplicateSubmit
                }
            }
            throw HttpClientError.httpError(httpResponse.statusCode)
        }
        
        // 完整透传ApiResult<T>，不做任何处理
        let decoder = JSONDecoder()
        return try decoder.decode(ApiResult<T>.self, from: responseData)
    }
    
    /// 从UserDefaults读取缓存的请求头
    private func loadCachedHeaders() -> [String: String] {
        guard let data = UserDefaults.standard.data(forKey: config.headerStorageKey),
              let headers = try? JSONDecoder().decode([String: String].self, from: data) else {
            return [:]
        }
        return headers
    }
    
    /// 保存响应头到UserDefaults
    /// 只保存业务相关的请求头，排除系统内置的header
    /// 网关签发的token（x-rd-request-apitoken）等业务请求头会自动保存
    private func saveResponseHeaders(httpResponse: HTTPURLResponse) {
        var businessHeaders: [String: String] = [:]
        
        // 遍历所有响应头
        for (key, value) in httpResponse.allHeaderFields {
            guard let keyStr = key as? String,
                  let valueStr = value as? String else {
                continue
            }
            
            let lowerKey = keyStr.lowercased()
            
            // 排除系统内置的header，其他所有业务请求头都保存（包括x-rd-request-apitoken等）
            if !HttpClient.systemHeaders.contains(lowerKey) {
                businessHeaders[keyStr] = valueStr
            }
        }
        
        // 如果有业务请求头，保存到UserDefaults
        if !businessHeaders.isEmpty {
            let existingHeaders = loadCachedHeaders()
            let mergedHeaders = existingHeaders.merging(businessHeaders) { (_, new) in new }
            
            if let data = try? JSONEncoder().encode(mergedHeaders) {
                UserDefaults.standard.set(data, forKey: config.headerStorageKey)
                print("[HttpClient] 已保存响应头到UserDefaults: \(businessHeaders)")
            }
        }
    }

    // MARK: - 设备指纹集成
    
    /// 构建设备相关请求头
    /// 自动生成并附加以下请求头：
    /// - X-Device-Id：设备ID（基于IDFV和硬件特征生成并持久化）
    /// - X-Hardware-Fingerprint：硬件指纹（每次请求动态生成）
    private func buildDeviceHeaders() -> [String: String] {
        var headers: [String: String] = [:]
        
        // 设备ID（兼容旧版本）
        let deviceId = DeviceFingerprint.getOrCreateDeviceId()
        if !deviceId.isEmpty {
            headers["X-Device-Id"] = deviceId
        }
        
        // 硬件指纹（动态生成）
        let fingerprint = DeviceFingerprint.generateHardwareFingerprint()
        if let fingerprintJson = DeviceFingerprint.fingerprintToString(fingerprint) {
            headers["X-Hardware-Fingerprint"] = fingerprintJson
        }
        
        return headers
    }
}

// ==================== 错误定义 ====================

enum HttpClientError: Error {
    case invalidUrl
    case invalidResponse
    case duplicateRequest
    case duplicateSubmit
    case httpError(Int)
    case encryptionError(String)
}

// ==================== ECC加密模块 ====================

class EccCryptoModule {
    private var privateKey: P256.KeyAgreement.PrivateKey?
    private var sharedSecret: SymmetricKey?
    private(set) var gatewayKeyId: String?
    
    func isInitialized() -> Bool {
        return sharedSecret != nil && gatewayKeyId != nil
    }
    
    func exchangeKeys(baseUrl: String, clientId: String) async throws {
        // 生成客户端密钥对
        privateKey = P256.KeyAgreement.PrivateKey()
        let publicKeyData = privateKey!.publicKey.x963Representation
        let clientPublicKey = publicKeyData.base64EncodedString()
        
        // 发送密钥交换请求
        guard let url = URL(string: "\(baseUrl)/api/crypto/exchange") else {
            throw HttpClientError.invalidUrl
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(clientPublicKey, forHTTPHeaderField: "X-Client-Public-Key")
        request.setValue(clientId, forHTTPHeaderField: "X-Client-Id")
        
        let (data, _) = try await URLSession.shared.data(for: request)
        let json = try JSONSerialization.jsonObject(with: data) as! [String: String]
        
        gatewayKeyId = json["keyId"]
        let gatewayPublicKeyB64 = json["gatewayPublicKey"]!
        
        // 计算共享密钥
        let gatewayPublicKeyData = Data(base64Encoded: gatewayPublicKeyB64)!
        let gatewayPublicKey = try P256.KeyAgreement.PublicKey(x963Representation: gatewayPublicKeyData)
        
        sharedSecret = try privateKey!.sharedSecretFromKeyAgreement(with: gatewayPublicKey)
        print("[ECC] 密钥交换成功, KeyId: \(gatewayKeyId!)")
    }
    
    func reHandshake(keyId: String, gatewayPublicKey: String) async throws {
        gatewayKeyId = keyId
        let gatewayPublicKeyData = Data(base64Encoded: gatewayPublicKey)!
        let publicKey = try P256.KeyAgreement.PublicKey(x963Representation: gatewayPublicKeyData)
        sharedSecret = try privateKey!.sharedSecretFromKeyAgreement(with: publicKey)
    }
    
    func encrypt(plaintext: String) throws -> String {
        guard let secret = sharedSecret else {
            throw HttpClientError.encryptionError("共享密钥未初始化")
        }
        
        let key = SymmetricKey(data: secret.withUnsafeBytes { Data($0.prefix(32)) })
        let nonce = AES.GCM.Nonce()
        let data = plaintext.data(using: .utf8)!
        
        let sealedBox = try AES.GCM.seal(data, using: key, nonce: nonce)
        let combined = sealedBox.nonce + sealedBox.ciphertext + sealedBox.tag
        return combined.base64EncodedString()
    }
    
    func decrypt(ciphertext: String) throws -> String {
        guard let secret = sharedSecret else {
            throw HttpClientError.encryptionError("共享密钥未初始化")
        }
        
        guard let combined = Data(base64Encoded: ciphertext) else {
            throw HttpClientError.encryptionError("Base64解码失败")
        }
        
        let key = SymmetricKey(data: secret.withUnsafeBytes { Data($0.prefix(32)) })
        let nonce = try AES.GCM.Nonce(data: combined.prefix(12))
        let ciphertextData = combined.dropFirst(12).dropLast(16)
        let tag = combined.suffix(16)
        
        let sealedBox = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertextData, tag: tag)
        let decryptedData = try AES.GCM.open(sealedBox, using: key)
        
        return String(data: decryptedData, encoding: .utf8)!
    }
}

// ==================== 防重复提交模块 ====================

class DuplicateSubmitModule {
    private var requestQueue: [String: RequestInfo] = [:]
    private let timeWindow: Int64
    private let lock = NSLock()
    
    init(timeWindow: Int64) {
        self.timeWindow = timeWindow
    }
    
    func generateRequestId(url: String, method: String, body: String) -> String {
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000) / timeWindow * timeWindow
        let data = "\(url)\(method)\(timestamp)\(body)"
        return data.md5Hash
    }
    
    func isDuplicateRequest(requestId: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        
        guard let info = requestQueue[requestId] else {
            return false
        }
        
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        return now - info.timestamp < timeWindow
    }
    
    func recordRequest(requestId: String, url: String) {
        lock.lock()
        defer { lock.unlock() }
        
        requestQueue[requestId] = RequestInfo(
            timestamp: Int64(Date().timeIntervalSince1970 * 1000),
            url: url
        )
        cleanupExpired()
    }
    
    func clearRequest(requestId: String) {
        lock.lock()
        defer { lock.unlock() }
        requestQueue.removeValue(forKey: requestId)
    }
    
    private func cleanupExpired() {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let expiredTime = now - timeWindow
        requestQueue = requestQueue.filter { $0.value.timestamp >= expiredTime }
    }
}

struct RequestInfo {
    let timestamp: Int64
    let url: String
}

// ==================== 工具扩展 ====================

extension String {
    var md5Hash: String {
        let data = Data(self.utf8)
        let hash = Insecure.MD5.hash(data: data)
        return hash.map { String(format: "%02hhx", $0) }.joined()
    }
}

