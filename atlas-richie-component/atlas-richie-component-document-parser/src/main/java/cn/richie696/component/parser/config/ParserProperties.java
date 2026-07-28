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
package cn.richie696.component.parser.config;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.time.Duration;

/**
 * 文档解析组件配置属性。
 * <p>
 * 配置前缀: {@code platform.component.parser}
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
@Data
@Accessors(chain = true)
@ConfigurationProperties(prefix = "platform.component.parser")
public class ParserProperties {

    /**
     * 是否启用解析组件(默认 true)
     */
    private boolean enabled = true;

    /** 单次解析允许执行的最长时间。 */
    private Duration parseTimeout = Duration.ofMinutes(2);

    /** 单个公开文本段允许保留的最大字符数。 */
    private int maxSegmentCharacters = 16 * 1024;

    /**
     * URL 拉取配置(三道防线之协议层)
     */
    @NestedConfigurationProperty
    private UrlConfig url = new UrlConfig();

    /**
     * PDF 解析配置
     */
    @NestedConfigurationProperty
    private PdfConfig pdf = new PdfConfig();

    /** 单张嵌入图片允许读入内存的最大字节数，超过时跳过该图片事件。 */
    private long maxEmbeddedImageBytes = 20L * 1024 * 1024;

    /**
     * URL 拉取配置。
     */
    @Data
    @Accessors(chain = true)
    public static class UrlConfig {
        /**
         * 是否允许明文 HTTP(默认 false,SSRF 防御)
         */
        private boolean allowHttp = false;

        /**
         * 是否允许内网 IP(默认 false,SSRF 防御)
         */
        private boolean allowPrivateIp = false;

        /**
         * 是否跟随重定向(默认 true)
         */
        private boolean followRedirects = true;

        /**
         * 最大字节数(默认 200MB)
         */
        private long maxBytes = 200L * 1024 * 1024;

        /**
         * 连接超时(默认 5s)
         */
        private Duration connectTimeout = Duration.ofSeconds(5);

        /**
         * 读取超时(默认 60s)
         */
        private Duration readTimeout = Duration.ofSeconds(60);
    }

    /**
     * PDF 解析配置。
     */
    @Data
    @Accessors(chain = true)
    public static class PdfConfig {
        /**
         * 全图片 PDF 检测配置。
         */
        @NestedConfigurationProperty
        private ImageOnlyDetection imageOnlyDetection = new ImageOnlyDetection();

        /**
         * 全图片 PDF 检测启发式配置。
         * <p>
         * 判定逻辑: {@code textChars < minTextChars AND imageCount >= minImageCount}
         * 或 {@code textChars < 50} 快速通道。
         */
        @Data
        @Accessors(chain = true)
        public static class ImageOnlyDetection {
            /**
             * 是否启用检测(默认 false)
             * <p>
             * 默认禁用, 业务方收到 emit 的 ImageStreaming event 自己处理 (OCR / VLM)。
             * 严格模式: 业务方显式 enabled = true, 触发 ImageOnlyPdfException 强制 fail-fast。
             */
            private boolean enabled = false;

            /**
             * 触发异常的最小文本字符数阈值(默认 200)
             */
            private int minTextChars = 200;

            /**
             * 触发异常的最小图片数量阈值(默认 5)
             */
            private int minImageCount = 5;
        }
    }

}
