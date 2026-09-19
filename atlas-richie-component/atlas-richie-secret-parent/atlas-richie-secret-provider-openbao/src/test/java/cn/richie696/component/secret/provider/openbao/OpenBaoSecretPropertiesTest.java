package cn.richie696.component.secret.provider.openbao;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenBaoSecretPropertiesTest {
    @Test
    void protectsNestedConfigurationAndSecretToken() {
        OpenBaoSecretProperties properties = new OpenBaoSecretProperties();
        properties.setEndpoint(URI.create("https://openbao.example"));
        properties.setNamespace("orders");
        properties.setAuthentication(null);
        properties.setKv(null);
        properties.setTransit(null);
        properties.setTls(null);
        properties.setProxy(null);
        properties.setSecrets(null);
        assertThat(properties.getEndpoint()).isEqualTo(URI.create("https://openbao.example"));
        assertThat(properties.getNamespace()).isEqualTo("orders");
        assertThat(properties.getAuthentication()).isNotNull();
        assertThat(properties.getKv()).isNotNull();
        assertThat(properties.getTransit()).isNotNull();
        assertThat(properties.getTls()).isNotNull();
        assertThat(properties.getProxy()).isNotNull();
        assertThat(properties.getSecrets()).isEmpty();

        OpenBaoSecretProperties.Authentication authentication = new OpenBaoSecretProperties.Authentication();
        authentication.setType(null);
        authentication.setToken("token".toCharArray());
        authentication.setTokenFile("/run/token");
        char[] token = authentication.getToken();
        token[0] = 'X';
        assertThat(authentication.getType()).isEqualTo(OpenBaoSecretProperties.AuthenticationType.TOKEN);
        assertThat(authentication.getToken()).containsExactly("token".toCharArray());
        assertThat(authentication.getTokenFile()).isEqualTo("/run/token");

        OpenBaoSecretProperties.Kv kv = new OpenBaoSecretProperties.Kv();
        kv.setMount("secret-v2"); kv.setRuntimePrefix("runtime-prod");
        assertThat(kv.getMount()).isEqualTo("secret-v2");
        assertThat(kv.getRuntimePrefix()).isEqualTo("runtime-prod");
        OpenBaoSecretProperties.Transit transit = new OpenBaoSecretProperties.Transit();
        transit.setMount("transit-prod"); transit.setKeyBindings(Map.of("key", "physical"));
        assertThat(transit.getMount()).isEqualTo("transit-prod");
        assertThat(transit.getKeyBindings()).containsEntry("key", "physical");
    }
}
