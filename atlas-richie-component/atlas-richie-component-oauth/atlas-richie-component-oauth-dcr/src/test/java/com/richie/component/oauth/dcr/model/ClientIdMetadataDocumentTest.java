package com.richie.component.oauth.dcr.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClientIdMetadataDocument 模型测试")
class ClientIdMetadataDocumentTest {

    @Test
    @DisplayName("默认构造函数创建实例")
    void constructor_withNoArgs_createsInstance() {
        ClientIdMetadataDocument document = new ClientIdMetadataDocument();

        assertThat(document).isNotNull();
    }

    @Test
    @DisplayName("@Builder 创建实例，所有字段可访问")
    void builder_createsInstance_withAllFields() {
        List<String> redirectUris = List.of("https://example.com/callback");
        List<String> grantTypes = List.of("client_credentials");
        List<String> scopes = List.of("read", "write");
        List<String> contacts = List.of("admin@example.com");
        List<String> resource = List.of("https://api.example.com");

        ClientIdMetadataDocument document = ClientIdMetadataDocument.builder()
                .clientId("client-123")
                .clientSecret("secret-abc")
                .clientName("Test Client")
                .redirectUris(redirectUris)
                .tokenEndpointAuthMethod("client_secret_basic")
                .grantTypes(grantTypes)
                .scopes(scopes)
                .contacts(contacts)
                .clientUri("https://example.com")
                .logoUri("https://example.com/logo.png")
                .owner("owner@example.com")
                .tosUri("https://example.com/tos")
                .policyUri("https://example.com/policy")
                .jwksUri("https://example.com/.well-known/jwks.json")
                .resource(resource)
                .build();

        assertThat(document.getClientId()).isEqualTo("client-123");
        assertThat(document.getClientSecret()).isEqualTo("secret-abc");
        assertThat(document.getClientName()).isEqualTo("Test Client");
        assertThat(document.getRedirectUris()).isEqualTo(redirectUris);
        assertThat(document.getTokenEndpointAuthMethod()).isEqualTo("client_secret_basic");
        assertThat(document.getGrantTypes()).isEqualTo(grantTypes);
        assertThat(document.getScopes()).isEqualTo(scopes);
        assertThat(document.getContacts()).isEqualTo(contacts);
        assertThat(document.getClientUri()).isEqualTo("https://example.com");
        assertThat(document.getLogoUri()).isEqualTo("https://example.com/logo.png");
        assertThat(document.getOwner()).isEqualTo("owner@example.com");
        assertThat(document.getTosUri()).isEqualTo("https://example.com/tos");
        assertThat(document.getPolicyUri()).isEqualTo("https://example.com/policy");
        assertThat(document.getJwksUri()).isEqualTo("https://example.com/.well-known/jwks.json");
        assertThat(document.getResource()).isEqualTo(resource);
    }

