package cn.richie696.component.secret.provider.pkcs11;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Pkcs11SecretConfigurationTest {
    @Test
    void requiresLibraryPinAndBinding() {
        assertThrows(RuntimeException.class, () -> Pkcs11SecretConfiguration.validate(new Pkcs11SecretProperties()));
    }
}
