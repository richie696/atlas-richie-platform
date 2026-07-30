package cn.richie696.antivirus.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 通过本地文件路径直接提交扫描任务，跳过 HTTP 下载，专供内部测试。 */
@Data
public class SubmitLocalScanRequest {
    @NotBlank
    private String localPath;
    private String fileName;
    private Long expectedSize;
}