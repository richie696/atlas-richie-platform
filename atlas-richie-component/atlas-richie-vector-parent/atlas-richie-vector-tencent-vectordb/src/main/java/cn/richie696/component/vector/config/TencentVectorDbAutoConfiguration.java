package cn.richie696.component.vector.config;

import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.service.SparseVectorizerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureBefore(VectorAutoConfiguration.class)
public class TencentVectorDbAutoConfiguration {
    @Bean @ConditionalOnMissingBean(TencentVectorDbProviderFactory.class)
    TencentVectorDbProviderFactory tencentVectorDbProviderFactory(ObjectProvider<RerankService> rerankService,
                                                                   ObjectProvider<SparseVectorizerRegistry> sparseVectorizers) {
        return new TencentVectorDbProviderFactory(rerankService.getIfAvailable(),
                sparseVectorizers.getIfAvailable(() -> new SparseVectorizerRegistry(java.util.Map.of())));
    }
}
