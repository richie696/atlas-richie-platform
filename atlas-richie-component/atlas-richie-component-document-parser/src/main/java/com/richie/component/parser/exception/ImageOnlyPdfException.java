/*
 * Copyright (c) 2026 Richie (https://www.richie696.cn)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.richie.component.parser.exception;

/**
 * 全图片 PDF 扫描件异常。
 * <p>
 * 当 PDF 文档提取出的文字极少且嵌入大量图片时(扫描件特征)抛出。
 * 当前组件不支持 OCR / 多模态识别,业务方可 catch 本异常后:
 * <ul>
 *   <li>转人工处理</li>
 *   <li>接入 OCR 管道(Tesseract / 阿里云 OCR 等)</li>
 *   <li>调用视觉语言大模型(Qwen-VL / GPT-4V 等)</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
public class ImageOnlyPdfException extends DocumentParseException {

    private final int imageCount;
    private final int textLength;
    private final long fileSize;

    public ImageOnlyPdfException(int imageCount, int textLength, long fileSize, String source) {
        super(buildMessage(imageCount, textLength, fileSize, source));
        this.imageCount = imageCount;
        this.textLength = textLength;
        this.fileSize = fileSize;
    }

    public int getImageCount() {
        return imageCount;
    }

    public int getTextLength() {
        return textLength;
    }

    public long getFileSize() {
        return fileSize;
    }

    private static String buildMessage(int images, int textLen, long size, String source) {
        return String.format(
                "PDF document '%s' appears to be image-only: detected %d embedded images with only %d text characters "
                        + "(file size: %d bytes). OCR or multimodal extraction is not supported by this component. "
                        + "Please pre-process with an OCR pipeline or vision-language model.",
                source, images, textLen, size
        );
    }
}
