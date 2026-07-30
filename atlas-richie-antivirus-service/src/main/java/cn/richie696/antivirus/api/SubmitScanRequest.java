package cn.richie696.antivirus.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 提交一个可公开读取或临时授权读取的文件地址。 */
@Data
public class SubmitScanRequest {
    @NotBlank
    private String fileId;
    @NotBlank
    private String downloadUrl;
    private String fileName;
    private Long expectedSize;
    private String expectedEtag;
}
