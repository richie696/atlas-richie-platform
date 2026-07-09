/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.oauth.dcr;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.FieldOps;
import com.richie.component.cache.ops.KeyOps;
import com.richie.component.cache.ops.StructOps;
import com.richie.component.cache.ops.ValueOps;
import com.richie.component.oauth.core.ClientRegistry;
import com.richie.component.oauth.core.config.OAuth2Properties;
import com.richie.component.oauth.core.config.OAuth2RedisKey;
import com.richie.component.oauth.dcr.dto.ClientRegistrationRequest;
import com.richie.component.oauth.dcr.dto.ClientRegistrationResponse;
import com.richie.component.oauth.dcr.model.ClientIdMetadataDocument;
import com.richie.component.oauth.dcr.spi.ClientIdMetadataDocumentResolver;
import com.richie.component.oauth.dcr.support.SSRFProtection;
import com.richie.contract.exception.BusinessException;
import com.richie.contract.gateway.model.OAuth2Constants;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DynamicClientRegistrationEndpoint 测试")
class DynamicClientRegistrationEndpointTest {

    @Mock
    private ClientRegistry clientRegistry;
    @Mock
    private ClientIdMetadataDocumentResolver metadataResolver;
    @Mock
    private SSRFProtection ssrfProtection;
    @Mock
    private OAuth2Properties properties;
    @Mock
    private HttpServletRequest httpRequest;
    @Mock
    private StructOps structOps;
    @Mock
    private ValueOps valueOps;
    @Mock
    private KeyOps keyOps;

