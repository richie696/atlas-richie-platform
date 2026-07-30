package cn.richie696.antivirus.scanner;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TikaMimeDetectorTest {
    private final TikaMimeDetector detector = new TikaMimeDetector();

    @Test
    void detectsPngFromContentAndPreservesProbeBytes() throws Exception {
        byte[] content = new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52
        };

        TikaMimeDetector.Detection detection =
                detector.probe(new ByteArrayInputStream(content), "tenant/image.bin", 64);

        assertThat(detection.mimeType()).isEqualTo("image/png");
        assertThat(detection.prefix()).containsExactly(content);
    }

    @Test
    void readsOnlyConfiguredPrefix() throws Exception {
        byte[] content = "0123456789".getBytes(StandardCharsets.UTF_8);

        TikaMimeDetector.Detection detection =
                detector.probe(new ByteArrayInputStream(content), "sample.txt", 4);

        assertThat(detection.prefix()).containsExactly(
                (byte) '0', (byte) '1', (byte) '2', (byte) '3');
        assertThat(detection.mimeType()).isEqualTo("text/plain");
    }
}
