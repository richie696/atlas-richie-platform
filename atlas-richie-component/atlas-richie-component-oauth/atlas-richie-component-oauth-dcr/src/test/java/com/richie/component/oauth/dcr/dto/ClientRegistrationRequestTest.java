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
package com.richie.component.oauth.dcr.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClientRegistrationRequest DTO 测试")
class ClientRegistrationRequestTest {

    @Test
    @DisplayName("默认构造函数创建实例")
    void constructor_withNoArgs_createsInstance() {
        ClientRegistrationRequest request = new ClientRegistrationRequest();

        assertThat(request).isNotNull();
    }

    @Test
    @DisplayName("@Builder 创建实例，所有字段可访问")
    void builder_createsInstance_withAllFields() {
        List<String> redirectUris = List.of("https://example.com/callback");
        List<String> grantTypes = List.of("client_credentials");
        List<String> scopes = List.of("read", "write");
        List<String> resource = List.of("https://api.example.com");

        ClientRegistrationRequest request = ClientRegistrationRequest.builder()
                .clientName("Test Client")
                .clientUri("https://example.com")
                .logoUri("https://example.com/logo.png")
                .redirectUris(redirectUris)
                .tokenEndpointAuthMethod("client_secret_basic")
                .grantTypes(grantTypes)
                .scopes(scopes)
                .jwks("{\"keys\":[]}")
                .jwksUri("https://example.com/.well-known/jwks.json")
                .softwareId("software-123")
                .softwareVersion("1.0.0")
                .resource(resource)
                .build();

        assertThat(request.getClientName()).isEqualTo("Test Client");
        assertThat(request.getClientUri()).isEqualTo("https://example.com");
        assertThat(request.getLogoUri()).isEqualTo("https://example.com/logo.png");
        assertThat(request.getRedirectUris()).isEqualTo(redirectUris);
        assertThat(request.getTokenEndpointAuthMethod()).isEqualTo("client_secret_basic");
        assertThat(request.getGrantTypes()).isEqualTo(grantTypes);
        assertThat(request.getScopes()).isEqualTo(scopes);
        assertThat(request.getJwks()).isEqualTo("{\"keys\":[]}");
        assertThat(request.getJwksUri()).isEqualTo("https://example.com/.well-known/jwks.json");
        assertThat(request.getSoftwareId()).isEqualTo("software-123");
        assertThat(request.getSoftwareVersion()).isEqualTo("1.0.0");
        assertThat(request.getResource()).isEqualTo(resource);
    }

    @Test
    @DisplayName("@Builder 创建实例，所有字段类型正确")
    void builder_createsInstance_withCorrectFieldTypes() {
        List<String> listValue = List.of("value1", "value2");

        ClientRegistrationRequest request = ClientRegistrationRequest.builder()
                .clientName("name")
                .clientUri("uri")
                .logoUri("logo")
                .redirectUris(listValue)
                .tokenEndpointAuthMethod("method")
                .grantTypes(listValue)
                .scopes(listValue)
                .jwks("jwks")
                .jwksUri("jwksUri")
                .softwareId("softwareId")
                .softwareVersion("version")
                .resource(listValue)
                .build();

        assertThat(request.getClientName()).isInstanceOf(String.class);
        assertThat(request.getClientUri()).isInstanceOf(String.class);
        assertThat(request.getLogoUri()).isInstanceOf(String.class);
        assertThat(request.getRedirectUris()).isInstanceOf(List.class);
        assertThat(request.getTokenEndpointAuthMethod()).isInstanceOf(String.class);
        assertThat(request.getGrantTypes()).isInstanceOf(List.class);
        assertThat(request.getScopes()).isInstanceOf(List.class);
        assertThat(request.getJwks()).isInstanceOf(String.class);
        assertThat(request.getJwksUri()).isInstanceOf(String.class);
        assertThat(request.getSoftwareId()).isInstanceOf(String.class);
        assertThat(request.getSoftwareVersion()).isInstanceOf(String.class);
        assertThat(request.getResource()).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("getter 和 setter 正常工作")
    void gettersAndSetters_workCorrectly() {
        ClientRegistrationRequest request = new ClientRegistrationRequest();

        request.setClientName("Test Client");
        assertThat(request.getClientName()).isEqualTo("Test Client");

        request.setClientUri("https://example.com");
        assertThat(request.getClientUri()).isEqualTo("https://example.com");

        request.setLogoUri("https://example.com/logo.png");
        assertThat(request.getLogoUri()).isEqualTo("https://example.com/logo.png");

        List<String> redirectUris = List.of("https://example.com/callback");
        request.setRedirectUris(redirectUris);
        assertThat(request.getRedirectUris()).isEqualTo(redirectUris);

        request.setTokenEndpointAuthMethod("client_secret_basic");
        assertThat(request.getTokenEndpointAuthMethod()).isEqualTo("client_secret_basic");

        List<String> grantTypes = List.of("client_credentials");
        request.setGrantTypes(grantTypes);
        assertThat(request.getGrantTypes()).isEqualTo(grantTypes);

        List<String> scopes = List.of("read", "write");
        request.setScopes(scopes);
        assertThat(request.getScopes()).isEqualTo(scopes);

        request.setJwks("{\"keys\":[]}");
        assertThat(request.getJwks()).isEqualTo("{\"keys\":[]}");

        request.setJwksUri("https://example.com/.well-known/jwks.json");
        assertThat(request.getJwksUri()).isEqualTo("https://example.com/.well-known/jwks.json");

        request.setSoftwareId("software-123");
        assertThat(request.getSoftwareId()).isEqualTo("software-123");

        request.setSoftwareVersion("1.0.0");
        assertThat(request.getSoftwareVersion()).isEqualTo("1.0.0");

        List<String> resource = List.of("https://api.example.com");
        request.setResource(resource);
        assertThat(request.getResource()).isEqualTo(resource);
    }

    @Test
    @DisplayName("全参数构造函数创建实例")
    void constructor_withAllArgs_createsInstance() {
        List<String> redirectUris = List.of("https://example.com/callback");
        List<String> grantTypes = List.of("client_credentials");
        List<String> scopes = List.of("read");
        List<String> resource = List.of("https://api.example.com");

        ClientRegistrationRequest request = new ClientRegistrationRequest(
                "Test Client",
                "https://example.com",
                "https://example.com/logo.png",
                redirectUris,
                "client_secret_basic",
                grantTypes,
                scopes,
                "{\"keys\":[]}",
                "https://example.com/.well-known/jwks.json",
                "software-123",
                "1.0.0",
                resource
        );

        assertThat(request.getClientName()).isEqualTo("Test Client");
        assertThat(request.getClientUri()).isEqualTo("https://example.com");
        assertThat(request.getLogoUri()).isEqualTo("https://example.com/logo.png");
        assertThat(request.getRedirectUris()).isEqualTo(redirectUris);
        assertThat(request.getTokenEndpointAuthMethod()).isEqualTo("client_secret_basic");
        assertThat(request.getGrantTypes()).isEqualTo(grantTypes);
        assertThat(request.getScopes()).isEqualTo(scopes);
        assertThat(request.getJwks()).isEqualTo("{\"keys\":[]}");
        assertThat(request.getJwksUri()).isEqualTo("https://example.com/.well-known/jwks.json");
        assertThat(request.getSoftwareId()).isEqualTo("software-123");
        assertThat(request.getSoftwareVersion()).isEqualTo("1.0.0");
        assertThat(request.getResource()).isEqualTo(resource);
    }
}
