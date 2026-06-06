package com.richie.testing.local;

import com.richie.testing.redis.RedisContainerSupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalComposeDefaultsTest {

    @Test
    void externalConnection_shouldEmitRedisUrlWithPort() {
        List<String> pairs = new ArrayList<>();
        RedisContainerSupport.externalConnection("localhost", 16379, null, 15, "msg", "STORAGE")
                .appendConnectionPropertyPairs(pairs);

        assertThat(pairs).contains(
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=16379",
                "spring.data.redis.url=redis://localhost:16379",
                "spring.data.redis.database=15");
    }

    @Test
    void localComposePorts_matchDockerScripts() {
        assertThat(LocalComposeDefaults.REDIS_PORT).isEqualTo(16379);
        assertThat(LocalComposeDefaults.MYSQL_PORT).isEqualTo(53366);
        assertThat(LocalComposeDefaults.MYSQL_DATABASE).isEqualTo("platform");
    }
}
