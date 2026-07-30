package cn.richie696.antivirus.download;

import cn.richie696.antivirus.config.AntivirusProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class PublicUrlValidatorTest {
    private final AntivirusProperties properties = new AntivirusProperties();
    private final PublicUrlValidator validator = new PublicUrlValidator(properties);

    @Test
    void allowsPublicHttpsAddress() {
        assertThatNoException().isThrownBy(
                () -> validator.validate(URI.create("https://8.8.8.8/file.bin")));
    }

    @Test
    void rejectsPrivateAndMetadataAddresses() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> validator.validate(URI.create("https://127.0.0.1/file.bin")));
        assertThatIllegalArgumentException().isThrownBy(
                () -> validator.validate(URI.create("https://10.0.0.1/file.bin")));
        assertThatIllegalArgumentException().isThrownBy(
                () -> validator.validate(URI.create("https://169.254.169.254/latest/meta-data")));
    }

    @Test
    void rejectsHttpByDefault() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> validator.validate(URI.create("http://8.8.8.8/file.bin")));
    }
}
