package cn.richie696.antivirus.api;

import cn.richie696.antivirus.model.ScanStatus;
import cn.richie696.antivirus.model.ScanTask;

import java.time.OffsetDateTime;

/** 对外查询视图，刻意不返回可能包含临时凭证的下载 URL。 */
public record ScanTaskResponse(
        String taskId,
        String fileId,
        ScanStatus status,
        Long actualSize,
        String sha256,
        String detectedMimeType,
        String threatName,
        String engineVersion,
        String signatureVersion,
        String errorMessage,
        String localPath,
        OffsetDateTime submittedAt,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt) {

    public static ScanTaskResponse from(ScanTask task) {
        return new ScanTaskResponse(
                task.getTaskId(), task.getFileId(), task.getStatus(), task.getActualSize(),
                task.getSha256(), task.getDetectedMimeType(), task.getThreatName(),
                task.getEngineVersion(), task.getSignatureVersion(), task.getErrorMessage(),
                task.getLocalPath(),
                task.getSubmittedAt(), task.getStartedAt(), task.getCompletedAt());
    }
}
