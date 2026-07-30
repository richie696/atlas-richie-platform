package cn.richie696.antivirus.scanner;

import cn.richie696.antivirus.config.AntivirusProperties;
import cn.richie696.antivirus.model.ScanStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** clamd Unix Socket INSTREAM 协议客户端。 */
@Component
public class ClamdClient {
    private static final int BUFFER_SIZE = 32 * 1024;
    private final AntivirusProperties properties;
    private final TikaMimeDetector mimeDetector;

    public ClamdClient(AntivirusProperties properties, TikaMimeDetector mimeDetector) {
        this.properties = properties;
        this.mimeDetector = mimeDetector;
    }

    /**
     * 查询 clamd 版本与病毒库版本。响应形如 {@code "ClamAV 1.5.3/28077/Mon Jul 30 22:51:43 2026"}。
     */
    public ClamdVersion version() throws IOException {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(properties.getClamav().getSocketPath()));
            channel.write(StandardCharsets.US_ASCII.encode("VERSION\0"));
            channel.shutdownOutput();
            String response = readResponse(channel);
            return ClamdVersion.parse(response);
        }
    }

    public ScanOutcome scan(InputStream input, String sourceName) throws IOException {
        return scan(input, sourceName, null, null);
    }

    public ScanOutcome scan(InputStream input, String sourceName,
                            String engineVersion, String signatureVersion) throws IOException {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(properties.getClamav().getSocketPath()));
            DataOutputStream output = new DataOutputStream(Channels.newOutputStream(channel));
            output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            TikaMimeDetector.Detection detection =
                    mimeDetector.probe(input, sourceName, properties.getClamav().getMimeProbeBytes());
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0;

            if (detection.prefix().length > 0) {
                total = writeChunk(output, digest, detection.prefix(), detection.prefix().length, total);
            }
            for (int read; (read = input.read(buffer)) >= 0; ) {
                if (read == 0) {
                    continue;
                }
                total = writeChunk(output, digest, buffer, read, total);
            }
            output.writeInt(0);
            output.flush();
            channel.shutdownOutput();

            String response = readResponse(channel);
            String sha256 = HexFormat.of().formatHex(digest.digest());
            String resolvedEngine = engineVersion != null ? engineVersion : "clamd";
            if (response.endsWith(" OK")) {
                return new ScanOutcome(
                        ScanStatus.CLEAN, total, sha256, detection.mimeType(),
                        null, resolvedEngine, signatureVersion, null);
            }
            if (response.endsWith(" FOUND")) {
                String threat = response.substring(0, response.length() - " FOUND".length())
                        .replaceFirst("^[^:]+: ", "");
                return new ScanOutcome(
                        ScanStatus.INFECTED, total, sha256, detection.mimeType(),
                        threat, resolvedEngine, signatureVersion, null);
            }
            return ScanOutcome.failed("clamd 返回异常：" + response);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JRE 不支持 SHA-256", exception);
        }
    }

    private long writeChunk(DataOutputStream output, MessageDigest digest,
                            byte[] bytes, int length, long currentTotal) throws IOException {
        long nextTotal = currentTotal + length;
        if (nextTotal > properties.getClamav().getMaxFileSizeBytes()) {
            throw new IOException("对象超过扫描大小限制");
        }
        digest.update(bytes, 0, length);
        output.writeInt(length);
        output.write(bytes, 0, length);
        return nextTotal;
    }

    private String readResponse(SocketChannel channel) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        while (channel.read(buffer) != -1) {
            buffer.flip();
            bytes.write(buffer.array(), 0, buffer.remaining());
            buffer.clear();
        }
        return bytes.toString(StandardCharsets.UTF_8).trim();
    }
}