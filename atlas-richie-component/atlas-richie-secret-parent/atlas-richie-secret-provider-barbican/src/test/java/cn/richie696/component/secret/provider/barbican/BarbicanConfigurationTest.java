package cn.richie696.component.secret.provider.barbican;

import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BarbicanConfigurationTest {
    @Test
    void rejectsUnsafeSecretId() {
        BarbicanSecretProperties p = new BarbicanSecretProperties();
        p.setEndpoint(URI.create("https://barbican.example.internal"));
        BarbicanSecretProperties.SecretMapping mapping = new BarbicanSecretProperties.SecretMapping();
        mapping.setId("../secret");
        p.setSecrets(java.util.Map.of("password", mapping));
        assertThrows(RuntimeException.class, () -> BarbicanConfiguration.validate(p));
    }

    @Test
    void propertySourcePayloadMustBeStrictUtf8WhileBinaryReadsRemainBytes() {
        assertThat(BarbicanSecretClient.decodePropertyValue("secret".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isEqualTo("secret");
        assertThatThrownBy(() -> BarbicanSecretClient.decodePropertyValue(new byte[]{(byte) 0xc3, 0x28}))
                .hasMessageContaining("valid UTF-8");
    }
}
