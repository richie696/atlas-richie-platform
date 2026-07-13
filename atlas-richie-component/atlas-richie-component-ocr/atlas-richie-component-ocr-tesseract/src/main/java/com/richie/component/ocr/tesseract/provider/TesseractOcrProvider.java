/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ocr.tesseract.provider;

import com.richie.component.ocr.model.Languages;
import com.richie.component.ocr.model.OcrImage;
import com.richie.component.ocr.model.OcrLine;
import com.richie.component.ocr.model.OcrOptions;
import com.richie.component.ocr.model.OcrResult;
import com.richie.component.ocr.model.Point;
import com.richie.component.ocr.exception.OcrException;
import com.richie.component.ocr.provider.AbstractOcrProvider;
import com.richie.component.ocr.tesseract.config.TesseractOcrProperties;
import com.richie.component.ocr.tesseract.protocol.TesseractRequest;
import com.richie.component.ocr.tesseract.protocol.TesseractResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tesseract OCR Provider 实现 — 通过 Tesseract CLI 子进程执行。
 *
 * <p><b>协议</b>: {@code tesseract <input> stdout -l <lang> tsv}
 * <ul>
 *   <li>输入: 临时 PNG 文件（Tesseract 不支持 stdin + TSV）</li>
 *   <li>输出: TSV, 11 列, level=5 是 word 级别</li>
 *   <li>tessdata 路径: 通过 {@code TESSDATA_PREFIX} 环境变量传给子进程</li>
 * </ul>
 *
 * <p><b>支持</b>: {@link OcrImage.Bytes} + {@link OcrImage.Stream}
 * <br><b>拒绝</b>: {@link OcrImage.Url}（CLI 模式无法直接 HTTP 下 URL 内容）
 *
 * <p><b>错误映射</b>:
 * <ul>
 *   <li>exit != 0 → {@link OcrException.Unrecognized}</li>
 *   <li>IOException（CLI 不存在 / IO 失败）→ {@link OcrException.SidecarUnavailable}</li>
 *   <li>超时 → {@link OcrException.Unrecognized}（CLI 超时通常 CLI 还在跑; 等同业务失败）</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
public class TesseractOcrProvider extends AbstractOcrProvider<TesseractRequest, TesseractResponse> {

    private static final String DEFAULT_TESSDATA_PATH = "/usr/share/tesseract-ocr/4.00/tessdata";
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;
    private static final String TESSERACT_COMMAND = "tesseract";

    private final TesseractOcrProperties props;

    /**
     * 通过 typed {@link TesseractOcrProperties} 构造 Provider，所有 vendor 配置
     * （{@code tessdata-path} / {@code timeout-ms}）都通过 {@code liveXxx()} 实时读取，
     * 支持业务侧通过 Spring Cloud {@code @RefreshScope} / Nacos Config Listener 实现热更新。
     *
     * @param props Spring 标准 {@code @ConfigurationProperties} 绑定得到 typed POJO
     * @throws OcrException.ConfigMissing 当 {@code tessdataPath} 为 {@code null}/空白时抛出
     */
    public TesseractOcrProvider(TesseractOcrProperties props) {
        super();
        this.props = props;
        if (liveTessdataPath().isBlank()) {
            throw new OcrException.ConfigMissing("tesseract", "tesseract.tessdataPath");
        }
        log().info("Tesseract OCR provider[{}] constructed: tessdata={}, timeoutMs={}",
                "tesseract", liveTessdataPath(), liveTimeoutMs());
    }

    private String liveTessdataPath() {
        String path = props.getTessdataPath();
        return (path == null || path.isBlank()) ? DEFAULT_TESSDATA_PATH : path;
    }

    private long liveTimeoutMs() {
        long t = props.getTimeoutMs();
        return t > 0 ? t : DEFAULT_TIMEOUT_MS;
    }

    // --- AbstractOcrProvider 模板实现 ---

    /**
     * 将统一的 {@link OcrImage} 物化为字节数组，并拼装 Tesseract 子进程所需的 {@link TesseractRequest}。
     *
     * <p>仅支持 {@link OcrImage.Bytes} 与 {@link OcrImage.Stream}；{@link OcrImage.Url}
     * 因 Tesseract CLI 无法直接拉取远程内容而被拒绝。
     *
     * @param image 待识别图片，支持 {@code Bytes} 与 {@code Stream} 两种类型
     * @param options 调用选项，用于提取语言集合以映射到 Tesseract 语言码
     * @return 包含图片字节、语言、tessdata 路径与超时的 Tesseract 请求对象
     * @throws OcrException.Unrecognized 当传入的 {@link OcrImage} 类型为 {@code Url} 时抛出
     * @throws OcrException.ProviderUnavailable 当读取 {@link OcrImage.Stream} 输入流发生 {@link IOException} 时抛出
     */
    @Override
    protected TesseractRequest toProviderRequest(OcrImage image, OcrOptions options) {
        // 提取 byte[]: Bytes 拿 data; Stream 物化
        byte[] data = materializeBytes(image);
        String lang = mapLanguage(options.languages());
        return new TesseractRequest(data, lang, liveTessdataPath(), liveTimeoutMs());
    }

