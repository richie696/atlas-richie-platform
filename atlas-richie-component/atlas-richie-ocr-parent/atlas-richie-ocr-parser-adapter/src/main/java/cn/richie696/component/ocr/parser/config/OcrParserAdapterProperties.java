package cn.richie696.component.ocr.parser.config;

import cn.richie696.component.ocr.parser.OcrFailureMode;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * document-parser 图片事件的 OCR 流式增强配置。
 */
@Data
@Accessors(chain = true)
@ConfigurationProperties(prefix = "platform.component.ocr.parser-adapter")
public class OcrParserAdapterProperties {

    /**
     * 默认关闭；只有业务工程显式开启才会产生 OCR 调用成本。
     */
    private boolean enabled;
    private long maxImageBytes = 10L * 1024 * 1024;
    private List<String> supportedMimeTypes = List.of("image/png", "image/jpeg", "image/tiff", "image/bmp", "image/webp");
    private float minConfidence = 0.6f;
    private boolean emitOriginalImage = true;
    private OcrFailureMode failureMode = OcrFailureMode.SKIP_IMAGE;
    private boolean tableRecognition;
    private boolean detectOrientation = true;
}
