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
package com.richie.component.ocr.config;

import com.richie.component.ocr.model.Languages;
import com.richie.component.ocr.model.OcrOptions;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * OCR 组件核心配置属性。
 *
 * <p>绑定前缀: {@code platform.component.ocr}
 *
 * <p>典型配置 (单 vendor 模式):
 * <pre>
 * platform:
 *   component:
 *     ocr:
 *       enabled: true                   # 主开关, 默认 true; false 时整个组件不装配任何 Bean
 *       vendor: aliyun                  # 当前激活的 vendor —— 决定哪个 vendor 模块的
 *                                       # AutoConfiguration 被 @ConditionalOnProperty 激活
 *       aliyun:                         # aliyun 私有配置 (AliyunOcrProperties)
 *         endpoint: https://ocr-api.cn-shanghai.aliyuncs.com
 *         timeout-ms: 30000
 *         credentials:
 *           app-code: ${ALIYUN_APP_CODE:}
 *         vendor:
 *           model: standard-form
 * </pre>
 *
 * <p>设计原则:
 * <ul>
 *   <li><b>单一 vendor</b> —— 同一部署只允许激活一个 OCR 引擎 (选型跟数据合规/成本/SLA
 *       强相关, 是上线前一次性确定的事)。vendor 字段枚举约束可选值</li>
 *   <li><b>core 不感知 vendor 细节</b> —— 核心 Properties 只关心"激活哪个 vendor", vendor
 *       私有配置 (endpoint, credentials 等) 由各 vendor 模块自己的
 *       {@code *Properties} 类 (前缀 {@code platform.component.ocr.<vendor>}) 绑定</li>
 *   <li><b>包级约定</b> —— 业务侧只需引入 {@code ocr-core} + 对应 vendor 实现包 (如
 *       {@code ocr-aliyun}), Spring Boot 自动装配即可生效</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
@Data
@ConfigurationProperties(prefix = OcrProperties.PREFIX)
public class OcrProperties {

    /**
     * 配置前缀常量 —— vendor 模块 autoconfig 引用此常量以保证前缀一致。
     */
    public static final String PREFIX = "platform.component.ocr";

    /**
     * 主开关 —— {@code false} 时整个 OCR 组件不装配任何 Bean (Registry / Engine 也不创建)。
     * 默认 {@code true}。
     */
    private boolean enabled = true;

    /**
     * 当前激活的 vendor —— 决定哪个 vendor 模块的 {@code AutoConfiguration} 被激活。
     * <p>yaml 配小写 ({@code aliyun} / {@code baidu} / ...), 通过 {@link OcrVendorConverter}
     * 转换为 {@link OcrVendor} 枚举。
     * <p>L2 单 vendor 模式下, {@code vendor} 字段未设置或值不在 {@link OcrVendor} 枚举内时,
     * 所有 vendor autoconfig 因 {@code @ConditionalOnProperty} 不匹配而不会激活;
     * 业务侧 {@code @Autowired AliyunOcrProvider} 等具体 vendor Bean 时由 Spring 抛
     * {@code NoSuchBeanDefinitionException}, 与本组件无关。
     */
    private OcrVendor vendor;

    /**
     * 全局默认语言集合 —— 业务侧 {@link OcrOptions.Builder#languages()}
     * 不调或传 {@link Languages#AUTO} 时使用本字段。
     *
     * <p>yaml 配置示例:
     * <pre>
     * platform:
     *   component:
     *     ocr:
     *       default-languages:
     *         - JAPANESE
     *         - ENGLISH
     * </pre>
     */
    private Set<Languages> defaultLanguages = EnumSet.of(Languages.CHINESE_SIMPLIFIED_AND_ENGLISH);

    /**
     * Spring Boot 绑定入口 (yaml list → {@code Set<Languages>})。
     * 接受字符串或 {@link Languages} 枚举名, 容错处理 yaml 大小写.
     * Lombok {@code @Data} 生成的 {@code setDefaultLanguages(Set<Languages>)} 也保留.
     *
     * <p>容错策略:
     * <ul>
     *   <li>{@code raw} 为 {@code null} 或空 → 回落默认 {@code EnumSet(CHINESE_SIMPLIFIED_AND_ENGLISH)}</li>
     *   <li>任意元素为 {@code null} / 空白 → 跳过</li>
     *   <li>元素大小写自动 trim + 大写, {@code -} 转 {@code _} 后再 {@link Languages#valueOf}</li>
     *   <li>解析失败的元素静默忽略; 全失败时回落默认</li>
     * </ul>
     *
     * @param raw 从 yaml {@code platform.component.ocr.default-languages} 读出的字符串列表;
     *            允许大小写、连字符、空格、不识别项, 本方法统一容错
     */
    public void setDefaultLanguages(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            this.defaultLanguages = EnumSet.of(Languages.CHINESE_SIMPLIFIED_AND_ENGLISH);
            return;
        }
        Set<Languages> resolved = EnumSet.noneOf(Languages.class);
        for (String s : raw) {
            if (s == null || s.isBlank()) continue;
            String key = s.trim().toUpperCase().replace('-', '_');
            try {
                resolved.add(Languages.valueOf(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (resolved.isEmpty()) {
            this.defaultLanguages = EnumSet.of(Languages.CHINESE_SIMPLIFIED_AND_ENGLISH);
        } else {
            this.defaultLanguages = resolved;
        }
    }
}