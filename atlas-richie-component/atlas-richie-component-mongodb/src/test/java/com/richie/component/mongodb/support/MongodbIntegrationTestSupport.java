package com.richie.component.mongodb.support;

import com.richie.testing.mongo.MongoContainerSupport;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;

public final class MongodbIntegrationTestSupport {

    private static final DockerImageName MONGO_IMAGE = DockerImageName.parse("mongo:7.0");
    private static final String UNAVAILABLE_MESSAGE =
            "MongoDB 集成测试需要 Docker（Testcontainers）。请安装并启动 Docker 后执行 mvn verify；"
                    + "CI 请设置 IT_REQUIRE_DOCKER=true。";

    private static final MongoContainerSupport DELEGATE = MongoContainerSupport.resolve(
            MONGO_IMAGE,
            UNAVAILABLE_MESSAGE,
            "MONGODB");

    private MongodbIntegrationTestSupport() {
    }

    public static MongodbIntegrationTestSupport getInstance() {
        return Holder.INSTANCE;
    }

    public static boolean isEnabled() {
        return DELEGATE.isAvailable();
    }

    public boolean isExternal() {
        return DELEGATE.isExternal();
    }

    public void registerMongoProperties(DynamicPropertyRegistry registry) {
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
        private static final MongodbIntegrationTestSupport INSTANCE = new MongodbIntegrationTestSupport();
    }
}
