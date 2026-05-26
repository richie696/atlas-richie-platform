package com.richie.component.dao.config;

import org.redisson.config.Config;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.stereotype.Component;

/**
 * Redisson 自动配置定制器，集群模式下关闭 slot 覆盖检查以兼容部分 Redis 集群环境。
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-24
 */
@Component
public class RedissonConfig implements RedissonAutoConfigurationCustomizer {
    @Override
    public void customize(Config configuration) {
        if (configuration.isClusterConfig()) {
            configuration.useClusterServers().setCheckSlotsCoverage(false);
        }
    }
}
