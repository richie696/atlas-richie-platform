package com.richie.component.oauth.dcr.support;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.FieldOps;
import com.richie.component.cache.ops.StructOps;
import com.richie.component.oauth.core.config.OAuth2RedisKey;
import com.richie.component.oauth.dcr.model.ClientIdMetadataDocument;
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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultClientIdMetadataDocumentResolver 测试")
class DefaultClientIdMetadataDocumentResolverTest {

    @Mock
    private GlobalCache globalCache;
    @Mock
    private SSRFProtection ssrfProtection;
    @Mock
    private StructOps structOps;
    @Mock
    private FieldOps fieldOps;

    @Test
    @DisplayName("resolve 返回 metadata 从 Redis")
    void resolve_whenDocumentExistsInRedis_returnsDocument() {
        String clientId = "client-123";
        String metadataUri = "https://example.com/metadata.json";
        ClientIdMetadataDocument expectedDoc = ClientIdMetadataDocument.builder()
                .clientId(clientId)
                .clientSecret("secret-abc")
                .clientName("Test Client")
                .redirectUris(List.of("https://example.com/callback"))
                .tokenEndpointAuthMethod("client_secret_basic")
                .build();

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            DefaultClientIdMetadataDocumentResolver resolver = new DefaultClientIdMetadataDocumentResolver(
                    globalCache, ssrfProtection);

            globalCacheMock.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId), ClientIdMetadataDocument.class))
                    .thenReturn(expectedDoc);
            when(ssrfProtection.isUrlSafe(metadataUri)).thenReturn(true);

            ClientIdMetadataDocument result = resolver.resolve(clientId, metadataUri);

            assertThat(result).isNotNull();
            assertThat(result.getClientId()).isEqualTo(clientId);
            assertThat(result.getClientSecret()).isEqualTo("secret-abc");
            assertThat(result.getClientName()).isEqualTo("Test Client");
            verify(structOps).get(OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId), ClientIdMetadataDocument.class);
        }
    }

    @Test
    @DisplayName("resolve 返回 null 当 clientId 为 null")
    void resolve_withNullClientId_returnsNull() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            DefaultClientIdMetadataDocumentResolver resolver = new DefaultClientIdMetadataDocumentResolver(
                    globalCache, ssrfProtection);

            ClientIdMetadataDocument result = resolver.resolve(null, null);

            assertThat(result).isNull();
        }
    }

    @Test
    @DisplayName("resolve 返回 null 当文档不存在")
    void resolve_whenDocumentNotExists_returnsNull() {
        String clientId = "nonexistent-client";
        String metadataUri = null;

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            DefaultClientIdMetadataDocumentResolver resolver = new DefaultClientIdMetadataDocumentResolver(
                    globalCache, ssrfProtection);

            globalCacheMock.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId), ClientIdMetadataDocument.class))
                    .thenReturn(null);

            ClientIdMetadataDocument result = resolver.resolve(clientId, metadataUri);

            assertThat(result).isNull();
        }
    }

    @Test
    @DisplayName("resolve 当 metadataUri 不安全时记录警告")
    void resolve_whenMetadataUriNotSafe_logsWarning() {
        String clientId = "client-123";
        String metadataUri = "http://dangerous.example.com/metadata.json";
        ClientIdMetadataDocument existingDoc = ClientIdMetadataDocument.builder()
                .clientId(clientId)
                .build();

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            DefaultClientIdMetadataDocumentResolver resolver = new DefaultClientIdMetadataDocumentResolver(
                    globalCache, ssrfProtection);

            globalCacheMock.when(GlobalCache::struct).thenReturn(structOps);
            when(structOps.get(OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId), ClientIdMetadataDocument.class))
                    .thenReturn(existingDoc);
            when(ssrfProtection.isUrlSafe(metadataUri)).thenReturn(false);

            ClientIdMetadataDocument result = resolver.resolve(clientId, metadataUri);

            assertThat(result).isNotNull();
            verify(ssrfProtection).isUrlSafe(metadataUri);
        }
    }

    @Test
    @DisplayName("getMetadataUri 返回配置的 URI")
    void getMetadataUri_whenConfigured_returnsUri() {
        String clientId = "client-123";
        String expectedUri = "https://example.com/.well-known/client-metadata.json";

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            DefaultClientIdMetadataDocumentResolver resolver = new DefaultClientIdMetadataDocumentResolver(
                    globalCache, ssrfProtection);

            globalCacheMock.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId), "metadataUri", String.class))
                    .thenReturn(expectedUri);

            String result = resolver.getMetadataUri(clientId);

            assertThat(result).isEqualTo(expectedUri);
            verify(fieldOps).get(OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId), "metadataUri", String.class);
        }
    }

    @Test
    @DisplayName("getMetadataUri 返回 null 当 clientId 为 null")
    void getMetadataUri_withNullClientId_returnsNull() {
        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            DefaultClientIdMetadataDocumentResolver resolver = new DefaultClientIdMetadataDocumentResolver(
                    globalCache, ssrfProtection);

            String result = resolver.getMetadataUri(null);

            assertThat(result).isNull();
        }
    }

    @Test
    @DisplayName("getMetadataUri 返回 null 当 metadataUri 未配置")
    void getMetadataUri_whenNotConfigured_returnsNull() {
        String clientId = "client-123";

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            DefaultClientIdMetadataDocumentResolver resolver = new DefaultClientIdMetadataDocumentResolver(
                    globalCache, ssrfProtection);

            globalCacheMock.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId), "metadataUri", String.class))
                    .thenReturn(null);

            String result = resolver.getMetadataUri(clientId);

            assertThat(result).isNull();
        }
    }

    @Test
    @DisplayName("getMetadataUri 使用正确的 Redis key")
    void getMetadataUri_usesCorrectRedisKey() {
        String clientId = "test-client-456";

        try (MockedStatic<GlobalCache> globalCacheMock = mockStatic(GlobalCache.class)) {
            DefaultClientIdMetadataDocumentResolver resolver = new DefaultClientIdMetadataDocumentResolver(
                    globalCache, ssrfProtection);

            globalCacheMock.when(GlobalCache::field).thenReturn(fieldOps);
            when(fieldOps.get(OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId), "metadataUri", String.class))
                    .thenReturn(null);

            resolver.getMetadataUri(clientId);

            String expectedKey = OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId);
            verify(fieldOps).get(expectedKey, "metadataUri", String.class);
        }
    }
}