    /**
     * 调用 Tesseract CLI 子进程执行识别，命令格式为 {@code tesseract <input> stdout -l <lang> tsv}，
     * 并通过 {@code TESSDATA_PREFIX} 环境变量指向训练数据目录。
     *
     * <p>执行步骤：写入临时 PNG 文件 → 启动 Tesseract CLI → 同步等待（带超时）→ 解析 TSV 输出 →
     * 聚合 word 为 line（计算最小包围盒与平均置信度）。
     *
     * @param request 已构造好的 Tesseract 请求（图片字节、语言、tessdata 路径与超时）
     * @return 包含已解析的行列表、平均置信度与耗时的 Tesseract 响应
     * @throws OcrException.Unrecognized 当子进程返回非零退出码、超时或被中断时抛出
     * @throws OcrException.SidecarUnavailable 当 Tesseract CLI 不存在或临时文件 IO 失败时抛出
     * @throws OcrException.ProviderUnavailable 当发生其他未预期异常时抛出
     */
    @Override
    protected TesseractResponse callProvider(TesseractRequest request) {
        Path tmpFile = null;
        Process process = null;
        long start = System.currentTimeMillis();
        try {
            // 1. 写临时 PNG (Tesseract + TSV 不支持 stdin)
            tmpFile = Files.createTempFile("ocr-tesseract-", ".png");
            tmpFile.toFile().deleteOnExit();
            Files.write(tmpFile, request.imageData());

            // 2. ProcessBuilder: tesseract <input> stdout -l <lang> tsv
            ProcessBuilder pb = new ProcessBuilder(
                    TESSERACT_COMMAND,
                    tmpFile.toAbsolutePath().toString(),
                    "stdout",
                    "-l", request.language(),
                    "tsv");
            // tessdata-prefix 通过环境变量
            pb.environment().put("TESSDATA_PREFIX", request.tessdataPath() + "/");
            pb.redirectErrorStream(true);

            process = pb.start();

            // 3. 同步等待（带超时）
            boolean finished = process.waitFor(request.timeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - start;
            if (!finished) {
                process.destroyForcibly();
                throw new OcrException.Unrecognized("tesseract",
                        "tesseract timeout after " + elapsed + "ms (budget=" + request.timeoutMs() + "ms)");
            }

            // 4. 读 stdout (ProcessBuilder redirectErrorStream=true, stderr 也走 stdout)
            String output;
            try (InputStream is = process.getInputStream()) {
                output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            int exit = process.exitValue();
            if (exit != 0) {
                throw new OcrException.Unrecognized("tesseract",
                        "tesseract exit " + exit + ": " + safeBody(output));
            }

            // 5. 解析 TSV
            List<OcrLine> lines = parseTsv(output);
            float avgConf = lines.isEmpty() ? 0f : (float) lines.stream()
                    .mapToDouble(OcrLine::confidence).average().orElse(0d);
            return new TesseractResponse(lines, avgConf, elapsed);

        } catch (OcrException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new OcrException.Unrecognized("tesseract",
                    "tesseract interrupted: " + e.getMessage());
        } catch (IOException e) {
            // CLI 不存在 / 临时文件 IO 失败 → 视为 sidecar 不可用
            throw new OcrException.SidecarUnavailable("tesseract",
                    TESSERACT_COMMAND + " (CLI)", e);
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            throw new OcrException.ProviderUnavailable("tesseract", null, e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (tmpFile != null) {
                try { Files.deleteIfExists(tmpFile); } catch (IOException ignored) { }
            }
        }
    }

    /**
     * 将 Tesseract 子进程产出的 {@link TesseractResponse} 转换为统一的 {@link OcrResult}。
     *
     * <p>把每行 {@link OcrLine} 的文本以 {@code \n} 拼接为完整文本；由于 Tesseract TSV 不含 block 概念，
     * block 列表留空；元数据中记录 provider 名称、vendor 与行数。
     *
     * @param response Tesseract 解析后的行列表、平均置信度与耗时
     * @return 统一的 OCR 识别结果（全文本、行级平均置信度、Provider 标识与耗时）
     */
    @Override
    protected OcrResult fromProviderResponse(TesseractResponse response) {
        // 拼装 text: 各 line 用 \n 分隔
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < response.lines().size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(response.lines().get(i).text());
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("provider", "tesseract");
        metadata.put("vendor", "tesseract");
        metadata.put("lineCount", response.lines().size());

        return new OcrResult(
                sb.toString(),
                List.of(),          // Tesseract TSV 不带 block 概念, 跳过
                response.avgConfidence(),
                metadata,
                response.latencyMs());
    }

    // --- 工具 ---

    /**
     * Tesseract 不支持 stdin + TSV, 必须先物化为 byte[]
     */
    private static byte[] materializeBytes(OcrImage image) {
        if (image instanceof OcrImage.Bytes bytes) {
            return bytes.data();
        }
        if (image instanceof OcrImage.Stream stream) {
            try (InputStream in = stream.input()) {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new OcrException.ProviderUnavailable("tesseract", null, e);
            }
        }
        // Url: Tesseract CLI 无法拉取, 业务侧应先自己物化 (Bytes)
        throw new OcrException.Unrecognized("tesseract",
                "Tesseract CLI only supports Bytes/Stream; got " + image.getClass().getSimpleName()
                        + " (hint: download the URL first then pass OcrImage.Bytes)");
    }

    /**
     * Languages → Tesseract 语言代码 (tessdata 文件名, 无 .traineddata 后缀)
     */
    private static String mapLanguage(java.util.Set<Languages> languages) {
        if (languages == null || languages.isEmpty()) return "chi_sim+eng";
        return languages.stream()
                .map(TesseractOcrProvider::toTesseractLang)
                .reduce((a, b) -> a + "+" + b)
                .orElse("chi_sim+eng");
    }

    private static String toTesseractLang(Languages lang) {
        return switch (lang) {
            case CHINESE_SIMPLIFIED -> "chi_sim";
            case CHINESE_TRADITIONAL -> "chi_tra";
            case CHINESE_SIMPLIFIED_AND_ENGLISH -> "chi_sim+eng";
            case JAPANESE -> "jpn";
            case KOREAN -> "kor";
            case ARABIC -> "ara";
            case RUSSIAN -> "rus";
            case HINDI -> "hin";
            case THAI -> "tha";
            case VIETNAMESE -> "vie";
            case GREEK -> "ell";
            case TURKISH -> "tur";
            default -> "eng";
        };
    }

    /**
     * TSV 解析: level=5 (word), 按 (block_num, par_num, line_num) 分组成 OcrLine。
     * <p>TSV 列定义: level page_num block_num par_num line_num word_num left top width height conf text
     * <p>line bbox = 各 word 的 left/top/width/height 最小包围盒
     */
    private static List<OcrLine> parseTsv(String tsv) {
        if (tsv == null || tsv.isBlank()) return List.of();

        String[] lines = tsv.split("\\R", -1);
        Map<String, LineBuilder> lineMap = new HashMap<>();
        List<LineBuilder> lineOrder = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {  // skip header
            String row = lines[i];
            if (row.isBlank()) continue;
            String[] cols = row.split("\t", -1);
            if (cols.length < 12) continue;
            try {
                int level = Integer.parseInt(cols[0].trim());
                if (level != 5) continue;  // 仅 word 级别

                String key = cols[2] + ":" + cols[3] + ":" + cols[4];
                int left = Integer.parseInt(cols[6].trim());
                int top = Integer.parseInt(cols[7].trim());
                int width = Integer.parseInt(cols[8].trim());
                int height = Integer.parseInt(cols[9].trim());
                float conf = Float.parseFloat(cols[10].trim()) / 100f;  // 0-100 → 0-1
                String text = cols[11].trim();

                LineBuilder lb = lineMap.computeIfAbsent(key, k -> {
                    LineBuilder nb = new LineBuilder();
                    lineOrder.add(nb);
                    return nb;
                });
                lb.addWord(text, left, top, width, height, conf);
            } catch (NumberFormatException ignore) {
                // 数据列异常, 跳过此行
            }
        }

        List<OcrLine> result = new ArrayList<>(lineOrder.size());
        for (LineBuilder lb : lineOrder) {
            result.add(lb.build());
        }
        return result;
    }

    /**
     * TSV 单 line 聚合器: 累积 word, 计算 bbox 包围盒 + 平均置信度
     */
    private static final class LineBuilder {
        final StringBuilder text = new StringBuilder();
        int minLeft = Integer.MAX_VALUE, minTop = Integer.MAX_VALUE;
        int maxRight = Integer.MIN_VALUE, maxBottom = Integer.MIN_VALUE;
        float confSum = 0f;
        int wordCount = 0;

        void addWord(String word, int left, int top, int width, int height, float conf) {
            if (word.isEmpty()) return;  // skip empty token
            if (!text.isEmpty()) text.append(' ');
            text.append(word);
            minLeft = Math.min(minLeft, left);
            minTop = Math.min(minTop, top);
            maxRight = Math.max(maxRight, left + width);
            maxBottom = Math.max(maxBottom, top + height);
            confSum += conf;
            wordCount++;
        }

        OcrLine build() {
            if (wordCount == 0) return new OcrLine("", List.of(), 0f);
            // bbox: 4 角点 (左上 → 右上 → 右下 → 左下)
            List<Point> box = List.of(
                    new Point(minLeft, minTop),
                    new Point(maxRight, minTop),
                    new Point(maxRight, maxBottom),
                    new Point(minLeft, maxBottom));
            float avgConf = confSum / wordCount;
            return new OcrLine(text.toString(), box, avgConf);
        }
    }
    private static String safeBody(String body) {
        if (body == null) return "<empty>";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }
}