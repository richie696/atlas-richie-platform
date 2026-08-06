/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.embeddings.ModalityAwareEmbeddingService;
import cn.richie696.component.vector.filter.SpringAiVectorFilterCompiler;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.knowledge.ActiveProjectionVersionResolver;
import cn.richie696.component.vector.knowledge.DefaultKnowledgeBaseVectorService;
import cn.richie696.component.vector.knowledge.KnowledgeBaseVectorService;
import cn.richie696.component.vector.service.VectorService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 向量组件核心自动装配。
 *
 * <p>只装配核心协作者；各 provider 由自己的 {@code @AutoConfiguration} 显式导入。
 * 禁止扫描整个向量包，避免多个 provider 或历史组件被意外注册。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(VectorProperties.class)
@Import({ModalityAwareEmbeddingService.class, VectorMultiProviderGuard.class})
public class VectorAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name = "spring-ai-filter-dsl-enabled", havingValue = "true")
    @ConditionalOnMissingBean(VectorFilterCompiler.class)
    public VectorFilterCompiler springAiVectorFilterCompiler() {
        return new SpringAiVectorFilterCompiler();
    }

    /**
     * 没有可信的 provider-side filter compiler 时不创建知识库门面，杜绝 ACL 后过滤。
     */
    @Bean
    @ConditionalOnBean({VectorService.class, VectorFilterCompiler.class})
    @ConditionalOnMissingBean(KnowledgeBaseVectorService.class)
    public KnowledgeBaseVectorService knowledgeBaseVectorService(
            VectorService vectorService,
            ObjectProvider<ActiveProjectionVersionResolver> resolver) {
        return new DefaultKnowledgeBaseVectorService(vectorService, resolver.getIfAvailable());
    }
}
