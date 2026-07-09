/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.parser.config;

import com.richie.component.parser.DocumentReader;
import com.richie.component.parser.internal.FesodDocumentParser;
import com.richie.component.parser.internal.ParserRouter;
import com.richie.component.parser.internal.TikaDocumentParser;
import com.richie.component.parser.internal.UrlFetcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文档解析组件 Spring Boot 自动配置。
 * <p>
 * 注册所有内部 parser + Router + UrlFetcher + DocumentReader Bean。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-07-08
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "platform.component.parser", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ParserProperties.class)
public class ParserAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TikaDocumentParser tikaDocumentParser(ParserProperties properties) {
        return new TikaDocumentParser(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public FesodDocumentParser fesodDocumentParser() {
        return new FesodDocumentParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public UrlFetcher urlFetcher() {
        return new UrlFetcher();
    }

    @Bean
    @ConditionalOnMissingBean
    public ParserRouter parserRouter(TikaDocumentParser tika, FesodDocumentParser fesod) {
        return new ParserRouter(tika, fesod);
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentReader documentReader(ParserProperties properties,
                                         ParserRouter router,
                                         UrlFetcher urlFetcher) {
        return new DocumentReader(properties, router, urlFetcher);
    }
}
