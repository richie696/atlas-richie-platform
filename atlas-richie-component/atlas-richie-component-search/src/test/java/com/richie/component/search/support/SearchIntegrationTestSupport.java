package com.richie.component.search.support;

import com.richie.testing.elasticsearch.ElasticsearchContainerSupport;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

public final class SearchIntegrationTestSupport {

    private static final DockerImageName ES_IMAGE =
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.4.1");
    private static final String UNAVAILABLE_MESSAGE =
            "Elasticsearch 集成测试需要 Docker（Testcontainers）。CI 请设置 IT_REQUIRE_DOCKER=true。";

    private static final ElasticsearchContainerSupport DELEGATE = ElasticsearchContainerSupport.resolve(
            ES_IMAGE,
            UNAVAILABLE_MESSAGE,
            "SEARCH");

    private SearchIntegrationTestSupport() {
    }

    public static SearchIntegrationTestSupport getInstance() {
        return Holder.INSTANCE;
    }

    public static boolean isEnabled() {
        return DELEGATE.isAvailable();
    }

    public void registerElasticsearchProperties(DynamicPropertyRegistry registry) {
        List<String> pairs = new ArrayList<>();
        appendPropertyPairs(pairs);
        pairs.forEach(pair -> {
            int eq = pair.indexOf('=');
            registry.add(pair.substring(0, eq), () -> pair.substring(eq + 1));
        });
    }

    void appendPropertyPairs(List<String> pairs) {
        DELEGATE.appendConnectionPropertyPairs(pairs);
    }

    private static final class Holder {
        private static final SearchIntegrationTestSupport INSTANCE = new SearchIntegrationTestSupport();
    }
}
