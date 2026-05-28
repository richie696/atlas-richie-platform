//
// 设备指纹生成工具（高安全版）
// 提供高安全性的设备识别方案，适用于 iOS 原生应用
//
// Author: richie696
// Version: 2.0
// Since: 1.0.0
//

import Foundation
import UIKit
import CryptoKit

/// 硬件指纹特征结构体
public struct HardwareFingerprint: Codable {
    /// 设备标识符（IDFV）
    public let identifierForVendor: String
    /// 设备型号（如：iPhone15,2）
    public let model: String
    /// 设备名称（如：iPhone）
    public let deviceName: String
    /// 系统版本（如：17.0）
    public let systemVersion: String
    /// 系统名称（如：iOS）
    public let systemName: String
    /// 屏幕宽度（points）
    public let screenWidth: CGFloat
    /// 屏幕高度（points）
    public let screenHeight: CGFloat
    /// 屏幕缩放因子
    public let screenScale: CGFloat
    /// 设备类型（iPhone、iPad等）
    public let deviceType: String
    /// 总内存（字节）
    public let totalMemory: UInt64
    /// CPU核心数
    public let cpuCount: Int
}

/// 设备指纹工具类
public class DeviceFingerprint {
    
    /// 存储键名（Keychain）
    private static let keychainService = "com.richie.device.fingerprint"
    private static let keychainKey = "device_fingerprint"
    
    /// UserDefaults 键名（备用）
    private static let userDefaultsKey = "richie_device_fingerprint"
    
    /// 获取或创建设备指纹
    /// - Returns: 设备指纹对象
    public static func getOrCreateDeviceFingerprint() -> HardwareFingerprint {
        // 1. 尝试从 Keychain 读取（最安全）
        if let storedFingerprint = loadFingerprintFromKeychain() {
            return storedFingerprint
        }
        
        // 2. 尝试从 UserDefaults 读取（备用）
        if let storedFingerprint = loadFingerprintFromUserDefaults() {
            // 迁移到 Keychain
            saveFingerprintToKeychain(storedFingerprint)
            return storedFingerprint
        }
        
        // 3. 生成新的设备指纹
        let fingerprint = generateHardwareFingerprint()
        
        // 4. 保存到 Keychain（优先）和 UserDefaults（备用）
        saveFingerprintToKeychain(fingerprint)
        saveFingerprintToUserDefaults(fingerprint)
        
        return fingerprint
    }
    
    /// 生成动态硬件指纹（每次请求时调用）
    /// - Returns: 硬件指纹对象
    public static func generateHardwareFingerprint() -> HardwareFingerprint {
        let device = UIDevice.current
        let screen = UIScreen.main
        
        // 获取设备标识符（IDFV）
        let identifierForVendor = device.identifierForVendor?.uuidString ?? UUID().uuidString
        
        // 获取设备型号
        var systemInfo = utsname()
        uname(&systemInfo)
        let modelCode = withUnsafePointer(to: &systemInfo.machine) {
            $0.withMemoryRebound(to: CChar.self, capacity: 1) {
                String(validatingUTF8: $0)
            }
        } ?? "unknown"
        
        // 获取设备名称
        let deviceName = device.name
        
        // 获取系统信息
        let systemVersion = device.systemVersion
        let systemName = device.systemName
        
        // 获取屏幕信息
        let screenWidth = screen.bounds.width
        let screenHeight = screen.bounds.height
        let screenScale = screen.scale
        
        // 获取设备类型
        let deviceType = device.model
        
        // 获取内存信息
        let totalMemory = ProcessInfo.processInfo.physicalMemory
        
        // 获取CPU核心数
        let cpuCount = ProcessInfo.processInfo.processorCount
        
        return HardwareFingerprint(
            identifierForVendor: identifierForVendor,
            model: modelCode,
            deviceName: deviceName,
            systemVersion: systemVersion,
            systemName: systemName,
            screenWidth: screenWidth,
            screenHeight: screenHeight,
            screenScale: screenScale,
            deviceType: deviceType,
            totalMemory: totalMemory,
            cpuCount: cpuCount
        )
    }
    
    /// 将硬件指纹转换为字符串（用于传输）
    /// - Parameter fingerprint: 硬件指纹对象
    /// - Returns: JSON字符串
    public static func fingerprintToString(_ fingerprint: HardwareFingerprint) -> String? {
        guard let jsonData = try? JSONEncoder().encode(fingerprint) else {
            return nil
        }
        return String(data: jsonData, encoding: .utf8)
    }
    
    /// 从字符串解析硬件指纹
    /// - Parameter fingerprintStr: JSON字符串
    /// - Returns: 硬件指纹对象
    public static func fingerprintFromString(_ fingerprintStr: String) -> HardwareFingerprint? {
        guard let jsonData = fingerprintStr.data(using: .utf8) else {
            return nil
        }
        return try? JSONDecoder().decode(HardwareFingerprint.self, from: jsonData)
    }
    
    /// 生成硬件指纹的哈希值（用于快速比较）
    /// - Parameter fingerprint: 硬件指纹对象
    /// - Returns: SHA-256哈希值（64字符）
    public static func fingerprintToHash(_ fingerprint: HardwareFingerprint) -> String {
        guard let jsonString = fingerprintToString(fingerprint),
              let jsonData = jsonString.data(using: .utf8) else {
            return ""
        }
        
        let hash = SHA256.hash(data: jsonData)
        return hash.compactMap { String(format: "%02x", $0) }.joined()
    }
    
