package cn.richie696.component.vector.integration;

import cn.richie696.component.vector.config.MilvusVectorAutoConfiguration;
import cn.richie696.component.vector.config.MilvusVectorProviderFactory;
import cn.richie696.component.vector.config.MongoDbAtlasVectorAutoConfiguration;
import cn.richie696.component.vector.config.MongoDbAtlasVectorProviderFactory;
import cn.richie696.component.vector.config.Neo4jVectorAutoConfiguration;
import cn.richie696.component.vector.config.Neo4jVectorProviderFactory;
import cn.richie696.component.vector.config.PostgresqlVectorAutoConfiguration;
import cn.richie696.component.vector.config.PostgresqlVectorProviderFactory;
import cn.richie696.component.vector.config.QdrantVectorAutoConfiguration;
import cn.richie696.component.vector.config.QdrantVectorProviderFactory;
import cn.richie696.component.vector.config.RedisVectorAutoConfiguration;
import cn.richie696.component.vector.config.RedisVectorProviderFactory;
import cn.richie696.component.vector.config.VikingDbVectorAutoConfiguration;
import cn.richie696.component.vector.config.VikingDbVectorProviderFactory;
import cn.richie696.component.vector.config.WeaviateVectorAutoConfiguration;
import cn.richie696.component.vector.config.WeaviateVectorProviderFactory;
import cn.richie696.component.vector.topology.VectorProviderFactory;
import com.mongodb.client.MongoClient;
import io.milvus.v2.client.MilvusClientV2;
import io.qdrant.client.QdrantClient;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import redis.clients.jedis.RedisClient;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class VectorProviderClasspathCoexistenceTest {

    @Test
    void allProviderJarsRegisterFactoriesWithoutOpeningUnconfiguredResources() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MilvusVectorAutoConfiguration.class,
                        PostgresqlVectorAutoConfiguration.class,
                        WeaviateVectorAutoConfiguration.class,
                        QdrantVectorAutoConfiguration.class,
                        RedisVectorAutoConfiguration.class,
                        MongoDbAtlasVectorAutoConfiguration.class,
                        Neo4jVectorAutoConfiguration.class,
                        VikingDbVectorAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(VectorProviderFactory.class)).hasSize(8);
                    assertThat(context).hasSingleBean(MilvusVectorProviderFactory.class);
                    assertThat(context).hasSingleBean(PostgresqlVectorProviderFactory.class);
                    assertThat(context).hasSingleBean(WeaviateVectorProviderFactory.class);
                    assertThat(context).hasSingleBean(QdrantVectorProviderFactory.class);
                    assertThat(context).hasSingleBean(RedisVectorProviderFactory.class);
                    assertThat(context).hasSingleBean(MongoDbAtlasVectorProviderFactory.class);
                    assertThat(context).hasSingleBean(Neo4jVectorProviderFactory.class);
                    assertThat(context).hasSingleBean(VikingDbVectorProviderFactory.class);

                    assertThat(context).doesNotHaveBean(VectorStore.class);
                    assertThat(context).doesNotHaveBean(DataSource.class);
                    assertThat(context).doesNotHaveBean(MilvusClientV2.class);
                    assertThat(context).doesNotHaveBean(QdrantClient.class);
                    assertThat(context).doesNotHaveBean(RedisClient.class);
                    assertThat(context).doesNotHaveBean(MongoClient.class);
                    assertThat(context).doesNotHaveBean(Driver.class);
                    assertThat(context).doesNotHaveBean(
                            com.volcengine.vikingdb.runtime.vector.service.VectorService.class);
                });
    }
}
