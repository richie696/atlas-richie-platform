package cn.richie696.component.vector.config;

import cn.richie696.ai.vectorstore.vikingdb.VikingDbVectorStore;
import cn.richie696.component.vector.service.impl.VikingDbVectorServiceImpl;
import com.volcengine.ApiClient;
import com.volcengine.sign.Credentials;
import com.volcengine.vikingdb.VikingdbApi;
import com.volcengine.vikingdb.runtime.core.ClientConfig;
import com.volcengine.vikingdb.runtime.core.auth.AuthWithAkSk;
import com.volcengine.vikingdb.runtime.enums.Scheme;
import com.volcengine.vikingdb.runtime.exception.ApiClientException;
import com.volcengine.vikingdb.runtime.exception.VectorApiException;
import com.volcengine.vikingdb.runtime.vector.service.VectorService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.util.Assert;

/** VikingDB 的平台配置到 Atlas Richie AI VectorStore 的适配装配。 */
@AutoConfiguration
@EnableConfigurationProperties({VectorProperties.class, VikingDbConfig.class})
@Import(VikingDbVectorServiceImpl.class)
public class VikingDbVectorAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "vikingdb")
    public VectorService vikingDbDataPlaneClient(VikingDbConfig config) throws VectorApiException, ApiClientException {
        Assert.hasText(config.getHost(), "platform.component.vector.vikingdb.host 不能为空");
        Assert.hasText(config.getAccessKey(), "platform.component.vector.vikingdb.access-key 不能为空");
        Assert.hasText(config.getSecretKey(), "platform.component.vector.vikingdb.secret-key 不能为空");
        return new VectorService(Scheme.valueOf(config.getScheme()), config.getHost(), config.getRegion(),
                new AuthWithAkSk(config.getAccessKey(), config.getSecretKey()), ClientConfig.builder().build());
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "vikingdb")
    public VikingdbApi vikingDbControlPlaneClient(VikingDbConfig config) {
        Assert.hasText(config.getControlEndpoint(), "platform.component.vector.vikingdb.control-endpoint 不能为空");
        return new VikingdbApi(new ApiClient().setEndpoint(config.getControlEndpoint())
                .setCredentials(Credentials.getCredentials(config.getAccessKey(), config.getSecretKey()))
                .setRegion(config.getRegion()));
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "vikingdb")
    public VectorStore vikingDbVectorStore(VectorService vikingDbDataPlaneClient, VikingdbApi vikingDbControlPlaneClient,
                                           EmbeddingModel embeddingModel, VikingDbConfig config) {
        return new VikingDbVectorStore.Builder(embeddingModel, vikingDbDataPlaneClient)
                .controlPlane(vikingDbControlPlaneClient)
                .collectionName(config.getCollectionName())
                .indexName(config.getIndexName())
                .embeddingDimension(config.getEmbeddingDimension())
                .initializeSchema(config.isInitializeSchema())
                .projectName(config.getProjectName())
                .description(config.getDescription())
                .shardCount(config.getShardCount())
                .metadataFields(config.getMetadataFields())
                .batchingStrategy(new TokenCountBatchingStrategy())
                .build();
    }
}
