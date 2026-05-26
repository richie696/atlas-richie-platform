package com.richie.component.mqtt.filter.datasource.impl;

import com.richie.component.mqtt.filter.datasource.DatasourceHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.management.openmbean.KeyAlreadyExistsException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存数据库处理器类
 * <p>
 * 基于内存实现的消息去重处理器，用于MQTT消息的幂等性控制。
 * 使用ConcurrentHashMap存储已处理的消息标识，避免重复处理相同的消息。
 * 适用于单机环境或消息量较小的场景。
 *
 * @author richie696
 * @version 2.0
 * @since 2022-09-16 18:15:29
 */
@Slf4j
@Service("mqttMemoryStoreHandler")
public class MemoryDatasourceHandlerImpl implements DatasourceHandler {

    /**
     * 构造内存数据源处理器
     * <p>
     * 使用默认构造函数，由Spring容器管理实例。
     */
    public MemoryDatasourceHandlerImpl() {
    }

    /**
     * 内存数据库
     * <p>
     * 使用ConcurrentHashMap存储消息标识和过期时间，key为消息哈希值，value为过期时间戳。
     */
    private static final ConcurrentMap<String, Long> MEMORY_DB = new ConcurrentHashMap<>(10);

    @Override
    public boolean isDuplicate(String hash) {
        if (hash == null || hash.isEmpty()) {
            return true;
        }
        return MEMORY_DB.containsKey(getCacheKey(hash));
    }

    @Override
    public void saveCache(String hash, long expired) {
        if (hash == null || hash.isEmpty()) {
            return;
        }
        String key = getCacheKey(hash);
        if (MEMORY_DB.containsKey(key)) {
            throw new KeyAlreadyExistsException("当前数据已存在，无法重复保存。");
        }
        MEMORY_DB.put(key, System.currentTimeMillis() + expired);
    }

    @Scheduled(cron = "0/2 * * * * ? ")
    private void clearTimeout() {
//        log.debug("执行了MEMORY_DB的clearTimeout定时任务");
        long currentTime = System.currentTimeMillis();
        MEMORY_DB.forEach((key, expired) -> {
            if (currentTime >= expired) {
                MEMORY_DB.remove(key, expired);
                log.debug("当前 {} 消息已过期。", key);
            }
        });
    }

}
