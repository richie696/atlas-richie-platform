package cn.richie696.component.vector.config;

import cn.richie696.component.ai.service.RerankService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import cn.richie696.component.vector.service.SparseVectorizerRegistry;

@AutoConfiguration
@AutoConfigureBefore(VectorAutoConfiguration.class)
public class DashVectorAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(DashVectorProviderFactory.class)
    DashVectorProviderFactory dashVectorProviderFactory(ObjectProvider<RerankService> rerankService,ObjectProvider<SparseVectorizerRegistry> sparseVectorizers) {
        return new DashVectorProviderFactory(rerankService.getIfAvailable(),sparseVectorizers.getIfAvailable(() -> new SparseVectorizerRegistry(java.util.Map.of())));
    }
}
