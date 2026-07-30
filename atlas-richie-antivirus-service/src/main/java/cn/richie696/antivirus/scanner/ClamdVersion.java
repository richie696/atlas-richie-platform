package cn.richie696.antivirus.scanner;

/**
 * clamd VERSION 命令响应解析结果。
 *
 * <p>响应格式：{@code "ClamAV 1.5.3/28077/Mon Jul 30 22:51:43 2026"}，
 * 分别对应引擎版本、病毒库构建号、构建时间。
 */
public record ClamdVersion(String engineVersion, String signatureVersion) {

    public static ClamdVersion parse(String response) {
        if (response == null || response.isBlank()) {
            return new ClamdVersion(null, null);
        }
        String[] parts = response.split("/", 3);
        String engine = parts.length > 0 && !parts[0].isBlank() ? parts[0].trim() : null;
        String signature = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : null;
        return new ClamdVersion(engine, signature);
    }
}