    @Test
    @DisplayName("@Builder 创建实例，所有字段类型正确")
    void builder_createsInstance_withCorrectFieldTypes() {
        List<String> listValue = List.of("value1", "value2");

        ClientIdMetadataDocument document = ClientIdMetadataDocument.builder()
                .clientId("clientId")
                .clientSecret("clientSecret")
                .clientName("clientName")
                .redirectUris(listValue)
                .tokenEndpointAuthMethod("method")
                .grantTypes(listValue)
                .scopes(listValue)
                .contacts(listValue)
                .clientUri("clientUri")
                .logoUri("logoUri")
                .owner("owner")
                .tosUri("tosUri")
                .policyUri("policyUri")
                .jwksUri("jwksUri")
                .resource(listValue)
                .build();

        assertThat(document.getClientId()).isInstanceOf(String.class);
        assertThat(document.getClientSecret()).isInstanceOf(String.class);
        assertThat(document.getClientName()).isInstanceOf(String.class);
        assertThat(document.getRedirectUris()).isInstanceOf(List.class);
        assertThat(document.getTokenEndpointAuthMethod()).isInstanceOf(String.class);
        assertThat(document.getGrantTypes()).isInstanceOf(List.class);
        assertThat(document.getScopes()).isInstanceOf(List.class);
        assertThat(document.getContacts()).isInstanceOf(List.class);
        assertThat(document.getClientUri()).isInstanceOf(String.class);
        assertThat(document.getLogoUri()).isInstanceOf(String.class);
        assertThat(document.getOwner()).isInstanceOf(String.class);
        assertThat(document.getTosUri()).isInstanceOf(String.class);
        assertThat(document.getPolicyUri()).isInstanceOf(String.class);
        assertThat(document.getJwksUri()).isInstanceOf(String.class);
        assertThat(document.getResource()).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("getter 和 setter 正常工作")
    void gettersAndSetters_workCorrectly() {
        ClientIdMetadataDocument document = new ClientIdMetadataDocument();

        document.setClientId("client-123");
        assertThat(document.getClientId()).isEqualTo("client-123");

        document.setClientSecret("secret-abc");
        assertThat(document.getClientSecret()).isEqualTo("secret-abc");

        document.setClientName("Test Client");
        assertThat(document.getClientName()).isEqualTo("Test Client");

        List<String> redirectUris = List.of("https://example.com/callback");
        document.setRedirectUris(redirectUris);
        assertThat(document.getRedirectUris()).isEqualTo(redirectUris);

        document.setTokenEndpointAuthMethod("client_secret_basic");
        assertThat(document.getTokenEndpointAuthMethod()).isEqualTo("client_secret_basic");

        List<String> grantTypes = List.of("client_credentials");
        document.setGrantTypes(grantTypes);
        assertThat(document.getGrantTypes()).isEqualTo(grantTypes);

        List<String> scopes = List.of("read", "write");
        document.setScopes(scopes);
        assertThat(document.getScopes()).isEqualTo(scopes);

        List<String> contacts = List.of("admin@example.com");
        document.setContacts(contacts);
        assertThat(document.getContacts()).isEqualTo(contacts);

        document.setClientUri("https://example.com");
        assertThat(document.getClientUri()).isEqualTo("https://example.com");

        document.setLogoUri("https://example.com/logo.png");
        assertThat(document.getLogoUri()).isEqualTo("https://example.com/logo.png");

        document.setOwner("owner@example.com");
        assertThat(document.getOwner()).isEqualTo("owner@example.com");

        document.setTosUri("https://example.com/tos");
        assertThat(document.getTosUri()).isEqualTo("https://example.com/tos");

        document.setPolicyUri("https://example.com/policy");
        assertThat(document.getPolicyUri()).isEqualTo("https://example.com/policy");

        document.setJwksUri("https://example.com/.well-known/jwks.json");
        assertThat(document.getJwksUri()).isEqualTo("https://example.com/.well-known/jwks.json");

        List<String> resource = List.of("https://api.example.com");
        document.setResource(resource);
        assertThat(document.getResource()).isEqualTo(resource);
    }

    @Test
    @DisplayName("全参数构造函数创建实例")
    void constructor_withAllArgs_createsInstance() {
        List<String> redirectUris = List.of("https://example.com/callback");
        List<String> grantTypes = List.of("client_credentials");
        List<String> scopes = List.of("read");
        List<String> contacts = List.of("admin@example.com");
        List<String> resource = List.of("https://api.example.com");

        ClientIdMetadataDocument document = new ClientIdMetadataDocument(
                "client-123",
                "secret-abc",
                "Test Client",
                redirectUris,
                "client_secret_basic",
                grantTypes,
                scopes,
                contacts,
                "https://example.com",
                "https://example.com/logo.png",
                "owner@example.com",
                "https://example.com/tos",
                "https://example.com/policy",
                "https://example.com/.well-known/jwks.json",
                resource
        );

        assertThat(document.getClientId()).isEqualTo("client-123");
        assertThat(document.getClientSecret()).isEqualTo("secret-abc");
        assertThat(document.getClientName()).isEqualTo("Test Client");
        assertThat(document.getRedirectUris()).isEqualTo(redirectUris);
        assertThat(document.getTokenEndpointAuthMethod()).isEqualTo("client_secret_basic");
        assertThat(document.getGrantTypes()).isEqualTo(grantTypes);
        assertThat(document.getScopes()).isEqualTo(scopes);
        assertThat(document.getContacts()).isEqualTo(contacts);
        assertThat(document.getClientUri()).isEqualTo("https://example.com");
        assertThat(document.getLogoUri()).isEqualTo("https://example.com/logo.png");
        assertThat(document.getOwner()).isEqualTo("owner@example.com");
        assertThat(document.getTosUri()).isEqualTo("https://example.com/tos");
        assertThat(document.getPolicyUri()).isEqualTo("https://example.com/policy");
        assertThat(document.getJwksUri()).isEqualTo("https://example.com/.well-known/jwks.json");
        assertThat(document.getResource()).isEqualTo(resource);
    }
}
