package com.richie.testing.redis;

/** 组件 Redis 集测支撑的统一访问面，供 {@link AbstractRedisIntegrationTestBase} 使用。 */
public interface RedisIntegrationTestAccess {

    boolean isEnabled();

    boolean isExternal();

    void appendPropertyPairs(java.util.List<String> pairs);
}
