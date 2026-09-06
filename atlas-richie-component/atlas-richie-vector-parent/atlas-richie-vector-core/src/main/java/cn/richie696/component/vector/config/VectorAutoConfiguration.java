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
import cn.richie696.component.vector.diagnostics.DefaultVectorStoreDiagnostics;
import cn.richie696.component.vector.diagnostics.VectorStoreDiagnostics;
import cn.richie696.component.vector.filter.SpringAiVectorFilterCompiler;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.knowledge.ActiveProjectionVersionResolver;
import cn.richie696.component.vector.knowledge.DefaultKnowledgeBaseVectorService;
import cn.richie696.component.vector.knowledge.KnowledgeBaseVectorService;
import cn.richie696.component.vector.observation.VectorStoreObservationHook;
import cn.richie696.component.vector.observation.MicrometerVectorStoreObservationHook;
import cn.richie696.component.vector.service.VectorService;
import cn.richie696.component.vector.topology.VectorProviderFactory;
import cn.richie696.component.vector.topology.VectorCapabilityDiscovery;
import cn.richie696.component.vector.topology.AuthorizedVectorStoreResolver;
import cn.richie696.component.vector.topology.SpringNamedEmbeddingModelResolver;
import cn.richie696.component.vector.topology.VectorStoreAccessPolicy;
import cn.richie696.component.vector.topology.VectorServiceRegistry;
import cn.richie696.component.vector.topology.VectorTopologyBootstrap;
import cn.richie696.component.vector.topology.VectorTopologyDefinitions;
import cn.richie696.component.vector.topology.VectorTopologyRuntime;
import cn.richie696.component.vector.topology.DefaultVectorServiceRegistry;
import cn.richie696.component.vector.topology.VectorConnectionId;
import cn.richie696.component.vector.topology.VectorEmbeddingModelBinding;
import cn.richie696.component.vector.topology.VectorIndexDefinition;
import cn.richie696.component.vector.topology.VectorStoreCapabilities;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreHandle;
import cn.richie696.component.vector.topology.VectorStoreId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 向量组件核心自动装配。
 *
 * <p>只装配核心协作者；各 provider 由自己的 {@code @AutoConfiguration} 显式导入。
 * 禁止扫描整个向量包，避免多个 provider 或历史组件被意外注册。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(VectorProperties.class)
@Conditional(VectorConfigurationPresentCondition.class)
@Import(VectorMultiProviderGuard.class)
@Slf4j
public class VectorAutoConfiguration {

