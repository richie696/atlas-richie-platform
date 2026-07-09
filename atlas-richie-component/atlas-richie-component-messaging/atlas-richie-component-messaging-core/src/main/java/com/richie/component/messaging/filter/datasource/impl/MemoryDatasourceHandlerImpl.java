/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.messaging.filter.datasource.impl;

import com.richie.component.messaging.event.MessageEvent;
import com.richie.component.messaging.filter.datasource.DatasourceHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 内存数据库处理器类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-16 18:15:29
 */
@Slf4j
@Service("memoryStoreHandler")
public class MemoryDatasourceHandlerImpl implements DatasourceHandler {

    /**
     * 内存数据库（key -> 过期时间戳）
     */
    private static final ConcurrentMap<String, Long> MEMORY_DB = new ConcurrentHashMap<>(10);

    /**
     * 默认构造函数（供 Spring 使用）。
     */
    public MemoryDatasourceHandlerImpl() {
    }

    /**
     * 判断当前消息是否是重复消息
     *
     * @param message 待检测的消息
     * @return 返回检测结果（true：是重复消息，false：不是重复消息）
     */
    @Override
    public boolean isDuplicate(Message<MessageEvent> message) {
        return MEMORY_DB.containsKey(getCacheKey(message));
    }

    /**
     * 保存消息数据（原子操作，用于幂等去重）
     * <p>
     * 注意：内存实现使用 ConcurrentHashMap，put 操作是线程安全的，但检查+写入不是原子操作。
     * 在高并发场景下，建议使用 Redis 实现（RedisDatasourceHandlerImpl）。
     *
     * @param message   带保存的消息
     * @param expired 该消息的过期时间（单位：毫秒）
     * @return 返回保存结果（true：成功保存，消息首次处理；false：消息已存在，重复消息）
     */
    @Override
    public boolean saveCache(Message<MessageEvent> message, long expired) {
        var key = getCacheKey(message);
        // 使用 putIfAbsent 实现原子操作（如果 key 不存在则插入，返回 null；如果已存在则返回旧值）
        Long previousValue = MEMORY_DB.putIfAbsent(key, System.currentTimeMillis() + expired);
        if (previousValue != null) {
            // 消息已存在（重复），返回 false
            if (log.isDebugEnabled()) {
                log.debug("消息已存在，无法重复保存。key: {}, messageId: {}", key, message.getPayload().getMessageId());
        }
            return false;
        }
        // 成功写入，消息标记为已处理
        if (log.isTraceEnabled()) {
            log.trace("消息已保存到缓存。key: {}, messageId: {}, expired: {}ms", key, message.getPayload().getMessageId(), expired);
        }
        return true;
    }

    /**
     * 清除缓存
     *
     * @param message 待清除的消息
     */
    @Override
    public void clearCache(Message<MessageEvent> message) {
        var key = getCacheKey(message);
        MEMORY_DB.remove(key);
    }

    /**
     * 定时清理过期数据（每 10 秒执行一次）。
     */
    @Scheduled(cron = "0/10 * * * * ? ")
    public void clearTimeout() {
        var currentTime = System.currentTimeMillis();
        MEMORY_DB.forEach((key, expired) -> {
            if (currentTime >= expired) {
                MEMORY_DB.remove(key, expired);
                log.debug("当前 {} 消息已过期。", key);
            }
        });
    }

}
