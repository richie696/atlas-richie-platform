package com.richie.component.dao.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import java.util.List;

/**
 * 数据源监听器：订阅 Redisson 主题，接收“添加租户”消息并回调 TenantOperator 刷新本地数据源。
 *
 * @author yuy
 * @version 1.0
 * @since 2023-10-05
 */
@Slf4j
@RequiredArgsConstructor
public class DataSourceListener {

    /** 多租户配置（含监听 topic 等） */
    private final TenantProperties tenantProperties;

    /** Redisson 客户端，用于订阅添加租户消息 */
    private final RedissonClient redissonClient;

    /** 租户操作类，收到消息后执行添加租户/数据源 */
    private final TenantOperator tenantOperator;

    /**
     * 监听添加数据源
     */
    public void listen() {
        new Thread(() -> {
            RTopic topic = redissonClient.getTopic(tenantProperties.getAddTenantTopic());
            topic.addListenerAsync(List.class, (channel, msg) -> {
                //log.debug("DataSourceListener msg:{}", JsonUtils.getInstance().serialize(msg));
                tenantOperator.listenAddTenant(msg);
            });
            log.info("DataSourceListener init");
        }).start();
    }

}
