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
package cn.richie696.component.ocr.parser.config;

import cn.richie696.component.ocr.parser.DefaultOcrDocumentEnrichmentAdapter;
import cn.richie696.component.ocr.parser.OcrDocumentEnrichmentAdapter;
import cn.richie696.component.ocr.provider.OcrProvider;
import cn.richie696.component.parser.model.ReadEvent;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * OCR 对 document-parser 输出流的可选自动装配。
 */
@AutoConfiguration
@ConditionalOnClass({OcrProvider.class, ReadEvent.class})
@ConditionalOnSingleCandidate(OcrProvider.class)
@ConditionalOnProperty(prefix = "platform.component.ocr.parser-adapter", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OcrParserAdapterProperties.class)
public class OcrParserAdapterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OcrDocumentEnrichmentAdapter.class)
    public OcrDocumentEnrichmentAdapter ocrDocumentEnrichmentAdapter(OcrProvider provider,
                                                                     OcrParserAdapterProperties properties) {
        return new DefaultOcrDocumentEnrichmentAdapter(provider, properties);
    }
}
