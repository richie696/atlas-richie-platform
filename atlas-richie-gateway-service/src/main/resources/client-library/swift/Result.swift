// Result 通用响应结构
// 对应服务端 Java ResultVO<T>
//
// Author: richie696
// Version: 1.0
// Since: 2025-11-11

import Foundation

/// 国际化字典类型
public typealias I18nDict = [String: [String: String]]

/// 通用响应结果结构
/// 对应服务端 ResultVO<T>
///
/// - Parameter T: 响应数据的类型（Codable）
///
/// 使用示例：
/// ```swift
/// struct User: Codable {
///     let id: Int
///     let name: String
/// }
///
/// let result: ApiResult<User> = try await client.request(Url.userInfo)
/// if result.isSuccess {
///     print("用户信息:", result.data)
/// } else {
///     print("错误:", result.msg ?? "未知错误")
/// }
/// ```
public struct ApiResult<T: Codable>: Codable {
    /// 结果数据
    public let data: T?
    /// 结果代码，成功通常为 "200" 或 "SUCCESS"
    public let code: String
    /// 错误信息或提示信息
    public let msg: String?
    /// 国际化字典
    public let i18nDict: I18nDict?
    /// 时间戳（毫秒）
    public let timestamp: Int64
    
    /// 判断是否成功
    public var isSuccess: Bool {
        return code == "200" || code == "SUCCESS" || code == "0"
    }
    
    /// 判断是否失败
    public var isError: Bool {
        return !isSuccess
    }
    
    /// 提取数据，如果失败则抛出错误
    /// - Returns: 数据部分
    /// - Throws: 如果结果失败，抛出包含错误信息的Error
    public func extractData() throws -> T {
        guard isSuccess, let data = data else {
            throw ApiResultError.failed(code: code, message: msg ?? "操作失败")
        }
        return data
    }
}

/// Result错误类型
public enum ApiResultError: Error {
    case failed(code: String, message: String)
    
    public var localizedDescription: String {
        switch self {
        case .failed(let code, let message):
            return "操作失败，错误代码: \(code), 错误信息: \(message)"
        }
    }
}

/// 判断ApiResult是否成功
/// - Parameter result: ApiResult对象
/// - Returns: 返回true表示成功，false表示失败
public func isSuccess<T: Codable>(_ result: ApiResult<T>) -> Bool {
    return result.isSuccess
}

/// 判断ApiResult是否失败
/// - Parameter result: ApiResult对象
/// - Returns: 返回true表示失败，false表示成功
public func isError<T: Codable>(_ result: ApiResult<T>) -> Bool {
    return result.isError
}

/// 从ApiResult中提取数据，如果失败则抛出错误
/// - Parameter result: ApiResult对象
/// - Returns: 返回数据部分
/// - Throws: 如果结果失败，抛出包含错误信息的Error
public func extractData<T: Codable>(_ result: ApiResult<T>) throws -> T {
    return try result.extractData()
}

