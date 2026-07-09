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
package com.richie.component.oauth.dcr.spi;

import com.richie.component.oauth.dcr.model.ClientIdMetadataDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ClientIdMetadataDocumentResolver SPI 测试")
class ClientIdMetadataDocumentResolverTest {

    @Mock
    private ClientIdMetadataDocumentResolver resolver;

    private ClientIdMetadataDocument sampleDocument;

    @BeforeEach
    void setUp() {
        sampleDocument = ClientIdMetadataDocument.builder()
                .clientId("client-123")
                .clientSecret("secret-abc")
                .clientName("Test Client")
                .redirectUris(List.of("https://example.com/callback"))
                .tokenEndpointAuthMethod("client_secret_basic")
                .grantTypes(List.of("client_credentials"))
                .scopes(List.of("read", "write"))
                .clientUri("https://example.com")
                .logoUri("https://example.com/logo.png")
                .build();
    }

    @Test
    @DisplayName("resolve 返回 metadata document")
    void resolve_whenDocumentExists_returnsDocument() {
        String clientId = "client-123";
        String metadataUri = "https://example.com/metadata.json";
        when(resolver.resolve(clientId, metadataUri)).thenReturn(sampleDocument);

        ClientIdMetadataDocument result = resolver.resolve(clientId, metadataUri);

        assertThat(result).isNotNull();
        assertThat(result.getClientId()).isEqualTo("client-123");
        assertThat(result.getClientSecret()).isEqualTo("secret-abc");
        assertThat(result.getClientName()).isEqualTo("Test Client");
        verify(resolver).resolve(clientId, metadataUri);
    }

    @Test
    @DisplayName("resolve 返回 null 当文档不存在")
    void resolve_whenDocumentNotExists_returnsNull() {
        String clientId = "nonexistent-client";
        String metadataUri = null;
        when(resolver.resolve(clientId, metadataUri)).thenReturn(null);

        ClientIdMetadataDocument result = resolver.resolve(clientId, metadataUri);

        assertThat(result).isNull();
        verify(resolver).resolve(clientId, metadataUri);
    }

    @Test
    @DisplayName("resolve 传递 metadataUri 参数")
    void resolve_withMetadataUri_passesMetadataUri() {
        String clientId = "client-123";
        String metadataUri = "https://external.example.com/metadata.json";
        when(resolver.resolve(clientId, metadataUri)).thenReturn(sampleDocument);

        resolver.resolve(clientId, metadataUri);

        verify(resolver).resolve(clientId, metadataUri);
    }

    @Test
    @DisplayName("getMetadataUri 返回配置的 URI")
    void getMetadataUri_whenConfigured_returnsUri() {
        String clientId = "client-123";
        String expectedUri = "https://example.com/.well-known/client-metadata.json";
        when(resolver.getMetadataUri(clientId)).thenReturn(expectedUri);

        String result = resolver.getMetadataUri(clientId);

        assertThat(result).isEqualTo(expectedUri);
        verify(resolver).getMetadataUri(clientId);
    }

    @Test
    @DisplayName("getMetadataUri 返回 null 当未配置")
    void getMetadataUri_whenNotConfigured_returnsNull() {
        String clientId = "client-123";
        when(resolver.getMetadataUri(clientId)).thenReturn(null);

        String result = resolver.getMetadataUri(clientId);

        assertThat(result).isNull();
        verify(resolver).getMetadataUri(clientId);
    }

    @Test
    @DisplayName("getMetadataUri 对 null clientId 返回 null")
    void getMetadataUri_withNullClientId_returnsNull() {
        when(resolver.getMetadataUri(null)).thenReturn(null);

        String result = resolver.getMetadataUri(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolve 对 null clientId 返回 null")
    void resolve_withNullClientId_returnsNull() {
        when(resolver.resolve(null, null)).thenReturn(null);

        ClientIdMetadataDocument result = resolver.resolve(null, null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolve 返回完整文档内容")
    void resolve_returnsCompleteDocument() {
        String clientId = "client-123";
        String metadataUri = null;
        ClientIdMetadataDocument fullDocument = ClientIdMetadataDocument.builder()
                .clientId("client-123")
                .clientSecret("secret-xyz")
                .clientName("Full Client")
                .redirectUris(List.of("https://example.com/callback", "https://example.com/callback2"))
                .tokenEndpointAuthMethod("none")
                .grantTypes(List.of("authorization_code", "refresh_token"))
                .scopes(List.of("read", "write", "admin"))
                .contacts(List.of("admin@example.com", "support@example.com"))
                .clientUri("https://example.com")
                .logoUri("https://example.com/logo.png")
                .owner("owner@example.com")
                .tosUri("https://example.com/tos")
                .policyUri("https://example.com/policy")
                .jwksUri("https://example.com/.well-known/jwks.json")
                .resource(List.of("https://api.example.com"))
                .build();
        when(resolver.resolve(clientId, metadataUri)).thenReturn(fullDocument);

        ClientIdMetadataDocument result = resolver.resolve(clientId, metadataUri);

        assertThat(result.getClientId()).isEqualTo("client-123");
        assertThat(result.getClientSecret()).isEqualTo("secret-xyz");
        assertThat(result.getClientName()).isEqualTo("Full Client");
        assertThat(result.getRedirectUris()).hasSize(2);
        assertThat(result.getTokenEndpointAuthMethod()).isEqualTo("none");
        assertThat(result.getGrantTypes()).containsExactly("authorization_code", "refresh_token");
        assertThat(result.getScopes()).containsExactly("read", "write", "admin");
        assertThat(result.getContacts()).containsExactly("admin@example.com", "support@example.com");
        assertThat(result.getJwksUri()).isEqualTo("https://example.com/.well-known/jwks.json");
        assertThat(result.getResource()).containsExactly("https://api.example.com");
    }
}
