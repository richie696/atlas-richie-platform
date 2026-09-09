package cn.richie696.component.cache.redis.config.base;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisBaseAutoConfigurationTest {

    @Test
    void redisSerializerRoundTripsTypedCacheValues() {
        GenericJacksonJsonRedisSerializer serializer = new RedisBaseAutoConfiguration().redisValueSerializer();
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("employee", "E001");
        original.put("stores", List.of("SH001", "SH002"));

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored).isEqualTo(original);
    }
}
