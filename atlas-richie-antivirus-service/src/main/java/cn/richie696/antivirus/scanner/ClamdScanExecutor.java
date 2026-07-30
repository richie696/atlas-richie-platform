package cn.richie696.antivirus.scanner;

import cn.richie696.antivirus.config.AntivirusProperties;
import cn.richie696.antivirus.download.PublicHttpFileClient;
import cn.richie696.antivirus.model.ScanStatus;
import cn.richie696.antivirus.model.ScanTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 受控 HTTP 流下载或本地文件直读后，再经同 Pod Unix Socket 调用 clamd。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "platform.antivirus.clamav", name = "enabled", havingValue = "true")
public class ClamdScanExecutor implements ScanExecutor {
    private final AntivirusProperties properties;
    private final PublicHttpFileClient fileClient;
    private final ClamdClient clamdClient;

    public ClamdScanExecutor(AntivirusProperties properties,
                             PublicHttpFileClient fileClient,
                             ClamdClient clamdClient) {
        this.properties = properties;
        this.fileClient = fileClient;
        this.clamdClient = clamdClient;
    }

    @Override
    public ScanOutcome scan(ScanTask task) {
        if (task.getExpectedSize() != null
                && task.getExpectedSize() > properties.getClamav().getMaxFileSizeBytes()) {
            return ScanOutcome.failed("对象超过扫描大小限制");
        }
        if (task.getLocalPath() != null && !task.getLocalPath().isBlank()) {
            return scanLocalFile(task);
        }
        return scanRemoteUrl(task);
    }

    private ScanOutcome scanLocalFile(ScanTask task) {
        ClamdVersion version = resolveClamdVersion();
        try (InputStream input = Files.newInputStream(Paths.get(task.getLocalPath()))) {
            String sourceName = task.getFileName() != null
                    ? task.getFileName()
                    : Paths.get(task.getLocalPath()).getFileName().toString();
            ScanOutcome outcome = clamdClient.scan(input, sourceName,
                    version.engineVersion(), version.signatureVersion());
            if (task.getExpectedSize() != null
                    && !task.getExpectedSize().equals(outcome.actualSize())) {
                return failedWithEvidence(outcome, "本地文件的实际大小与预期不一致");
            }
            return outcome;
        } catch (IOException exception) {
            return ScanOutcome.failed("无法读取本地文件：" + safeMessage(exception));
        }
    }

    private ScanOutcome scanRemoteUrl(ScanTask task) {
        ClamdVersion version = resolveClamdVersion();
        try {
            PublicHttpFileClient.DownloadResult<ScanOutcome> download = fileClient.read(
                    task.getDownloadUrl(),
                    properties.getClamav().getMaxFileSizeBytes(),
                    (input, sourceName) -> clamdClient.scan(
                            input,
                            task.getFileName() == null ? sourceName : task.getFileName(),
                            version.engineVersion(),
                            version.signatureVersion()));
            ScanOutcome outcome = download.value();
            if (!etagMatches(task.getExpectedEtag(), download.etag())) {
                return failedWithEvidence(outcome, "下载对象的 ETag 与预期不一致");
            }
            if (task.getExpectedSize() != null
                    && !task.getExpectedSize().equals(outcome.actualSize())) {
                return failedWithEvidence(outcome, "下载对象的实际大小与预期不一致");
            }
            return outcome;
        } catch (Exception exception) {
            return ScanOutcome.failed("无法下载文件或调用扫描器：" + safeMessage(exception));
        }
    }

    private ClamdVersion resolveClamdVersion() {
        try {
            return clamdClient.version();
        } catch (IOException exception) {
            log.warn("查询 clamd 版本失败，将以空版本提交扫描结果: {}", safeMessage(exception));
            return new ClamdVersion(null, null);
        }
    }

    private ScanOutcome failedWithEvidence(ScanOutcome outcome, String message) {
        return new ScanOutcome(
                ScanStatus.FAILED,
                outcome.actualSize(),
                outcome.sha256(),
                outcome.detectedMimeType(),
                outcome.threatName(),
                outcome.engineVersion(),
                outcome.signatureVersion(),
                message);
    }

    private boolean etagMatches(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return actual != null && normalizeEtag(expected).equals(normalizeEtag(actual));
    }

    private String normalizeEtag(String etag) {
        String normalized = etag.trim();
        if (normalized.regionMatches(true, 0, "W/", 0, 2)) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
