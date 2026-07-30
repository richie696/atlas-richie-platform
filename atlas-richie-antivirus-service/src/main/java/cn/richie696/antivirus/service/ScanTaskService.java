package cn.richie696.antivirus.service;

import cn.richie696.antivirus.api.SubmitLocalScanRequest;
import cn.richie696.antivirus.api.SubmitScanRequest;
import cn.richie696.antivirus.config.AntivirusProperties;
import cn.richie696.antivirus.model.ScanStatus;
import cn.richie696.antivirus.model.ScanTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** HTTP 受理与 Redis Stream 投递。受理成功只代表任务已入队，不代表文件安全。 */
@Service
@RequiredArgsConstructor
public class ScanTaskService {
    private final ScanTaskRepository repository;
    private final ScanEventPublisher publisher;
    private final AntivirusProperties properties;

    public ScanTask submit(SubmitScanRequest request, String tenantId) {
        ScanTask task = new ScanTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTenantId(tenantId);
        task.setFileId(request.getFileId());
        task.setDownloadUrl(request.getDownloadUrl());
        task.setFileName(request.getFileName());
        task.setExpectedSize(request.getExpectedSize());
        task.setExpectedEtag(request.getExpectedEtag());
        task.setStatus(ScanStatus.PENDING);
        task.setSubmittedAt(OffsetDateTime.now());

        return persistAndPublish(task);
    }

    public ScanTask submitLocal(SubmitLocalScanRequest request, String tenantId) {
        if (!properties.getLocal().isEnabled()) {
            throw new ScanTaskSubmissionException("本地文件扫描端点未启用", null);
        }
        Path resolved = validateLocalPath(request.getLocalPath());

        ScanTask task = new ScanTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTenantId(tenantId);
        task.setFileId(resolved.getFileName().toString());
        task.setLocalPath(resolved.toString());
        task.setFileName(request.getFileName() != null && !request.getFileName().isBlank()
                ? request.getFileName()
                : resolved.getFileName().toString());
        task.setExpectedSize(request.getExpectedSize());
        task.setStatus(ScanStatus.PENDING);
        task.setSubmittedAt(OffsetDateTime.now());

        return persistAndPublish(task);
    }

    public Optional<ScanTask> get(String taskId, String tenantId) {
        return repository.find(taskId)
                .filter(task -> task.getTenantId().equals(tenantId));
    }

    private ScanTask persistAndPublish(ScanTask task) {
        repository.save(task);
        try {
            publisher.publish(task.getTaskId());
            return task;
        } catch (RuntimeException exception) {
            // 不返回一个永远无法执行的 PENDING 任务；调用方可安全重试提交。
            repository.delete(task.getTaskId());
            throw new ScanTaskSubmissionException("扫描任务暂时无法受理", exception);
        }
    }

    private Path validateLocalPath(String rawPath) {
        Path resolved = Paths.get(rawPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(resolved)) {
            throw new ScanTaskSubmissionException("本地文件不存在或不是普通文件", null);
        }
        List<String> allowed = properties.getLocal().getAllowedPaths();
        if (allowed == null || allowed.isEmpty()) {
            throw new ScanTaskSubmissionException("本地文件扫描未配置任何允许目录", null);
        }
        boolean inWhitelist = allowed.stream()
                .map(s -> Paths.get(s).toAbsolutePath().normalize())
                .anyMatch(root -> resolved.startsWith(root));
        if (!inWhitelist) {
            throw new ScanTaskSubmissionException("本地文件不在允许扫描的目录白名单内", null);
        }
        return resolved;
    }
}
