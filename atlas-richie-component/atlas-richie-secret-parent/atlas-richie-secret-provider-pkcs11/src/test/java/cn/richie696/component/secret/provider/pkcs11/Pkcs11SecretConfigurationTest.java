package cn.richie696.component.secret.provider.pkcs11;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Pkcs11SecretConfigurationTest {
    @Test
    void requiresLibraryPinAndBinding() {
        assertThrows(RuntimeException.class, () -> Pkcs11SecretConfiguration.validate(new Pkcs11SecretProperties()));
    }

    @Test
    void rejectsUnsupportedSelectorsAndBindings() throws Exception {
        Path library = Files.createTempFile("pkcs11", ".so");
        try {
            assertInvalid(library, p -> p.setSlot(-1));
            assertInvalid(library, p -> p.setPin(new char[0]));
            assertInvalid(library, p -> p.setKeyBindings(Map.of()));
            assertInvalid(library, p -> p.setSigningAlgorithm("none"));
            assertInvalid(library, p -> p.setKeyBindings(Map.of("../logical", "physical")));
            assertInvalid(library, p -> p.setKeyBindings(Map.of("logical", "../physical")));
            assertInvalid(library, p -> p.setVerificationKeyBindings(Map.of("logical", List.of())));
            assertInvalid(library, p -> p.setVerificationKeyBindings(Map.of("../logical", List.of("physical"))));
            assertInvalid(library, p -> p.setVerificationKeyBindings(Map.of("logical", List.of("../physical"))));
        } finally {
            Files.deleteIfExists(library);
        }
    }

    private static void assertInvalid(Path library, java.util.function.Consumer<Pkcs11SecretProperties> customizer) {
        Pkcs11SecretProperties properties = new Pkcs11SecretProperties();
        properties.setLibrary(library.toString());
        properties.setPin("pin".toCharArray());
        properties.setKeyBindings(Map.of("logical", "physical"));
        customizer.accept(properties);
        assertThrows(RuntimeException.class, () -> Pkcs11SecretConfiguration.validate(properties));
    }
}
