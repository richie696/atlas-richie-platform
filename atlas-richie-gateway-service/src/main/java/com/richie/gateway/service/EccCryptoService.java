/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.service;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;

public interface EccCryptoService {

    /**
     * 检查请求路径是否需要加密
     *
     * @param path 请求路径
     * @return 是否需要加密
     */
    boolean shouldEncrypt(String path);

    /**
     * 缓存客户端公钥
     *
     * @param clientId 客户端ID
     * @param clientPublicKey 客户端公钥（Base64编码）
     */
    void cacheClientPublicKey(String clientId, String clientPublicKey);

    /**
     * 获取客户端公钥
     *
     * @param clientId 客户端ID
     * @return 客户端公钥
     */
    PublicKey getClientPublicKey(String clientId);

    /**
     * 获取或生成共享密钥
     *
     * @param clientId 客户端ID
     * @param gatewayPrivateKey 网关私钥
     * @return 共享密钥
     */
    SecretKey getOrGenerateSharedKey(String clientId, PrivateKey gatewayPrivateKey);

    /**
     * 解密请求数据
     *
     * @param encryptedData 加密的请求数据
     * @param clientId 客户端ID
     * @param gatewayPrivateKey 网关私钥
     * @return 解密后的请求数据
     */
    String decryptRequestData(String encryptedData, String clientId, PrivateKey gatewayPrivateKey);

    /**
     * 加密响应数据
     *
     * @param responseData 响应数据
     * @param clientId 客户端ID
     * @param gatewayPrivateKey 网关私钥
     * @return 加密后的响应数据
     */
    String encryptResponseData(String responseData, String clientId, PrivateKey gatewayPrivateKey);

    /**
     * 清理客户端相关缓存
     *
     * @param clientId 客户端ID
     */
    void clearClientCache(String clientId);
}
