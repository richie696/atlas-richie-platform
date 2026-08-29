package cn.richie696.component.secret.provider.common;

import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class RemoteProviderModuleSupportTest {
    @Test
    void rejectsNonLoopbackHttpEndpoint() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setEndpoint(URI.create("http://provider.example"));
        assertThatThrownBy(() -> RemoteProviderModuleSupport.validate("azure", properties))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void rejectsUnsafeLogicalMappings() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setEndpoint(URI.create("https://provider.example"));
        properties.setKeyBindings(Map.of("../key", "physical-key"));
        assertThatThrownBy(() -> RemoteProviderModuleSupport.validate("gcp", properties))
                .isInstanceOf(SecretConfigurationException.class);
    }

    @Test
    void requiresCredentialsForConfiguredAuthenticationMode() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setEndpoint(URI.create("https://provider.example"));
        assertThatThrownBy(() -> RemoteProviderModuleSupport.validate("gcp", properties))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("bearer token");
    }

    @Test
    void rejectsUnsafeWireTemplates() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setEndpoint(URI.create("https://provider.example"));
        properties.getAuthentication().setType(RemoteProviderProperties.AuthenticationType.NONE);
        properties.getWire().setSecretPath("../secrets/{path}");
        assertThatThrownBy(() -> RemoteProviderModuleSupport.validate("azure", properties))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("wire path");
    }

    @Test
    void appliesOfficialGcpWireShapeWithoutChangingExplicitOverrides() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        OfficialWireProfiles.apply("gcp", properties);
        assertThat(properties.getWire().getSecretPath())
                .isEqualTo("/v1/projects/{projectId}/secrets/{path}/versions/{version}:access");
        assertThat(properties.getWire().getSecretValueField()).isEqualTo("payload.data");
        assertThat(properties.getWire().getSecretValueEncoding()).isEqualTo("BASE64");
        assertThat(properties.getWire().getRequestWrapValueField()).isEqualTo("plaintext");
        assertThat(properties.getWire().getRequestUnwrapValueField()).isEqualTo("ciphertext");

        RemoteProviderProperties custom = new RemoteProviderProperties();
        custom.getWire().setSecretPath("/contract/{path}");
        custom.getWire().setRequestValueField("payload");
        custom.getWire().setRequestAadField("customAad");
        OfficialWireProfiles.apply("gcp", custom);
        assertThat(custom.getWire().getSecretPath()).isEqualTo("/contract/{path}");
        assertThat(custom.getWire().getRequestWrapValueField()).isBlank();
        assertThat(custom.getWire().getRequestAadField()).isEqualTo("customAad");
    }

    @Test
    void rejectsInvalidProxyPortAndMissingTlsMaterial() {
        RemoteProviderProperties proxy = validNoneProperties();
        proxy.getProxy().setHost("proxy.example");
        proxy.getProxy().setPort(70000);
        assertThatThrownBy(() -> RemoteProviderModuleSupport.validate("azure", proxy))
                .isInstanceOf(SecretConfigurationException.class).hasMessageContaining("proxy port");

        RemoteProviderProperties tls = validNoneProperties();
        tls.getTls().setTrustStore("/path/that/does/not/exist.p12");
        assertThatThrownBy(() -> RemoteProviderModuleSupport.validate("azure", tls))
                .isInstanceOf(SecretConfigurationException.class).hasMessageContaining("trust-store");
    }

    @Test
    void vendorAccessKeyProfilesSelectDocumentedSignatureProtocols() {
        assertThat(signatureFor("huawei")).isEqualTo(RemoteProviderProperties.RequestSignature.HUAWEI_SDK_HMAC_SHA256);
        assertThat(signatureFor("tencent")).isEqualTo(RemoteProviderProperties.RequestSignature.TENCENT_TC3_HMAC_SHA256);
        assertThat(signatureFor("volcengine")).isEqualTo(RemoteProviderProperties.RequestSignature.VOLCENGINE_HMAC_SHA256);
        assertThat(signatureFor("baidu")).isEqualTo(RemoteProviderProperties.RequestSignature.BAIDU_BCE_V2);
        assertThat(signatureFor("gcp")).isEqualTo(RemoteProviderProperties.RequestSignature.NONE);
    }

    @Test
    void appliesOfficialVolcengineKmsWireShape() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        OfficialWireProfiles.apply("volcengine", properties);

        assertThat(properties.getWire().getWrapPath()).isEqualTo(
                "/?Action=Encrypt&Version=2021-02-18&KeyringName={namespace}&KeyName={key}");
        assertThat(properties.getWire().getUnwrapPath())
                .isEqualTo("/?Action=Decrypt&Version=2021-02-18");
        assertThat(properties.getWire().getRequestWrapValueField()).isEqualTo("Plaintext");
        assertThat(properties.getWire().getRequestUnwrapValueField()).isEqualTo("CiphertextBlob");
        assertThat(properties.getWire().getRequestAadField()).isEqualTo("EncryptionContext");
        assertThat(properties.getWire().getRequestAadEncoding())
                .isEqualTo(RemoteProviderProperties.RequestAadEncoding.ATTRIBUTES);
        assertThat(properties.getWire().getWrappedKeyField()).isEqualTo("Result.CiphertextBlob");
        assertThat(properties.getWire().getPlaintextField()).isEqualTo("Result.Plaintext");
    }

    @Test
    void detectsTransportCredentialRotationWithoutExposingTheCredential() {
        RemoteProviderProperties properties = validNoneProperties();
        properties.getProxy().setHost("proxy.example");
        properties.getProxy().setPort(8080);
        properties.getProxy().setPassword("first".toCharArray());
        String first = RemoteProviderModuleSupport.hash("gcp", properties);
        properties.getProxy().setPassword("second".toCharArray());
        String second = RemoteProviderModuleSupport.hash("gcp", properties);
        assertThat(second).isNotEqualTo(first);
        assertThat(first).doesNotContain("first").doesNotContain("second");
        assertThat(second).doesNotContain("first").doesNotContain("second");
    }

    private static RemoteProviderProperties.RequestSignature signatureFor(String provider) {
        RemoteProviderProperties properties = validNoneProperties();
        properties.getAuthentication().setType(RemoteProviderProperties.AuthenticationType.ACCESS_KEY);
        properties.getAuthentication().setAccessKeyId("ak");
        properties.getAuthentication().setAccessKeySecret("sk".toCharArray());
        OfficialWireProfiles.apply(provider, properties);
        return properties.getAuthentication().getSignature();
    }

    private static RemoteProviderProperties validNoneProperties() {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.setEndpoint(URI.create("https://provider.example"));
        properties.getAuthentication().setType(RemoteProviderProperties.AuthenticationType.NONE);
        return properties;
    }
}
