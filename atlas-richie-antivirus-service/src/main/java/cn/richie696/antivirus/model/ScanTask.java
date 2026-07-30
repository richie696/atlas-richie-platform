package cn.richie696.antivirus.model;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/** Redis 中暂存的可查询扫描结果，不是长期审计记录。 */
@Data
public class ScanTask implements Serializable {
    private String taskId;
    private String fileId;
    private String tenantId;
    private String downloadUrl;
    /** 与 downloadUrl 互斥；本字段有值时执行器直接读本地文件，跳过 HTTP 下载。 */
    private String localPath;
    private String fileName;
    private Long expectedSize;
    private String expectedEtag;
    private Long actualSize;
    private ScanStatus status;
    private String sha256;
    private String detectedMimeType;
    private String threatName;
    private String engineVersion;
    private String signatureVersion;
    private String errorMessage;
    private OffsetDateTime submittedAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}
