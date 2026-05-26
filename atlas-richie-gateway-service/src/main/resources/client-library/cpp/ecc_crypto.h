/**
 * ecc_crypto.h
 * ECC加密模块头文件
 *
 * Author: richie696
 * Version: 1.0
 * Since: 2025-11-01
 */

#ifndef ECC_CRYPTO_H
#define ECC_CRYPTO_H

#include <string>
#include <memory>

namespace httpclient {

/**
 * ECC加密模块
 * 负责ECC密钥交换和AES-GCM加密/解密
 */
class EccCryptoModule {
public:
    EccCryptoModule();
    ~EccCryptoModule();
    
    /**
     * 是否已初始化
     */
    bool isInitialized() const;
    
    /**
     * 密钥交换
     * @param baseUrl 网关基础URL
     * @param clientId 客户端ID
     * @return 是否成功
     */
    bool exchangeKeys(const std::string& baseUrl, const std::string& clientId);
    
    /**
     * 重新握手
     */
    bool reHandshake(const std::string& keyId, const std::string& gatewayPublicKey);
    
    /**
     * 加密数据
     */
    std::string encrypt(const std::string& plaintext);
    
    /**
     * 解密数据
     */
    std::string decrypt(const std::string& ciphertext);
    
    /**
     * 获取网关KeyId
     */
    std::string getGatewayKeyId() const;

private:
    class Impl;
    std::unique_ptr<Impl> pImpl_;
};

} // namespace httpclient

#endif // ECC_CRYPTO_H