    private DynamicClientRegistrationEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new DynamicClientRegistrationEndpoint(
                clientRegistry, metadataResolver, ssrfProtection, properties);
    }

    @Test
    @DisplayName("registerClient 返回成功响应，所有字段正确")
    void registerClient_withValidRequest_returnsSuccessResponse() {
        ClientRegistrationRequest request = ClientRegistrationRequest.builder()
                .clientName("Test Client")
                .clientUri("https://example.com")
                .logoUri("https://example.com/logo.png")
                .redirectUris(List.of("https://example.com/callback"))
                .tokenEndpointAuthMethod("client_secret_basic")
                .grantTypes(List.of("client_credentials"))
                .scopes(List.of("read", "write"))
                .build();

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::struct).thenReturn(structOps);
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            globalCacheMock.when(GlobalCache::key).thenReturn(keyOps);

            when(keyOps.hasKey(anyString())).thenReturn(false);
            when(ssrfProtection.isUrlSafe("https://example.com/callback")).thenReturn(true);

            ClientRegistrationResponse response = endpoint.registerClient(request, httpRequest);

            assertThat(response).isNotNull();
            assertThat(response.getClientId()).isNotNull();
            assertThat(response.getClientId()).startsWith("dcr-");
            assertThat(response.getClientSecret()).isNotNull();
            assertThat(response.getClientSecretExpiresAt()).isGreaterThan(0);
            assertThat(response.getRegistrationAccessToken()).isNotNull();
            assertThat(response.getRegistrationClientUri()).contains(response.getClientId());
            assertThat(response.getClientName()).isEqualTo("Test Client");
            assertThat(response.getRedirectUris()).containsExactly("https://example.com/callback");
            assertThat(response.getTokenEndpointAuthMethod()).isEqualTo("client_secret_basic");
            assertThat(response.getGrantTypes()).containsExactly("client_credentials");
            assertThat(response.getScopes()).containsExactly("read", "write");

            verify(structOps, atLeastOnce()).set(anyString(), any(ClientIdMetadataDocument.class), anyLong());
            verify(structOps, atLeastOnce()).set(anyString(), any(), anyLong());
            verify(valueOps).set(anyString(), anyString(), anyLong());
        }
    }

    @Test
    @DisplayName("registerClient 抛出异常当请求为 null")
    void registerClient_withNullRequest_throwsException() {
        assertThatThrownBy(() -> endpoint.registerClient(null, httpRequest))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(OAuth2Constants.ERROR_INVALID_REQUEST);
                    assertThat(be.getMessage()).contains("注册请求不能为空");
                });

        verify(structOps, never()).set(anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("registerClient 抛出异常当 redirect_uris 为空")
    void registerClient_withEmptyRedirectUris_throwsException() {
        ClientRegistrationRequest request = ClientRegistrationRequest.builder()
                .clientName("Test Client")
                .redirectUris(List.of())
                .build();

        assertThatThrownBy(() -> endpoint.registerClient(request, httpRequest))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(OAuth2Constants.ERROR_INVALID_REQUEST);
                    assertThat(be.getMessage()).contains("redirect_uris 不能为空");
                });
    }

    @Test
    @DisplayName("registerClient 抛出异常当 redirect_uris 为 null")
    void registerClient_withNullRedirectUris_throwsException() {
        ClientRegistrationRequest request = ClientRegistrationRequest.builder()
                .clientName("Test Client")
                .redirectUris(null)
                .build();

        assertThatThrownBy(() -> endpoint.registerClient(request, httpRequest))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(OAuth2Constants.ERROR_INVALID_REQUEST);
                    assertThat(be.getMessage()).contains("redirect_uris 不能为空");
                });
    }

    @Test
    @DisplayName("registerClient 抛出异常当 redirect_uri 未通过 SSRF 检查")
    void registerClient_withUnsafeRedirectUri_throwsException() {
        ClientRegistrationRequest request = ClientRegistrationRequest.builder()
                .clientName("Test Client")
                .redirectUris(List.of("http://unsafe.example.com/callback"))
                .build();

        when(ssrfProtection.isUrlSafe("http://unsafe.example.com/callback")).thenReturn(false);

        assertThatThrownBy(() -> endpoint.registerClient(request, httpRequest))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(OAuth2Constants.ERROR_INVALID_REQUEST);
                    assertThat(be.getMessage()).contains("redirect_uri 不安全");
                });
    }

    @Test
    @DisplayName("registerClient 存储 metadata document 到 Redis")
    void registerClient_storesMetadataDocumentToRedis() {
        ClientRegistrationRequest request = ClientRegistrationRequest.builder()
                .clientName("Test Client")
                .redirectUris(List.of("https://example.com/callback"))
                .build();

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::struct).thenReturn(structOps);
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            globalCacheMock.when(GlobalCache::key).thenReturn(keyOps);

            when(keyOps.hasKey(anyString())).thenReturn(false);
            when(ssrfProtection.isUrlSafe("https://example.com/callback")).thenReturn(true);

            ClientRegistrationResponse response = endpoint.registerClient(request, httpRequest);

            verify(structOps).set(
                    eq(OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(response.getClientId())),
                    any(ClientIdMetadataDocument.class),
                    eq(365 * 24 * 3600 * 1000L)
            );
        }
    }

    @Test
    @DisplayName("registerClient 存储 client config 到 Redis")
    void registerClient_storesClientConfigToRedis() {
        ClientRegistrationRequest request = ClientRegistrationRequest.builder()
                .clientName("Test Client")
                .redirectUris(List.of("https://example.com/callback"))
                .build();

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::struct).thenReturn(structOps);
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            globalCacheMock.when(GlobalCache::key).thenReturn(keyOps);

            when(keyOps.hasKey(anyString())).thenReturn(false);
            when(ssrfProtection.isUrlSafe("https://example.com/callback")).thenReturn(true);

            ClientRegistrationResponse response = endpoint.registerClient(request, httpRequest);

            verify(structOps).set(
                    eq(OAuth2RedisKey.OAUTH2_CLIENT_CONFIG.getKey(response.getClientId())),
                    any(),
                    eq(365 * 24 * 3600 * 1000L)
            );
        }
    }

    @Test
    @DisplayName("registerClient 存储 registration access token")
    void registerClient_storesRegistrationAccessToken() {
        ClientRegistrationRequest request = ClientRegistrationRequest.builder()
                .clientName("Test Client")
                .redirectUris(List.of("https://example.com/callback"))
                .build();

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::struct).thenReturn(structOps);
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            globalCacheMock.when(GlobalCache::key).thenReturn(keyOps);

            when(keyOps.hasKey(anyString())).thenReturn(false);
            when(ssrfProtection.isUrlSafe("https://example.com/callback")).thenReturn(true);

            ClientRegistrationResponse response = endpoint.registerClient(request, httpRequest);

            verify(valueOps).set(
                    eq(OAuth2RedisKey.OAUTH2_REGISTRATION_TOKEN.getKey(response.getClientId())),
                    anyString(),
                    eq(365 * 24 * 3600 * 1000L)
            );
        }
    }

    @Test
    @DisplayName("registerClient 生成唯一 clientId")
    void registerClient_generatesUniqueClientId() {
        ClientRegistrationRequest request = ClientRegistrationRequest.builder()
                .clientName("Test Client")
                .redirectUris(List.of("https://example.com/callback"))
                .build();

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::struct).thenReturn(structOps);
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            globalCacheMock.when(GlobalCache::key).thenReturn(keyOps);

            when(keyOps.hasKey(anyString())).thenReturn(false);
            when(ssrfProtection.isUrlSafe("https://example.com/callback")).thenReturn(true);

            ClientRegistrationResponse response1 = endpoint.registerClient(request, httpRequest);
            ClientRegistrationResponse response2 = endpoint.registerClient(request, httpRequest);

            assertThat(response1.getClientId()).isNotNull();
            assertThat(response2.getClientId()).isNotNull();
            assertThat(response1.getClientId()).isNotEqualTo(response2.getClientId());
        }
    }

    @Test
    @DisplayName("registerClient 不返回 clientSecret 当 auth method 为 none")
    void registerClient_withNoneAuthMethod_doesNotReturnClientSecret() {
        ClientRegistrationRequest request = ClientRegistrationRequest.builder()
                .clientName("Test Client")
                .redirectUris(List.of("https://example.com/callback"))
                .tokenEndpointAuthMethod("none")
                .build();

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::struct).thenReturn(structOps);
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            globalCacheMock.when(GlobalCache::key).thenReturn(keyOps);

            when(keyOps.hasKey(anyString())).thenReturn(false);
            when(ssrfProtection.isUrlSafe("https://example.com/callback")).thenReturn(true);

            ClientRegistrationResponse response = endpoint.registerClient(request, httpRequest);

            assertThat(response.getClientSecret()).isNull();
            assertThat(response.getClientSecretExpiresAt()).isEqualTo(0L);
        }
    }

    @Test
    @DisplayName("updateClient 返回更新后的响应")
    void updateClient_withValidRequest_returnsUpdatedResponse() {
        String clientId = "existing-client";
        ClientIdMetadataDocument existingDoc = ClientIdMetadataDocument.builder()
                .clientId(clientId)
                .clientSecret("existing-secret")
                .clientName("Old Name")
                .redirectUris(List.of("https://old.example.com/callback"))
                .tokenEndpointAuthMethod("client_secret_basic")
                .grantTypes(List.of("client_credentials"))
                .scopes(List.of("read"))
                .build();

        ClientRegistrationRequest updateRequest = ClientRegistrationRequest.builder()
                .clientName("New Name")
                .redirectUris(List.of("https://new.example.com/callback"))
                .build();

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::struct).thenReturn(structOps);
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            globalCacheMock.when(GlobalCache::key).thenReturn(keyOps);

            when(metadataResolver.resolve(clientId, null)).thenReturn(existingDoc);
            when(ssrfProtection.isUrlSafe("https://new.example.com/callback")).thenReturn(true);

            ClientRegistrationResponse response = endpoint.updateClient(clientId, updateRequest, httpRequest);

            assertThat(response).isNotNull();
            assertThat(response.getClientId()).isEqualTo(clientId);
            assertThat(response.getClientName()).isEqualTo("New Name");
            assertThat(response.getRedirectUris()).containsExactly("https://new.example.com/callback");
            assertThat(response.getClientSecret()).isEqualTo("existing-secret");

            verify(structOps).set(anyString(), any(ClientIdMetadataDocument.class), anyLong());
        }
    }

    @Test
    @DisplayName("updateClient 抛出异常当客户端不存在")
    void updateClient_withNonExistentClient_throwsException() {
        String clientId = "nonexistent-client";
        ClientRegistrationRequest updateRequest = ClientRegistrationRequest.builder()
                .clientName("New Name")
                .build();

        when(metadataResolver.resolve(clientId, null)).thenReturn(null);

        assertThatThrownBy(() -> endpoint.updateClient(clientId, updateRequest, httpRequest))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(OAuth2Constants.ERROR_INVALID_CLIENT);
                    assertThat(be.getMessage()).contains("客户端不存在");
                });
    }

    @Test
    @DisplayName("updateClient 抛出异常当 jwks_uri 未通过 SSRF 检查")
    void updateClient_withUnsafeJwksUri_throwsException() {
        String clientId = "existing-client";
        ClientIdMetadataDocument existingDoc = ClientIdMetadataDocument.builder()
                .clientId(clientId)
                .clientSecret("existing-secret")
                .build();

        ClientRegistrationRequest updateRequest = ClientRegistrationRequest.builder()
                .jwksUri("http://unsafe.example.com/jwks.json")
                .build();

        when(metadataResolver.resolve(clientId, null)).thenReturn(existingDoc);
        when(ssrfProtection.isUrlSafe("http://unsafe.example.com/jwks.json")).thenReturn(false);

        assertThatThrownBy(() -> endpoint.updateClient(clientId, updateRequest, httpRequest))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(OAuth2Constants.ERROR_INVALID_REQUEST);
                    assertThat(be.getMessage()).contains("jwks_uri 不安全");
                });
    }

    @Test
    @DisplayName("updateClient 合并字段时保留未提供的值")
    void updateClient_mergesFields_preservesExistingValues() {
        String clientId = "existing-client";
        ClientIdMetadataDocument existingDoc = ClientIdMetadataDocument.builder()
                .clientId(clientId)
                .clientSecret("existing-secret")
                .clientName("Old Name")
                .redirectUris(List.of("https://old.example.com/callback"))
                .tokenEndpointAuthMethod("client_secret_basic")
                .grantTypes(List.of("client_credentials"))
                .scopes(List.of("read", "write"))
                .clientUri("https://old.example.com")
                .logoUri("https://old.example.com/logo.png")
                .build();

        ClientRegistrationRequest updateRequest = ClientRegistrationRequest.builder()
                .clientName("New Name")
                .build();

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::struct).thenReturn(structOps);
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            globalCacheMock.when(GlobalCache::key).thenReturn(keyOps);

            when(metadataResolver.resolve(clientId, null)).thenReturn(existingDoc);

            ClientRegistrationResponse response = endpoint.updateClient(clientId, updateRequest, httpRequest);

            assertThat(response.getClientName()).isEqualTo("New Name");
            assertThat(response.getRedirectUris()).containsExactly("https://old.example.com/callback");
            assertThat(response.getTokenEndpointAuthMethod()).isEqualTo("client_secret_basic");
            assertThat(response.getScopes()).containsExactly("read", "write");
            assertThat(response.getClientUri()).isEqualTo("https://old.example.com");
            assertThat(response.getLogoUri()).isEqualTo("https://old.example.com/logo.png");
        }
    }

    @Test
    @DisplayName("updateClient 抛出异常当 clientId 为空")
    void updateClient_withBlankClientId_throwsException() {
        ClientRegistrationRequest updateRequest = ClientRegistrationRequest.builder()
                .clientName("New Name")
                .build();

        assertThatThrownBy(() -> endpoint.updateClient("  ", updateRequest, httpRequest))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(OAuth2Constants.ERROR_INVALID_REQUEST);
                    assertThat(be.getMessage()).contains("client_id 不能为空");
                });
    }

    @Test
    @DisplayName("registerClient 验证多个 redirect URIs")
    void registerClient_validatesMultipleRedirectUris() {
        ClientRegistrationRequest request = ClientRegistrationRequest.builder()
                .clientName("Test Client")
                .redirectUris(List.of(
                        "https://example.com/callback",
                        "https://app.example.com/callback",
                        "https://web.example.com/callback"
                ))
                .build();

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::struct).thenReturn(structOps);
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            globalCacheMock.when(GlobalCache::key).thenReturn(keyOps);

            when(keyOps.hasKey(anyString())).thenReturn(false);
            when(ssrfProtection.isUrlSafe("https://example.com/callback")).thenReturn(true);
            when(ssrfProtection.isUrlSafe("https://app.example.com/callback")).thenReturn(true);
            when(ssrfProtection.isUrlSafe("https://web.example.com/callback")).thenReturn(true);

            ClientRegistrationResponse response = endpoint.registerClient(request, httpRequest);

            assertThat(response.getRedirectUris()).hasSize(3);
            verify(ssrfProtection).isUrlSafe("https://example.com/callback");
            verify(ssrfProtection).isUrlSafe("https://app.example.com/callback");
            verify(ssrfProtection).isUrlSafe("https://web.example.com/callback");
        }
    }

    @Test
    @DisplayName("updateClient 不强制要求 redirect_uris 更新")
    void updateClient_doesNotRequireRedirectUris() {
        String clientId = "existing-client";
        ClientIdMetadataDocument existingDoc = ClientIdMetadataDocument.builder()
                .clientId(clientId)
                .clientSecret("existing-secret")
                .clientName("Old Name")
                .redirectUris(List.of("https://old.example.com/callback"))
                .build();

        ClientRegistrationRequest updateRequest = ClientRegistrationRequest.builder()
                .clientName("New Name")
                .build();

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            globalCacheMock.when(GlobalCache::struct).thenReturn(structOps);
            globalCacheMock.when(GlobalCache::value).thenReturn(valueOps);
            globalCacheMock.when(GlobalCache::key).thenReturn(keyOps);

            when(metadataResolver.resolve(clientId, null)).thenReturn(existingDoc);

            ClientRegistrationResponse response = endpoint.updateClient(clientId, updateRequest, httpRequest);

            assertThat(response).isNotNull();
            assertThat(response.getRedirectUris()).containsExactly("https://old.example.com/callback");
        }
    }
}
