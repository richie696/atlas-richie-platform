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
package com.richie.component.ocr.paddle.provider;

import com.richie.component.ocr.model.Languages;
import com.richie.component.ocr.paddle.config.PaddleOcrProperties;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.ocr.paddle.protocol.PaddleOcrEnvelope;
import com.richie.component.ocr.model.OcrBlock;
import com.richie.component.ocr.model.OcrImage;
import com.richie.component.ocr.model.OcrLine;
import com.richie.component.ocr.model.OcrOptions;
import com.richie.component.ocr.model.OcrResult;
import com.richie.component.ocr.model.Point;
import com.richie.component.ocr.exception.OcrException;
import com.richie.component.ocr.provider.AbstractOcrProvider;
import com.richie.component.ocr.paddle.protocol.PaddleRequest;
import com.richie.component.ocr.paddle.protocol.PaddleResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * PaddleOCR Provider 实现
 *
 * <p>通过子进程调用 {@code paddleocr} Python 包识别本地图片。
 * Python 包装脚本以 classpath 资源形式部署在 {@code scripts/paddle_ocr.py}，
 * 启动时复制到临时文件复用。
 *
 * <p><b>运行时依赖</b>:
 * <ul>
 *   <li>Python 3.x（默认 {@code python3}）</li>
 *   <li>{@code paddleocr} 包（通过 {@code pip install paddleocr paddlepaddle} 安装）</li>
 *   <li>可选 {@code model-dir}（PaddleOCR 默认下载模型到 {@code ~/.paddleocr/}）</li>
 * </ul>
 *
 * <p><b>支持的图片类型</b>: {@link OcrImage.Bytes}、{@link OcrImage.Stream}。
 * {@link OcrImage.Url} 不支持（无网络拉取能力）。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
public class PaddleOcrProvider extends AbstractOcrProvider<PaddleRequest, PaddleResponse> {

    private static final String PYTHON_SCRIPT_RESOURCE = "/scripts/paddle_ocr.py";
    private static final long DEFAULT_TIMEOUT_MS = 60_000L;
    private static final String DEFAULT_PYTHON_PATH = "python3";

    private final PaddleOcrProperties props;
    private final Path scriptPath;

    /**
     * 构造 PaddleOCR Provider：注入 typed 配置 + 同时拷贝 classpath 中的 Python 包装脚本
     * 至临时文件供子进程调用。
     *
     * <p>所有 vendor 配置属性（pythonPath / modelDir / timeoutMs）都通过 {@code liveXxx()} 实时读取，
     * 支持业务侧通过 Spring Cloud {@code @RefreshScope} / Nacos Config Listener 热更新。
     *
     * @param props PaddleOCR 配置（python-path / model-dir / timeout-ms）
     * @throws OcrException.SidecarUnavailable 当 classpath 中的 Python 包装脚本缺失或拷贝失败时抛出
     */
    public PaddleOcrProvider(PaddleOcrProperties props) {
        super();

        this.props = props;

        try (InputStream in = getClass().getResourceAsStream(PYTHON_SCRIPT_RESOURCE)) {
            if (in == null) {
                throw new OcrException.SidecarUnavailable("paddle",
                        "classpath:" + PYTHON_SCRIPT_RESOURCE,
                        new IOException("Paddle wrapper script not found in classpath"));
            }
            Path sp = Files.createTempFile("paddle_ocr_", ".py");
            Files.copy(in, sp, StandardCopyOption.REPLACE_EXISTING);
            sp.toFile().deleteOnExit();
            this.scriptPath = sp;
        } catch (IOException e) {
            throw new OcrException.SidecarUnavailable("paddle",
                    "classpath:" + PYTHON_SCRIPT_RESOURCE, e);
        }

        log().info("PaddleOCR provider[{}] constructed: python={}, modelDir={}, timeoutMs={}, script={}",
                "paddle", livePythonPath(), liveModelDir(), liveTimeoutMs(), scriptPath);
    }

    private String livePythonPath() {
        String path = props.getPythonPath();
        return (path == null || path.isBlank()) ? DEFAULT_PYTHON_PATH : path;
    }

    private String liveModelDir() {
        return props.getModelDir();
    }

