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
package com.richie.component.oauth.dcr.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClientRegistrationResponse DTO 测试")
class ClientRegistrationResponseTest {

    @Test
    @DisplayName("默认构造函数创建实例")
    void constructor_withNoArgs_createsInstance() {
        ClientRegistrationResponse response = new ClientRegistrationResponse();

        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("@Builder 创建实例，所有字段可访问")
    void builder_createsInstance_withAllFields() {
        List<String> redirectUris = List.of("https://example.com/callback");
        List<String> grantTypes = List.of("client_credentials");
        List<String> scopes = List.of("read", "write");
        List<String> resource = List.of("https://api.example.com");
        Long clientSecretExpiresAt = System.currentTimeMillis() + 86400000L;
        Long registrationAccessToken = 123456789L;

        ClientRegistrationResponse response = ClientRegistrationResponse.builder()
                .clientId("client-123")
                .clientSecret("secret-abc")
                .clientSecretExpiresAt(clientSecretExpiresAt)
                .registrationAccessToken(registrationAccessToken)
                .registrationClientUri("/oauth/register/client-123")
                .clientName("Test Client")
                .redirectUris(redirectUris)
                .tokenEndpointAuthMethod("client_secret_basic")
                .grantTypes(grantTypes)
                .scopes(scopes)
                .clientUri("https://example.com")
                .logoUri("https://example.com/logo.png")
                .resource(resource)
                .build();

        assertThat(response.getClientId()).isEqualTo("client-123");
        assertThat(response.getClientSecret()).isEqualTo("secret-abc");
        assertThat(response.getClientSecretExpiresAt()).isEqualTo(clientSecretExpiresAt);
        assertThat(response.getRegistrationAccessToken()).isEqualTo(registrationAccessToken);
        assertThat(response.getRegistrationClientUri()).isEqualTo("/oauth/register/client-123");
        assertThat(response.getClientName()).isEqualTo("Test Client");
        assertThat(response.getRedirectUris()).isEqualTo(redirectUris);
        assertThat(response.getTokenEndpointAuthMethod()).isEqualTo("client_secret_basic");
        assertThat(response.getGrantTypes()).isEqualTo(grantTypes);
        assertThat(response.getScopes()).isEqualTo(scopes);
        assertThat(response.getClientUri()).isEqualTo("https://example.com");
        assertThat(response.getLogoUri()).isEqualTo("https://example.com/logo.png");
        assertThat(response.getResource()).isEqualTo(resource);
    }

    @Test
    @DisplayName("@Builder 创建实例，所有字段类型正确")
    void builder_createsInstance_withCorrectFieldTypes() {
        List<String> listValue = List.of("value1", "value2");
        Long longValue = 123456789L;

        ClientRegistrationResponse response = ClientRegistrationResponse.builder()
                .clientId("clientId")
                .clientSecret("clientSecret")
                .clientSecretExpiresAt(longValue)
                .registrationAccessToken(longValue)
                .registrationClientUri("uri")
                .clientName("clientName")
                .redirectUris(listValue)
                .tokenEndpointAuthMethod("method")
                .grantTypes(listValue)
                .scopes(listValue)
                .clientUri("clientUri")
                .logoUri("logoUri")
                .resource(listValue)
                .build();

        assertThat(response.getClientId()).isInstanceOf(String.class);
        assertThat(response.getClientSecret()).isInstanceOf(String.class);
        assertThat(response.getClientSecretExpiresAt()).isInstanceOf(Long.class);
        assertThat(response.getRegistrationAccessToken()).isInstanceOf(Long.class);
        assertThat(response.getRegistrationClientUri()).isInstanceOf(String.class);
        assertThat(response.getClientName()).isInstanceOf(String.class);
        assertThat(response.getRedirectUris()).isInstanceOf(List.class);
        assertThat(response.getTokenEndpointAuthMethod()).isInstanceOf(String.class);
        assertThat(response.getGrantTypes()).isInstanceOf(List.class);
        assertThat(response.getScopes()).isInstanceOf(List.class);
        assertThat(response.getClientUri()).isInstanceOf(String.class);
        assertThat(response.getLogoUri()).isInstanceOf(String.class);
        assertThat(response.getResource()).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("getter 和 setter 正常工作")
    void gettersAndSetters_workCorrectly() {
        ClientRegistrationResponse response = new ClientRegistrationResponse();

        response.setClientId("client-123");
        assertThat(response.getClientId()).isEqualTo("client-123");

        response.setClientSecret("secret-abc");
        assertThat(response.getClientSecret()).isEqualTo("secret-abc");

        Long expiresAt = System.currentTimeMillis() + 86400000L;
        response.setClientSecretExpiresAt(expiresAt);
        assertThat(response.getClientSecretExpiresAt()).isEqualTo(expiresAt);

        Long accessToken = 987654321L;
        response.setRegistrationAccessToken(accessToken);
        assertThat(response.getRegistrationAccessToken()).isEqualTo(accessToken);

        response.setRegistrationClientUri("/oauth/register/client-123");
        assertThat(response.getRegistrationClientUri()).isEqualTo("/oauth/register/client-123");

        response.setClientName("Test Client");
        assertThat(response.getClientName()).isEqualTo("Test Client");

        List<String> redirectUris = List.of("https://example.com/callback");
        response.setRedirectUris(redirectUris);
        assertThat(response.getRedirectUris()).isEqualTo(redirectUris);

        response.setTokenEndpointAuthMethod("client_secret_basic");
        assertThat(response.getTokenEndpointAuthMethod()).isEqualTo("client_secret_basic");

        List<String> grantTypes = List.of("client_credentials");
        response.setGrantTypes(grantTypes);
        assertThat(response.getGrantTypes()).isEqualTo(grantTypes);

        List<String> scopes = List.of("read", "write");
        response.setScopes(scopes);
        assertThat(response.getScopes()).isEqualTo(scopes);

        response.setClientUri("https://example.com");
        assertThat(response.getClientUri()).isEqualTo("https://example.com");

        response.setLogoUri("https://example.com/logo.png");
        assertThat(response.getLogoUri()).isEqualTo("https://example.com/logo.png");

        List<String> resource = List.of("https://api.example.com");
        response.setResource(resource);
        assertThat(response.getResource()).isEqualTo(resource);
    }

    @Test
    @DisplayName("全参数构造函数创建实例")
    void constructor_withAllArgs_createsInstance() {
        List<String> redirectUris = List.of("https://example.com/callback");
        List<String> grantTypes = List.of("client_credentials");
        List<String> scopes = List.of("read");
        List<String> resource = List.of("https://api.example.com");
        Long clientSecretExpiresAt = System.currentTimeMillis() + 86400000L;
        Long registrationAccessToken = 123456789L;

        ClientRegistrationResponse response = new ClientRegistrationResponse(
                "client-123",
                "secret-abc",
                clientSecretExpiresAt,
                registrationAccessToken,
                "/oauth/register/client-123",
                "Test Client",
                redirectUris,
                "client_secret_basic",
                grantTypes,
                scopes,
                "https://example.com",
                "https://example.com/logo.png",
                resource
        );

        assertThat(response.getClientId()).isEqualTo("client-123");
        assertThat(response.getClientSecret()).isEqualTo("secret-abc");
        assertThat(response.getClientSecretExpiresAt()).isEqualTo(clientSecretExpiresAt);
        assertThat(response.getRegistrationAccessToken()).isEqualTo(registrationAccessToken);
        assertThat(response.getRegistrationClientUri()).isEqualTo("/oauth/register/client-123");
        assertThat(response.getClientName()).isEqualTo("Test Client");
        assertThat(response.getRedirectUris()).isEqualTo(redirectUris);
        assertThat(response.getTokenEndpointAuthMethod()).isEqualTo("client_secret_basic");
        assertThat(response.getGrantTypes()).isEqualTo(grantTypes);
        assertThat(response.getScopes()).isEqualTo(scopes);
        assertThat(response.getClientUri()).isEqualTo("https://example.com");
        assertThat(response.getLogoUri()).isEqualTo("https://example.com/logo.png");
        assertThat(response.getResource()).isEqualTo(resource);
    }

    @Test
    @DisplayName("clientSecret 可以为 null（none 认证方式时）")
    void builder_withNullClientSecret_works() {
        ClientRegistrationResponse response = ClientRegistrationResponse.builder()
                .clientId("client-123")
                .clientSecret(null)
                .clientSecretExpiresAt(0L)
                .build();

        assertThat(response.getClientId()).isEqualTo("client-123");
        assertThat(response.getClientSecret()).isNull();
        assertThat(response.getClientSecretExpiresAt()).isEqualTo(0L);
    }
}
