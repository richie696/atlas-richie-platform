package cn.richie696.antivirus.scanner;

import cn.richie696.antivirus.model.ScanStatus;

/** 单次扫描的结果；实现不得把扫描器不可用或异常映射为 CLEAN。 */
public record ScanOutcome(ScanStatus status, Long actualSize, String sha256, String detectedMimeType,
                          String threatName, String engineVersion, String signatureVersion,
                          String errorMessage) {
    public static ScanOutcome failed(String errorMessage) {
        return new ScanOutcome(ScanStatus.FAILED, null, null, null, null, null, null, errorMessage);
    }
}
