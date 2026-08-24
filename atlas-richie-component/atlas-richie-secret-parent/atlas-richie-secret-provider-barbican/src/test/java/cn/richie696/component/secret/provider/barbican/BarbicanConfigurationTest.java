package cn.richie696.component.secret.provider.barbican;

import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