    private long liveTimeoutMs() {
        long t = props.getTimeoutMs();
        return t > 0 ? t : DEFAULT_TIMEOUT_MS;
    }

    /**
     * 将统一的 {@link OcrImage} 转换为 PaddleOCR 子进程所需的 {@link PaddleRequest}。
     *
     * <p>仅支持 {@link OcrImage.Bytes} 与 {@link OcrImage.Stream} 两种本地图片来源；
     * {@link OcrImage.Url} 因不具备网络拉取能力将被拒绝。
     *
     * @param image 待识别图片，支持 {@code Bytes} 与 {@code Stream} 两种 sealed 子类型
     * @param options 调用选项，用于提取语言以映射到 PaddleOCR 的语言码
     * @return 包含图片字节与语言码的 PaddleOCR 请求对象
     * @throws OcrException.Unrecognized 当传入的 {@link OcrImage} 类型为 {@code Url} 时抛出
     * @throws OcrException.ProviderUnavailable 当读取 {@link OcrImage.Stream} 输入流发生 {@link IOException} 时抛出
     */
    @Override
    protected PaddleRequest toProviderRequest(OcrImage image, OcrOptions options) {
        byte[] data;
        switch (image) {
            case OcrImage.Bytes b -> data = b.data();
            case OcrImage.Stream s -> {
                try (InputStream in = s.input()) {
                    data = in.readAllBytes();
                } catch (IOException e) {
                    throw new OcrException.ProviderUnavailable("paddle", null, e);
                }
            }
            case OcrImage.Url _ -> throw new OcrException.Unrecognized("paddle",
                    "PaddleOCR only supports local Bytes/Stream images, not URL");
            default ->
                    throw new IllegalArgumentException("Unsupported OcrImage variant: " + image.getClass().getName());
        }
        return new PaddleRequest(data, mapLanguage(options.languages()));
    }