    @Bean
    @ConditionalOnClass({MeterRegistry.class, ObservationRegistry.class})
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(VectorStoreObservationHook.class)
    public VectorStoreObservationHook micrometerVectorStoreObservationHook(
            MeterRegistry meterRegistry,
            ObjectProvider<ObservationRegistry> observationRegistry) {
        return new MicrometerVectorStoreObservationHook(
                meterRegistry, observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP));
    }

    @Bean
    @ConditionalOnMissingBean(VectorStoreTopologyValidator.class)
    public VectorStoreTopologyValidator vectorStoreTopologyValidator(
            VectorProperties properties,
            ConfigurableEnvironment environment,
            ObjectProvider<VectorProviderFactory> providerFactories) {
        VectorStoreTopologyValidator validator = new VectorStoreTopologyValidator(
                properties, environment, providerFactories.orderedStream().toList());
        return validator;
    }

    @Bean
    @ConditionalOnMissingBean(VectorTopologyDefinitions.class)
    public VectorTopologyDefinitions vectorTopologyDefinitions(VectorStoreTopologyValidator validator) {
        return validator.validate();
    }

    @Bean(destroyMethod = "close")
    @Conditional(NamedVectorTopologyCondition.class)
    @ConditionalOnMissingBean(VectorTopologyRuntime.class)
    public VectorTopologyRuntime vectorTopologyRuntime(
            VectorTopologyDefinitions definitions,
            ObjectProvider<VectorProviderFactory> providerFactories,
            ListableBeanFactory beanFactory,
            ObjectProvider<VectorStoreObservationHook> observationHook) {
        return VectorTopologyBootstrap.start(
                definitions.connections(),
                definitions.stores(),
                providerFactories.orderedStream().toList(),
                new SpringNamedEmbeddingModelResolver(beanFactory),
                observationHook.getIfAvailable());
    }

    @Bean
    @Conditional(NamedVectorTopologyCondition.class)
    @ConditionalOnMissingBean(VectorServiceRegistry.class)
    public VectorServiceRegistry vectorServiceRegistry(VectorTopologyRuntime runtime) {
        return runtime.serviceRegistry();
    }

    /**
     * Preserves the legacy {@link VectorService} injection point for a single Named Store.
     * Multi-store applications must route explicitly through {@link VectorServiceRegistry}.
     */
    @Bean
    @Conditional(SingleNamedVectorStoreCondition.class)
    @ConditionalOnMissingBean(VectorService.class)
    public VectorService singleNamedVectorService(VectorServiceRegistry registry) {
        if (registry.describeStores().size() != 1) {
            throw new IllegalStateException("single Named VectorService requires exactly one initialized Store");
        }
        return registry.require(registry.describeStores().getFirst().id()).service();
    }

    /**
     * Projects that keep the old single-provider configuration also receive a read-only
     * default Store view. No provider resource is duplicated: the Handle wraps the existing
     * legacy service bean.
     */
    @Bean
    @Conditional(LegacyVectorTopologyCondition.class)
    @ConditionalOnBean(value = VectorService.class, name = "aiEmbeddingModel")
    @ConditionalOnMissingBean(VectorServiceRegistry.class)
    public VectorServiceRegistry legacyVectorServiceRegistry(
            VectorService service,
            @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel,
            VectorProperties properties) {
        log.warn("Legacy single-provider vector configuration is deprecated; "
                + "it is exposed as Store 'default'. Migrate to named connections/stores before 2.0.0.");
        VectorStoreDefinition definition = new VectorStoreDefinition(
                VectorStoreId.of("default"),
                VectorConnectionId.of("default"),
                "aiEmbeddingModel",
                properties.getDefaultIndex(),
                true,
                Set.of(),
                legacyIndexes(properties));
        VectorStoreHandle handle = VectorStoreHandle
                .builder(definition, properties.getProvider(), service)
                .embeddingModel(VectorEmbeddingModelBinding.of("aiEmbeddingModel", embeddingModel))
                .storeCapabilities(VectorStoreCapabilities.none())
                .build();
        return new DefaultVectorServiceRegistry(java.util.List.of(handle));
    }

    /**
     * Exposes an optional server-side Store authorization boundary without binding
     * this infrastructure component to a business identity or transport protocol.
     */
    @Bean
    @ConditionalOnBean(VectorServiceRegistry.class)
    @ConditionalOnMissingBean(VectorStoreAccessPolicy.class)
    public VectorStoreAccessPolicy vectorStoreAccessPolicy() {
        return VectorStoreAccessPolicy.internalOnly();
    }

    @Bean
    @ConditionalOnBean(VectorServiceRegistry.class)
    @ConditionalOnMissingBean(AuthorizedVectorStoreResolver.class)
    public AuthorizedVectorStoreResolver authorizedVectorStoreResolver(
            VectorServiceRegistry registry,
            VectorStoreAccessPolicy policy) {
        return new AuthorizedVectorStoreResolver(registry, policy);
    }

    @Bean
    @ConditionalOnBean(VectorServiceRegistry.class)
    @ConditionalOnMissingBean(VectorCapabilityDiscovery.class)
    public VectorCapabilityDiscovery vectorCapabilityDiscovery(
            VectorServiceRegistry registry,
            ObjectProvider<VectorProviderFactory> providerFactories) {
        return new VectorCapabilityDiscovery(registry, providerFactories.orderedStream().toList());
    }

    private static Map<String, VectorIndexDefinition> legacyIndexes(VectorProperties properties) {
        if (properties.getIndexes() == null || properties.getIndexes().isEmpty()) {
            return Map.of();
        }
        Map<String, VectorIndexDefinition> indexes = new LinkedHashMap<>();
        properties.getIndexes().forEach((indexId, index) -> indexes.put(indexId, new VectorIndexDefinition(
                indexId,
                index.getName() == null || index.getName().isBlank() ? indexId : index.getName(),
                index.getDimension(),
                index.getMetric(),
                index.getIndexType(),
                index.getReplicas(),
                index.getShards(),
                index.getAdditionalFields(),
                index.getIndexParams())));
        return Map.copyOf(indexes);
    }

    @Bean
    @ConditionalOnBean(VectorServiceRegistry.class)
    @ConditionalOnMissingBean(VectorStoreDiagnostics.class)
    public VectorStoreDiagnostics vectorStoreDiagnostics(
            VectorServiceRegistry registry,
            VectorTopologyDefinitions definitions,
            ObjectProvider<VectorTopologyRuntime> runtime) {
        VectorTopologyRuntime initializedRuntime = runtime.getIfAvailable();
        return new DefaultVectorStoreDiagnostics(
                registry,
                initializedRuntime == null ? java.util.List.of() : initializedRuntime.degradedStores(),
                definitions);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "platform.component.vector",
            name = "spring-ai-store-enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnBean(EmbeddingModel.class)
    @ConditionalOnMissingBean(ModalityAwareEmbeddingService.class)
    public ModalityAwareEmbeddingService modalityAwareEmbeddingService(
            EmbeddingModel textModel,
            @Qualifier("imageEmbeddingModel") ObjectProvider<EmbeddingModel> imageModel) {
        return new ModalityAwareEmbeddingService(textModel, imageModel.getIfAvailable());
    }

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