    /// 计算两个硬件指纹的相似度
    /// - Parameters:
    ///   - fingerprint1: 第一个硬件指纹
    ///   - fingerprint2: 第二个硬件指纹
    /// - Returns: 相似度（0-1之间，1表示完全匹配）
    public static func calculateFingerprintSimilarity(
        _ fingerprint1: HardwareFingerprint,
        _ fingerprint2: HardwareFingerprint
    ) -> Double {
        var score: Double = 0
        var totalWeight: Double = 0
        
        // IDFV（权重最高，必须匹配）
        let idfvWeight = 0.4
        totalWeight += idfvWeight
        if fingerprint1.identifierForVendor == fingerprint2.identifierForVendor {
            score += idfvWeight
        }
        
        // 设备型号（权重高）
        let modelWeight = 0.2
        totalWeight += modelWeight
        if fingerprint1.model == fingerprint2.model {
            score += modelWeight
        }
        
        // 屏幕分辨率（权重中等）
        let screenWeight = 0.15
        totalWeight += screenWeight
        if abs(fingerprint1.screenWidth - fingerprint2.screenWidth) < 1 &&
           abs(fingerprint1.screenHeight - fingerprint2.screenHeight) < 1 {
            score += screenWeight
        }
        
        // 屏幕缩放因子（权重中等）
        let scaleWeight = 0.1
        totalWeight += scaleWeight
        if abs(fingerprint1.screenScale - fingerprint2.screenScale) < 0.1 {
            score += scaleWeight
        }
        
        // 系统版本（权重较低，允许更新）
        let systemWeight = 0.1
        totalWeight += systemWeight
        if fingerprint1.systemVersion == fingerprint2.systemVersion {
            score += systemWeight
        } else {
            // 主版本号匹配（如 17.0 和 17.1）
            let version1 = fingerprint1.systemVersion.split(separator: ".").first ?? ""
            let version2 = fingerprint2.systemVersion.split(separator: ".").first ?? ""
            if version1 == version2 && !version1.isEmpty {
                score += systemWeight * 0.5
            }
        }
        
        // 总内存（权重较低，允许小幅变化）
        let memoryWeight = 0.05
        totalWeight += memoryWeight
        let memoryDiff = abs(Int64(fingerprint1.totalMemory) - Int64(fingerprint2.totalMemory))
        let memoryTolerance = Int64(fingerprint1.totalMemory) / 100 // 允许1%的误差
        if memoryDiff <= memoryTolerance {
            score += memoryWeight
        }
        
        return totalWeight > 0 ? score / totalWeight : 0
    }
    
    /// 获取设备名称（用于显示）
    /// - Returns: 设备名称（例如："iPhone 15 Pro on iOS"）
    public static func getDeviceName() -> String {
        let device = UIDevice.current
        let deviceName = device.name
        let systemName = device.systemName
        return "\(deviceName) on \(systemName)"
    }
    
    /// 获取设备ID（兼容旧版本）
    /// - Returns: 设备ID（SHA-256哈希，64字符）
    public static func getOrCreateDeviceId() -> String {
        let fingerprint = getOrCreateDeviceFingerprint()
        return fingerprintToHash(fingerprint)
    }
    
    /// 清除设备指纹
    public static func clearDeviceFingerprint() {
        deleteFingerprintFromKeychain()
        UserDefaults.standard.removeObject(forKey: userDefaultsKey)
    }
    
    // MARK: - Private Methods
    
    /// 从 Keychain 加载设备指纹
    private static func loadFingerprintFromKeychain() -> HardwareFingerprint? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainKey,
            kSecReturnData as String: true
        ]
        
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        
        guard status == errSecSuccess,
              let data = result as? Data,
              let fingerprint = try? JSONDecoder().decode(HardwareFingerprint.self, from: data) else {
            return nil
        }
        
        return fingerprint
    }
    
    /// 保存设备指纹到 Keychain
    private static func saveFingerprintToKeychain(_ fingerprint: HardwareFingerprint) {
        guard let data = try? JSONEncoder().encode(fingerprint) else {
            return
        }
        
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainKey,
            kSecValueData as String: data
        ]
        
        // 删除旧数据
        SecItemDelete(query as CFDictionary)
        
        // 添加新数据
        SecItemAdd(query as CFDictionary, nil)
    }
    
    /// 从 Keychain 删除设备指纹
    private static func deleteFingerprintFromKeychain() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainKey
        ]
        
        SecItemDelete(query as CFDictionary)
    }
    
    /// 从 UserDefaults 加载设备指纹（备用）
    private static func loadFingerprintFromUserDefaults() -> HardwareFingerprint? {
        guard let data = UserDefaults.standard.data(forKey: userDefaultsKey),
              let fingerprint = try? JSONDecoder().decode(HardwareFingerprint.self, from: data) else {
            return nil
        }
        
        return fingerprint
    }
    
    /// 保存设备指纹到 UserDefaults（备用）
    private static func saveFingerprintToUserDefaults(_ fingerprint: HardwareFingerprint) {
        guard let data = try? JSONEncoder().encode(fingerprint) else {
            return
        }
        
        UserDefaults.standard.set(data, forKey: userDefaultsKey)
    }
}