    /**
     * 调用 Python 子进程执行 PaddleOCR 识别，并将 stdout 反序列化为 typed {@link PaddleOcrEnvelope}。
     *
     * <p>执行步骤：写入临时图片文件 → 启动 {@code python3 <script> --image <tmp> --lang <lang> [--model-dir <dir>]}
     * → 等待子进程退出或超时 → 用 {@link JsonUtils} 反序列化为 {@link PaddleOcrEnvelope}。
     * stderr 通过 {@code redirectErrorStream(true)} 合入 stdout。
     *
     * @param request 已构造好的 PaddleOCR 请求（图片字节与语言码）
     * @return 包含 typed envelope 与耗时（毫秒）的 PaddleOCR 响应
     * @throws OcrException.SidecarUnavailable 当 Python 子进程启动失败、IO 异常或脚本报告错误时抛出
     * @throws OcrException.Unrecognized 当子进程返回非零退出码、或在指定超时内未退出时抛出
     * @throws OcrException.ProviderUnavailable 当等待子进程被中断时抛出，并恢复线程中断状态
     */
    @Override
    protected PaddleResponse callProvider(PaddleRequest request) {
        Path tmpImage = null;
        Process process = null;
        try {
            tmpImage = Files.createTempFile("paddle_ocr_img_", ".png");
            Files.write(tmpImage, request.imageData());
            tmpImage.toFile().deleteOnExit();

            List<String> cmd = new ArrayList<>(6);
            cmd.add(livePythonPath());
            cmd.add(scriptPath.toString());
            cmd.add("--image"); cmd.add(tmpImage.toString());
            cmd.add("--lang"); cmd.add(request.lang());
            if (liveModelDir() != null && !liveModelDir().isBlank()) {
                cmd.add("--model-dir"); cmd.add(liveModelDir());
            }

            // redirectErrorStream(true): stderr 错误信息合到 stdout, 简化错误捕获
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!output.isEmpty()) output.append('\n');
                    output.append(line);
                }
            }

            long start = System.currentTimeMillis();
            boolean exited = process.waitFor(liveTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!exited) {
                process.destroyForcibly();
                throw new OcrException.Unrecognized("paddle",
                        "PaddleOCR subprocess timed out after " + liveTimeoutMs() + "ms");
            }
            long latencyMs = System.currentTimeMillis() - start;

            int exitCode = process.exitValue();
            String stdout = output.toString();
            PaddleOcrEnvelope envelope = parseOrNull(stdout);
            if (exitCode != 0) {
                String err = envelope != null ? envelope.error() : null;
                if (err != null && !err.isBlank()) {
                    throw new OcrException.SidecarUnavailable("paddle", livePythonPath(),
                            new RuntimeException(err));
                }
                throw new OcrException.Unrecognized("paddle",
                        "PaddleOCR exit " + exitCode + ": " + safeBody(stdout));
            }

            if (envelope != null && envelope.error() != null) {
                throw new OcrException.SidecarUnavailable("paddle", livePythonPath(),
                        new RuntimeException(envelope.error()));
            }
            return new PaddleResponse(envelope, latencyMs);

        } catch (IOException e) {
            throw new OcrException.SidecarUnavailable("paddle", livePythonPath(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrException.ProviderUnavailable("paddle", null, e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (tmpImage != null) {
                try { Files.deleteIfExists(tmpImage); } catch (IOException ignored) { }
            }
        }
    }

    /**
     * 将 PaddleOCR 子进程产出的 typed {@link PaddleOcrEnvelope} 转换为统一的 {@link OcrResult}。
     *
     * <p>遍历 envelope 中的 {@link PaddleOcrEnvelope.Item} 列表，逐项构造 {@link OcrBlock}
     * （同时填入含相同文本/置信度/包围盒的单行 {@link OcrLine}）；最终合并全部文本并计算平均置信度。
     *
     * @param response PaddleOCR 子进程返回的 typed envelope 与耗时
     * @return 统一的 OCR 识别结果（全文本、文本块列表、平均置信度、Provider 标识与耗时）
     */
    @Override
    protected OcrResult fromProviderResponse(PaddleResponse response) {
        PaddleOcrEnvelope env = response.envelope();
        List<PaddleOcrEnvelope.Item> items = env != null ? env.items() : null;
        if (items == null || items.isEmpty()) {
            return new OcrResult("", List.of(), 0.0f,
                    Map.of("provider", "paddle"), response.latencyMs());
        }

        List<OcrBlock> blocks = new ArrayList<>(items.size());
        StringBuilder allText = new StringBuilder();
        float totalScore = 0f;
        int count = 0;

        for (PaddleOcrEnvelope.Item item : items) {
            String text = item.text() != null ? item.text() : "";
            float score = item.score() != null ? item.score().floatValue() : 0f;
            List<Point> bbox = parseBox(item.bbox());

            blocks.add(new OcrBlock(text, bbox, score,
                    List.of(new OcrLine(text, bbox, score))));

            if (!allText.isEmpty()) allText.append('\n');
            allText.append(text);
            totalScore += score;
            count++;
        }

        float avgScore = count > 0 ? totalScore / count : 0f;
        return new OcrResult(allText.toString(), blocks, avgScore,
                Map.of("provider", "paddle"), response.latencyMs());
    }

    private static List<Point> parseBox(List<List<Float>> bbox) {
        if (bbox == null) {
            return List.of();
        }
        List<Point> points = new ArrayList<>(bbox.size());
        for (List<Float> p : bbox) {
            if (p != null && p.size() >= 2) {
                points.add(new Point(
                        Math.round(p.get(0)),
                        Math.round(p.get(1))));
            }
        }
        return List.copyOf(points);
    }

    private static String mapLanguage(java.util.Set<Languages> langs) {
        if (langs == null || langs.isEmpty()) return "ch";
        Languages lang = langs.iterator().next();
        return switch (lang) {
            case CHINESE_SIMPLIFIED, CHINESE_SIMPLIFIED_AND_ENGLISH -> "ch";
            case ENGLISH, DIGITS_ONLY, LATIN -> "en";
            case CHINESE_TRADITIONAL -> "chinese_cht";
            case JAPANESE -> "japan";
            case KOREAN -> "korean";
            case ARABIC -> "ar";
            case RUSSIAN -> "ru";
            case HINDI -> "hi";
            case THAI -> "th";
            case VIETNAMESE -> "vi";
            default -> "en";
        };
    }

    private PaddleOcrEnvelope parseOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return JsonUtils.getInstance().deserialize(s, PaddleOcrEnvelope.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String safeBody(String body) {
        if (body == null) return "<empty>";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }
}
