package cn.richie696.component.secret.provider.common;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteProviderPropertiesTest {

    @Test
    void exposesTopLevelDefaultsAndDefensiveMaps() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setEndpoint(URI.create("https://provider.example"));
        properties.setSecretEndpoint(URI.create("https://secret.example"));
        properties.setKmsEndpoint(URI.create("https://kms.example"));
        properties.setRegion("region");
        properties.setProjectId("project");
        properties.setTenantId("tenant");
        properties.setNamespace("namespace");
        properties.setApiVersion("v1");
        properties.setAuthentication(null);
        properties.setWire(null);
        properties.setTls(null);
        properties.setProxy(null);
        RemoteProviderProperties.SecretMapping mapping = new RemoteProviderProperties.SecretMapping();
        mapping.setPath("orders");
        mapping.setField("password");
        properties.setSecrets(Map.of("database", mapping));
        properties.setKeyBindings(Map.of("key", "physical"));

        assertThat(properties.getSecretEndpoint()).isEqualTo(URI.create("https://secret.example"));
        assertThat(properties.getKmsEndpoint()).isEqualTo(URI.create("https://kms.example"));
        assertThat(properties.getRegion()).isEqualTo("region");
        assertThat(properties.getProjectId()).isEqualTo("project");
        assertThat(properties.getTenantId()).isEqualTo("tenant");
        assertThat(properties.getNamespace()).isEqualTo("namespace");
        assertThat(properties.getApiVersion()).isEqualTo("v1");
        assertThat(properties.getAuthentication()).isNotNull();
        assertThat(properties.getWire()).isNotNull();
        assertThat(properties.getTls()).isNotNull();
        assertThat(properties.getProxy()).isNotNull();
        assertThat(properties.getSecrets()).containsKey("database");
        assertThat(properties.getKeyBindings()).containsEntry("key", "physical");
        properties.setSecrets(null);
        properties.setKeyBindings(null);
        assertThat(properties.getSecrets()).isEmpty();
        assertThat(properties.getKeyBindings()).isEmpty();
    }

    @Test
    void protectsAuthenticationSecretsAndSupportsAllAuthenticationFields() {
        RemoteProviderProperties.Authentication authentication = new RemoteProviderProperties.Authentication();
        authentication.setType(null);
        authentication.setToken("token".toCharArray());
        authentication.setTokenFile("/run/token");
        authentication.setAccessKeyId("access");
        authentication.setAccessKeySecret("secret".toCharArray());
        authentication.setSecurityToken("session");
        authentication.setSigningService("kms");
        authentication.setSigningRegion("region");
        authentication.setApiAction("Encrypt");
        authentication.setApiVersion("v2");
        authentication.setSignature(null);
        authentication.setSignatureExpirationSeconds(60);
        authentication.setWorkloadIdentityTokenFile("/run/workload");

        char[] token = authentication.getToken();
        token[0] = 'X';
        char[] secret = authentication.getAccessKeySecret();
        secret[0] = 'X';

        assertThat(authentication.getType()).isEqualTo(RemoteProviderProperties.AuthenticationType.BEARER_TOKEN);
        assertThat(authentication.getToken()).containsExactly("token".toCharArray());
        assertThat(authentication.getTokenFile()).isEqualTo("/run/token");
        assertThat(authentication.getAccessKeyId()).isEqualTo("access");
        assertThat(authentication.getAccessKeySecret()).containsExactly("secret".toCharArray());
        assertThat(authentication.getSecurityToken()).isEqualTo("session");
        assertThat(authentication.getSigningService()).isEqualTo("kms");
        assertThat(authentication.getSigningRegion()).isEqualTo("region");
        assertThat(authentication.getApiAction()).isEqualTo("Encrypt");
        assertThat(authentication.getApiVersion()).isEqualTo("v2");
        assertThat(authentication.getSignature()).isEqualTo(RemoteProviderProperties.RequestSignature.NONE);
        assertThat(authentication.getSignatureExpirationSeconds()).isEqualTo(60);
        assertThat(authentication.getWorkloadIdentityTokenFile()).isEqualTo("/run/workload");
    }

    @Test
    void supportsWireTlsAndProxyConfiguration() {
        RemoteProviderProperties.Wire wire = new RemoteProviderProperties.Wire();
        wire.setSecretPath(null); wire.setSecretLatestPath(null); wire.setSecretMethod(null);
        wire.setSecretRequestNameField(null); wire.setSecretRequestVersionField(null); wire.setLatestVersionValue(null);
        wire.setWrapPath(null); wire.setUnwrapPath(null); wire.setSecretValueField(null);
        wire.setSecretVersionField(null); wire.setSecretCreatedAtField(null); wire.setWrappedKeyField(null);
        wire.setPlaintextField(null); wire.setSecretValueEncoding(null); wire.setRequestValueField(null);
        wire.setRequestWrapValueField(null); wire.setRequestUnwrapValueField(null); wire.setRequestAadField(null);
        wire.setRequestAadEncoding(null); wire.setRequestKeyField(null); wire.setRequestValueEncoding(null);
        wire.setResponseValueEncoding(null); wire.setRequestAlgorithmField(null); wire.setRequestAlgorithm(null);
        wire.setSecretAction(null); wire.setSecretApiVersion(null); wire.setSecretSigningService(null);
        wire.setWrapAction(null); wire.setUnwrapAction(null); wire.setKmsApiVersion(null); wire.setKmsSigningService(null);
        assertThat(wire.getSecretMethod()).isEqualTo(RemoteProviderProperties.HttpMethod.GET);
        assertThat(wire.getLatestVersionValue()).isEqualTo("latest");
        assertThat(wire.getRequestAadEncoding()).isEqualTo(RemoteProviderProperties.RequestAadEncoding.BASE64);
        assertThat(wire.getRequestValueEncoding()).isEqualTo(RemoteProviderProperties.ValueEncoding.BASE64);
        assertThat(wire.getResponseValueEncoding()).isEqualTo(RemoteProviderProperties.ValueEncoding.BASE64);

        RemoteProviderProperties.Tls tls = new RemoteProviderProperties.Tls();
        tls.setTrustStore("trust"); tls.setTrustStorePassword("trust-pass".toCharArray());
        tls.setKeyStore("key"); tls.setKeyStorePassword("key-pass".toCharArray()); tls.setKeyStoreType(null);
        assertThat(tls.getTrustStore()).isEqualTo("trust");
        assertThat(tls.getTrustStorePassword()).containsExactly("trust-pass".toCharArray());
        assertThat(tls.getKeyStore()).isEqualTo("key");
        assertThat(tls.getKeyStorePassword()).containsExactly("key-pass".toCharArray());
        assertThat(tls.getKeyStoreType()).isEqualTo("PKCS12");

        RemoteProviderProperties.Proxy proxy = new RemoteProviderProperties.Proxy();
        proxy.setHost("proxy"); proxy.setPort(8080); proxy.setUsername("user"); proxy.setPassword("pass".toCharArray());
        assertThat(proxy.getHost()).isEqualTo("proxy");
        assertThat(proxy.getPort()).isEqualTo(8080);
        assertThat(proxy.getUsername()).isEqualTo("user");
        assertThat(proxy.getPassword()).containsExactly("pass".toCharArray());
    }
}
