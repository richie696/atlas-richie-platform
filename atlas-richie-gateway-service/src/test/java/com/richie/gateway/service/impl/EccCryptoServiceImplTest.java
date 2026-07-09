/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.service.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.GlobalCacheManager;
import com.richie.gateway.config.EccCryptoConfig;
import com.richie.gateway.utils.EccCryptoUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EccCryptoServiceImplTest {

    private static final String CLIENT_ID = "test-client-001";
    private static final String ENCRYPTED_DATA = "encrypted-data-base64";
    private static final String DECRYPTED_DATA = "decrypted-data";
    private static final String PLAIN_DATA = "plain-text";

    private String clientPublicKeyBase64;

    @Mock
    private EccCryptoConfig config;

    @Mock
    private GlobalCacheManager cacheManager;

    @Mock
    private com.richie.component.cache.ops.ValueOps valueOps;

    @Mock
    private com.richie.component.cache.ops.StructOps structOps;

    @Mock
    private com.richie.component.cache.ops.KeyOps keyOps;

    private EccCryptoServiceImpl service;

    private MockedStatic<GlobalCache> globalCacheMockedStatic;
    private MockedStatic<EccCryptoUtils> eccCryptoUtilsMockedStatic;

    @BeforeEach
    void setUp() throws Exception {
        injectCacheManager();

        globalCacheMockedStatic = mockStatic(GlobalCache.class);
        globalCacheMockedStatic.when(GlobalCache::value).thenReturn(valueOps);
        globalCacheMockedStatic.when(GlobalCache::struct).thenReturn(structOps);
        globalCacheMockedStatic.when(GlobalCache::key).thenReturn(keyOps);

        // 运行时生成真实 secp256r1 密钥对并导出 Base64 编码的公钥,避免硬编码截断的 base64 字符串
        KeyPair clientKeyPair = EccCryptoUtils.generateKeyPair();
        clientPublicKeyBase64 = EccCryptoUtils.publicKeyToBase64(clientKeyPair.getPublic());

        eccCryptoUtilsMockedStatic = mockStatic(EccCryptoUtils.class);

        service = new EccCryptoServiceImpl(config);
    }

    private void injectCacheManager() throws Exception {
        var field = GlobalCache.class.getDeclaredField("DELEGATE");
        field.setAccessible(true);
        AtomicReference<GlobalCacheManager> ref = (AtomicReference<GlobalCacheManager>) field.get(null);
        ref.set(cacheManager);
    }

    @AfterEach
    void tearDown() {
        if (globalCacheMockedStatic != null) globalCacheMockedStatic.close();
        if (eccCryptoUtilsMockedStatic != null) eccCryptoUtilsMockedStatic.close();
    }

    @Nested
    @DisplayName("shouldEncrypt 是否应该加密")
    class ShouldEncryptTests {

        @Test
        @DisplayName("配置禁用时返回 false")
        void shouldEncrypt_disabled_returnsFalse() {
            when(config.isEnabled()).thenReturn(false);

            boolean result = service.shouldEncrypt("/api/orders");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("路径匹配排除规则时返回 false")
        void shouldEncrypt_excludedPath_returnsFalse() {
            when(config.isEnabled()).thenReturn(true);
            doReturn(new String[]{"/actuator/**"}).when(config).getExcludePaths();

            boolean result = service.shouldEncrypt("/actuator/health");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("路径匹配加密规则时返回 true")
        void shouldEncrypt_encryptPath_returnsTrue() {
            when(config.isEnabled()).thenReturn(true);
            doReturn(new String[]{}).when(config).getExcludePaths();
            doReturn(new String[]{"/api/orders/**"}).when(config).getEncryptPaths();

            boolean result = service.shouldEncrypt("/api/orders/123");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("路径不匹配任何规则时返回 false")
        void shouldEncrypt_noMatch_returnsFalse() {
            when(config.isEnabled()).thenReturn(true);
            doReturn(new String[]{}).when(config).getExcludePaths();
            doReturn(new String[]{"/api/orders/**"}).when(config).getEncryptPaths();

            boolean result = service.shouldEncrypt("/api/products/123");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("cacheClientPublicKey 缓存客户端公钥")
    class CacheClientPublicKeyTests {

        @Test
        @DisplayName("正常缓存公钥")
        void cacheClientPublicKey_happy_setsCache() {
            when(config.getClientKeyCacheExpire()).thenReturn(3600L);

            service.cacheClientPublicKey(CLIENT_ID, clientPublicKeyBase64);

            verify(valueOps).set(contains(CLIENT_ID), eq(clientPublicKeyBase64), eq(TimeUnit.SECONDS.toMillis(3600)));
        }

        @Test
        @DisplayName("clientId 为空时不操作")
        void cacheClientPublicKey_blankClientId_noOp() {
            service.cacheClientPublicKey("", clientPublicKeyBase64);

            verifyNoInteractions(valueOps);
        }

        @Test
        @DisplayName("publicKey 为空时不操作")
        void cacheClientPublicKey_blankPublicKey_noOp() {
            service.cacheClientPublicKey(CLIENT_ID, "   ");

            verifyNoInteractions(valueOps);
        }
    }

    @Nested
    @DisplayName("getClientPublicKey 获取客户端公钥")
    class GetClientPublicKeyTests {

        @Test
        @DisplayName("clientId 为空时返回 null")
        void getClientPublicKey_blankClientId_returnsNull() {
            PublicKey result = service.getClientPublicKey("");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("缓存未命中时返回 null")
        void getClientPublicKey_cacheMiss_returnsNull() {
            when(valueOps.get(anyString(), eq(String.class))).thenReturn(null);

            PublicKey result = service.getClientPublicKey(CLIENT_ID);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("正常解析公钥成功")
        void getClientPublicKey_validKey_returnsPublicKey() throws Exception {
            PublicKey expectedKey = java.security.KeyFactory.getInstance("EC")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(clientPublicKeyBase64)));
            when(valueOps.get(anyString(), eq(String.class))).thenReturn(clientPublicKeyBase64);
            eccCryptoUtilsMockedStatic.when(() -> EccCryptoUtils.base64ToPublicKey(clientPublicKeyBase64))
                    .thenReturn(expectedKey);

            PublicKey result = service.getClientPublicKey(CLIENT_ID);

            assertThat(result).isEqualTo(expectedKey);
        }

        @Test
        @DisplayName("无效 base64 时捕获异常返回 null")
        void getClientPublicKey_invalidBase64_returnsNull() {
            when(valueOps.get(anyString(), eq(String.class))).thenReturn("not-valid-base64!!!");
            eccCryptoUtilsMockedStatic.when(() -> EccCryptoUtils.base64ToPublicKey("not-valid-base64!!!"))
                    .thenThrow(new Exception("Invalid key"));

            PublicKey result = service.getClientPublicKey(CLIENT_ID);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getOrGenerateSharedKey 获取或生成共享密钥")
    class GetOrGenerateSharedKeyTests {

        @Mock
        private PrivateKey privateKey;

        @Mock
        private PublicKey publicKey;

        @Test
        @DisplayName("clientId 为空时返回 null")
        void getOrGenerateSharedKey_blankClientId_returnsNull() {
            SecretKey result = service.getOrGenerateSharedKey("", privateKey);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("缓存命中时返回缓存的密钥")
        void getOrGenerateSharedKey_cacheHit_returnsCached() {
            SecretKey cachedKey = mock(SecretKey.class);
            when(structOps.get(anyString(), eq(SecretKey.class))).thenReturn(cachedKey);

            SecretKey result = service.getOrGenerateSharedKey(CLIENT_ID, privateKey);

            assertThat(result).isEqualTo(cachedKey);
        }

        @Test
        @DisplayName("缓存未命中且公钥不存在时返回 null")
        void getOrGenerateSharedKey_cacheMissNoPublicKey_returnsNull() {
            when(structOps.get(anyString(), eq(SecretKey.class))).thenReturn(null);
            when(valueOps.get(anyString(), eq(String.class))).thenReturn(null);

            SecretKey result = service.getOrGenerateSharedKey(CLIENT_ID, privateKey);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("缓存未命中时生成新密钥并缓存")
        void getOrGenerateSharedKey_cacheMissGeneratesNew() throws Exception {
            SecretKey newKey = mock(SecretKey.class);
            PublicKey pubKey = java.security.KeyFactory.getInstance("EC")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(clientPublicKeyBase64)));
            when(structOps.get(anyString(), eq(SecretKey.class))).thenReturn(null);
            when(valueOps.get(anyString(), eq(String.class))).thenReturn(clientPublicKeyBase64);
            when(config.getClientKeyCacheExpire()).thenReturn(3600L);
            eccCryptoUtilsMockedStatic.when(() -> EccCryptoUtils.base64ToPublicKey(clientPublicKeyBase64))
                    .thenReturn(pubKey);
            eccCryptoUtilsMockedStatic.when(() -> EccCryptoUtils.generateSharedSecret(any(), any()))
                    .thenReturn(newKey);

            SecretKey result = service.getOrGenerateSharedKey(CLIENT_ID, privateKey);

            assertThat(result).isEqualTo(newKey);
            verify(structOps).set(anyString(), eq(newKey), eq(3600L));
        }

        @Test
        @DisplayName("生成共享密钥失败时返回 null")
        void getOrGenerateSharedKey_generateFails_returnsNull() throws Exception {
            PublicKey pubKey = java.security.KeyFactory.getInstance("EC")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(clientPublicKeyBase64)));
            when(structOps.get(anyString(), eq(SecretKey.class))).thenReturn(null);
            when(valueOps.get(anyString(), eq(String.class))).thenReturn(clientPublicKeyBase64);
            eccCryptoUtilsMockedStatic.when(() -> EccCryptoUtils.base64ToPublicKey(clientPublicKeyBase64))
                    .thenReturn(pubKey);
            eccCryptoUtilsMockedStatic.when(() -> EccCryptoUtils.generateSharedSecret(any(), any()))
                    .thenThrow(new Exception("Key agreement failed"));

            SecretKey result = service.getOrGenerateSharedKey(CLIENT_ID, privateKey);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("decryptRequestData 解密请求数据")
    class DecryptRequestDataTests {

        @Mock
        private PrivateKey privateKey;

        @Test
        @DisplayName("encryptedData 为空时返回原值")
        void decryptRequestData_blankData_returnsOriginal() {
            String result = service.decryptRequestData("", CLIENT_ID, privateKey);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("clientId 为空时返回原值")
        void decryptRequestData_blankClientId_returnsOriginal() {
            String result = service.decryptRequestData(ENCRYPTED_DATA, "", privateKey);
            assertThat(result).isEqualTo(ENCRYPTED_DATA);
        }

        @Test
        @DisplayName("共享密钥获取失败时返回 null")
        void decryptRequestData_noSharedKey_returnsNull() {
            when(structOps.get(anyString(), eq(SecretKey.class))).thenReturn(null);
            when(valueOps.get(anyString(), eq(String.class))).thenReturn(null);

            String result = service.decryptRequestData(ENCRYPTED_DATA, CLIENT_ID, privateKey);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("正常解密成功")
        void decryptRequestData_happy_decrypts() throws Exception {
            SecretKey sharedKey = mock(SecretKey.class);
            when(structOps.get(anyString(), eq(SecretKey.class))).thenReturn(null);
            when(valueOps.get(anyString(), eq(String.class))).thenReturn(clientPublicKeyBase64);
            when(config.getClientKeyCacheExpire()).thenReturn(3600L);
            PublicKey pubKey = java.security.KeyFactory.getInstance("EC")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(clientPublicKeyBase64)));
            eccCryptoUtilsMockedStatic.when(() -> EccCryptoUtils.base64ToPublicKey(clientPublicKeyBase64))
                    .thenReturn(pubKey);
            eccCryptoUtilsMockedStatic.when(() -> EccCryptoUtils.generateSharedSecret(any(), any()))
                    .thenReturn(sharedKey);
            eccCryptoUtilsMockedStatic.when(() -> EccCryptoUtils.decrypt(eq(ENCRYPTED_DATA), eq(sharedKey)))
                    .thenReturn(DECRYPTED_DATA);

            String result = service.decryptRequestData(ENCRYPTED_DATA, CLIENT_ID, privateKey);

            assertThat(result).isEqualTo(DECRYPTED_DATA);
        }

        @Test
        @DisplayName("解密异常时返回 null")
        void decryptRequestData_decryptFails_returnsNull() throws Exception {
            SecretKey sharedKey = mock(SecretKey.class);
            when(structOps.get(anyString(), eq(SecretKey.class))).thenReturn(sharedKey);
            eccCryptoUtilsMockedStatic.when(() -> EccCryptoUtils.decrypt(anyString(), eq(sharedKey)))
                    .thenThrow(new Exception("Decrypt failed"));

            String result = service.decryptRequestData(ENCRYPTED_DATA, CLIENT_ID, privateKey);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("encryptResponseData 加密响应数据")
    class EncryptResponseDataTests {

        @Mock
        private PrivateKey privateKey;

        @Test
        @DisplayName("responseData 为空时返回原值")
        void encryptResponseData_blankData_returnsOriginal() {
            String result = service.encryptResponseData("", CLIENT_ID, privateKey);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("clientId 为空时返回原值")
        void encryptResponseData_blankClientId_returnsOriginal() {
            String result = service.encryptResponseData(PLAIN_DATA, "", privateKey);
            assertThat(result).isEqualTo(PLAIN_DATA);
        }

        @Test
        @DisplayName("共享密钥获取失败时返回 null")
        void encryptResponseData_noSharedKey_returnsNull() {
            when(structOps.get(anyString(), eq(SecretKey.class))).thenReturn(null);
            when(valueOps.get(anyString(), eq(String.class))).thenReturn(null);

            String result = service.encryptResponseData(PLAIN_DATA, CLIENT_ID, privateKey);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("正常加密成功")
        void encryptResponseData_happy_encrypts() throws Exception {
            SecretKey sharedKey = mock(SecretKey.class);
            when(structOps.get(anyString(), eq(SecretKey.class))).thenReturn(sharedKey);
            eccCryptoUtilsMockedStatic.when(() -> EccCryptoUtils.encrypt(eq(PLAIN_DATA), eq(sharedKey)))
                    .thenReturn(ENCRYPTED_DATA);

            String result = service.encryptResponseData(PLAIN_DATA, CLIENT_ID, privateKey);

            assertThat(result).isEqualTo(ENCRYPTED_DATA);
        }

        @Test
        @DisplayName("加密异常时返回 null")
        void encryptResponseData_encryptFails_returnsNull() throws Exception {
            SecretKey sharedKey = mock(SecretKey.class);
            when(structOps.get(anyString(), eq(SecretKey.class))).thenReturn(sharedKey);
            eccCryptoUtilsMockedStatic.when(() -> EccCryptoUtils.encrypt(anyString(), eq(sharedKey)))
                    .thenThrow(new Exception("Encrypt failed"));

            String result = service.encryptResponseData(PLAIN_DATA, CLIENT_ID, privateKey);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("clearClientCache 清理客户端缓存")
    class ClearClientCacheTests {

        @Test
        @DisplayName("clientId 为空时不操作")
        void clearClientCache_blankClientId_noOp() {
            service.clearClientCache("");

            verifyNoInteractions(keyOps);
        }

        @Test
        @DisplayName("正常清理两个缓存键")
        void clearClientCache_happy_removesBothKeys() {
            service.clearClientCache(CLIENT_ID);

            verify(keyOps, times(2)).removeCache(contains(CLIENT_ID));
        }
    }
}
