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
package com.richie.gateway.service.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.gateway.config.EccCryptoConfig;
import com.richie.gateway.constants.GatewayRedisKey;
import com.richie.gateway.service.EccCryptoService;
import com.richie.gateway.utils.EccCryptoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.concurrent.TimeUnit;

/**
 * ECC加密服务类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EccCryptoServiceImpl implements EccCryptoService {

    private final EccCryptoConfig config;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 检查请求路径是否需要加密
     *
     * @param path 请求路径
     * @return 是否需要加密
     */
    @Override
    public boolean shouldEncrypt(String path) {
        if (!config.isEnabled()) {
            return false;
        }

        // 检查排除路径
        for (String excludePath : config.getExcludePaths()) {
            if (pathMatcher.match(excludePath, path)) {
                return false;
            }
        }

        // 检查包含路径
        for (String encryptPath : config.getEncryptPaths()) {
            if (pathMatcher.match(encryptPath, path)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 缓存客户端公钥
     *
     * @param clientId      客户端ID
     * @param clientPublicKey 客户端公钥（Base64编码）
     */
    @Override
    public void cacheClientPublicKey(String clientId, String clientPublicKey) {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientPublicKey)) {
            return;
        }

        String cacheKey = GatewayRedisKey.ECC_CLIENT_PUBLIC_KEY.getKey(clientId);
        GlobalCache.value().set(cacheKey, clientPublicKey, TimeUnit.SECONDS.toMillis(config.getClientKeyCacheExpire()));
        log.debug("缓存客户端公钥: {}", clientId);
    }

    /**
     * 获取客户端公钥
     *
     * @param clientId 客户端ID
     * @return 客户端公钥对象
     */
    @Override
    public PublicKey getClientPublicKey(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return null;
        }

        String cacheKey = GatewayRedisKey.ECC_CLIENT_PUBLIC_KEY.getKey(clientId);
        String base64PublicKey = GlobalCache.value().get(cacheKey, String.class);

        if (!StringUtils.hasText(base64PublicKey)) {
            log.warn("客户端公钥未找到: {}", clientId);
            return null;
        }

        try {
            return EccCryptoUtils.base64ToPublicKey(base64PublicKey);
        } catch (Exception e) {
            log.error("解析客户端公钥失败: {}", clientId, e);
            return null;
        }
    }

    /**
     * 生成或获取共享密钥
     *
     * @param clientId 客户端ID
     * @param gatewayPrivateKey 网关私钥
     * @return 共享密钥
     */
    @Override
    public SecretKey getOrGenerateSharedKey(String clientId, PrivateKey gatewayPrivateKey) {
        if (!StringUtils.hasText(clientId)) {
            return null;
        }

        String cacheKey = GatewayRedisKey.ECC_SHARED_KEY.getKey(clientId);

        // 尝试从缓存获取共享密钥
        SecretKey cachedKey = GlobalCache.struct().get(cacheKey, SecretKey.class);
        if (cachedKey != null) {
            return cachedKey;
        }

        // 生成新的共享密钥
        PublicKey clientPublicKey = getClientPublicKey(clientId);
        if (clientPublicKey == null) {
            log.warn("无法获取客户端公钥，无法生成共享密钥: {}", clientId);
            return null;
        }

        try {
            SecretKey sharedKey = EccCryptoUtils.generateSharedSecret(gatewayPrivateKey, clientPublicKey);

            // 缓存共享密钥
            GlobalCache.struct().set(cacheKey, sharedKey, config.getClientKeyCacheExpire());
            GlobalCache.key().setExpiredTime(cacheKey, TimeUnit.SECONDS.toMillis(config.getClientKeyCacheExpire()));

            log.debug("生成新的共享密钥: {}", clientId);
            return sharedKey;
        } catch (Exception e) {
            log.error("生成共享密钥失败: {}", clientId, e);
            return null;
        }
    }

    /**
     * 解密请求数据
     *
     * @param encryptedData 加密数据
     * @param clientId 客户端ID
     * @param gatewayPrivateKey 网关私钥
     * @return 解密后的数据
     */
    @Override
    public String decryptRequestData(String encryptedData, String clientId, PrivateKey gatewayPrivateKey) {
        if (!StringUtils.hasText(encryptedData) || !StringUtils.hasText(clientId)) {
            return encryptedData;
        }

        SecretKey sharedKey = getOrGenerateSharedKey(clientId, gatewayPrivateKey);
        if (sharedKey == null) {
            log.error("无法获取共享密钥，解密失败: {}", clientId);
            return null;
        }

        try {
            return EccCryptoUtils.decrypt(encryptedData, sharedKey);
        } catch (Exception e) {
            log.error("解密请求数据失败: {}", clientId, e);
            return null;
        }
    }

    /**
     * 加密响应数据
     *
     * @param responseData 响应数据
     * @param clientId 客户端ID
     * @param gatewayPrivateKey 网关私钥
     * @return 加密后的数据
     */
    @Override
    public String encryptResponseData(String responseData, String clientId, PrivateKey gatewayPrivateKey) {
        if (!StringUtils.hasText(responseData) || !StringUtils.hasText(clientId)) {
            return responseData;
        }

        SecretKey sharedKey = getOrGenerateSharedKey(clientId, gatewayPrivateKey);
        if (sharedKey == null) {
            log.error("无法获取共享密钥，加密失败: {}", clientId);
            return null;
        }

        try {
            return EccCryptoUtils.encrypt(responseData, sharedKey);
        } catch (Exception e) {
            log.error("加密响应数据失败: {}", clientId, e);
            return null;
        }
    }

    /**
     * 清理客户端相关缓存
     *
     * @param clientId 客户端ID
     */
    @Override
    public void clearClientCache(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            return;
        }

        String publicKeyCacheKey = GatewayRedisKey.ECC_CLIENT_PUBLIC_KEY.getKey(clientId);
        String sharedKeyCacheKey = GatewayRedisKey.ECC_SHARED_KEY.getKey(clientId);

        GlobalCache.key().removeCache(publicKeyCacheKey);
        GlobalCache.key().removeCache(sharedKeyCacheKey);

        log.debug("清理客户端缓存: {}", clientId);
    }
}
