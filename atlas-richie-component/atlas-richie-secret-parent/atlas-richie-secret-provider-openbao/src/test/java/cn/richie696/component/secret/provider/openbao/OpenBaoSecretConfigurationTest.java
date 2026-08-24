package cn.richie696.component.secret.provider.openbao;

import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenBaoSecretConfigurationTest {
    @Test
    void rejectsNonLoopbackHttp() {
        OpenBaoSecretProperties p = new OpenBaoSecretProperties();
        p.setEndpoint(URI.create("http://openbao.example.internal"));
        assertThrows(RuntimeException.class, () -> OpenBaoSecretConfiguration.validate(p));
    }
}
