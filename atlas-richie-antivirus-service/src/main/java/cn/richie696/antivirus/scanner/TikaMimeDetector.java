package cn.richie696.antivirus.scanner;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 使用 tika-core 对有界文件头做 MIME 探测，不执行文档内容解析。
 */
@Component
public class TikaMimeDetector {
    private static final String FALLBACK_MIME_TYPE = "application/octet-stream";
    private final Tika tika = new Tika();

    public Detection probe(InputStream input, String sourceName, int maxProbeBytes) throws IOException {
        if (maxProbeBytes <= 0) {
            return new Detection(new byte[0], FALLBACK_MIME_TYPE);
        }

        ByteArrayOutputStream prefix = new ByteArrayOutputStream(Math.min(maxProbeBytes, 8 * 1024));
        byte[] buffer = new byte[Math.min(maxProbeBytes, 8 * 1024)];
        int remaining = maxProbeBytes;
        while (remaining > 0) {
            int read = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read < 0) {
                break;
            }
            if (read == 0) {
                continue;
            }
            prefix.write(buffer, 0, read);
            remaining -= read;
        }

        byte[] bytes = prefix.toByteArray();
        String mimeType;
        try {
            mimeType = tika.detect(new ByteArrayInputStream(bytes), fileName(sourceName));
        } catch (IOException | RuntimeException exception) {
            mimeType = FALLBACK_MIME_TYPE;
        }
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = FALLBACK_MIME_TYPE;
        }
        return new Detection(bytes, mimeType);
    }

    private String fileName(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            return null;
        }
        int separator = Math.max(sourceName.lastIndexOf('/'), sourceName.lastIndexOf('\\'));
        return separator >= 0 ? sourceName.substring(separator + 1) : sourceName;
    }

    public record Detection(byte[] prefix, String mimeType) {
    }
}
