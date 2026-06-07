package com.richie.component.messaging.filter.datasource.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.component.messaging.event.MessageEvent;
import com.richie.component.messaging.filter.datasource.DatasourceHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;


/**
 * Redis 缓存处理器类
 * <p>
 * 使用 Redis SET NX 原子操作实现幂等去重，无需分布式锁。
 * <p>
 * 工作原理：
 * 1. 使用 {@link com.richie.component.cache.ops.ValueOps#setIfAbsent(String, String, long) GlobalCache.value().setIfAbsent} 方法（基于 Redis SET NX）
 * 2. SET NX 是原子操作，确保并发场景下只有一个实例能成功写入
 * 3. 如果返回 false，说明消息已存在（重复），应该跳过处理
 * 4. 如果返回 true，说明消息不存在（首次），可以继续处理
 * <p>
 * 并发场景处理：
 * - 两个消费者实例同时收到同一条消息
 * - 两个实例都调用 `setIfAbsent`（原子操作）
 * - 只有一个实例能成功写入（返回 true），另一个返回 false
 * - 返回 false 的实例会抛出异常，消息被标记为重复，跳过处理
 * <p>
 * 为什么不需要分布式锁？
 * - Redis 的 SET NX 命令本身就是原子操作
 * - 在 Redis 服务器端执行，保证原子性
 * - 比分布式锁更轻量、性能更好
 *
 * @author richie696
 * @version 2.0
 * @since 2022-09-16 18:15:03
 */
@Slf4j
@Service("redisStoreHandler")
public class RedisDatasourceHandlerImpl implements DatasourceHandler {

    /**
     * 默认构造函数（供 Spring 使用）。
     */
    public RedisDatasourceHandlerImpl() {
    }

    /**
     * 检查当前消息是否已在 Redis 中存在（重复消息）。
     *
     * @param message 待检测的消息
     * @return true 表示重复，false 表示未存在
     */
    @Override
    public boolean isDuplicate(Message<MessageEvent> message) {
        var key = getCacheKey(message);
        return GlobalCache.key().hasKey(key);
    }

    /**
     * 保存消息到缓存（原子操作，无需分布式锁）
     * <p>
     * 使用 Redis SET NX 原子操作，确保并发场景下只有一个实例能成功写入。
     * <p>
     * 实现原理：
     * - 使用 {@link com.richie.component.cache.ops.ValueOps#setIfAbsent(String, String, long) GlobalCache.value().setIfAbsent} 方法
     * - 底层调用 Redis 的 `SET key value NX EX timeout` 命令
     * - SET NX 是原子操作，在 Redis 服务器端执行，保证原子性
     * <p>
     * 并发场景示例：
     * 1. 消费者实例A和B同时收到同一条消息（messageId: msg-123）
     * 2. 实例A调用 `setIfAbsent("key", "1", 120000)` → 返回 true（成功写入）
     * 3. 实例B调用 `setIfAbsent("key", "1", 120000)` → 返回 false（key已存在）
     * 4. 实例A继续处理消息（返回 true）
     * 5. 实例B跳过处理（返回 false）
     * <p>
     * 优势：
     * - 无需分布式锁，性能更好
     * - Redis SET NX 是原子操作，保证并发安全
     * - 代码更简洁，逻辑更清晰
     * - 返回 boolean 值，调用方可根据返回值决定是否继续处理
     *
     * @param message 待保存的消息
     * @param expired 过期时间（单位：毫秒）
     * @return 返回保存结果（true：成功保存，消息首次处理；false：消息已存在，重复消息）
     */
    @Override
    public boolean saveCache(Message<MessageEvent> message, long expired) {
        var key = getCacheKey(message);
        
        // 使用原子操作 SET NX，无需分布式锁
        // 如果 key 不存在，则设置并返回 true；如果 key 已存在，则返回 false
        // 底层实现：Redis SET key value NX EX timeout（原子操作）
        boolean success = GlobalCache.value().setIfAbsent(key, "1", expired);
        
        if (!success) {
            // 消息已存在（重复），返回 false
            if (log.isDebugEnabled()) {
                log.debug("消息已存在，无法重复保存。key: {}, messageId: {}, topic: {}", 
                        key, message.getPayload().getMessageId(), message.getPayload().getTopic());
            }
            return false;
        }
        
        // 成功写入，消息标记为已处理
        if (log.isTraceEnabled()) {
            log.trace("消息已保存到缓存。key: {}, messageId: {}, topic: {}, expired: {}ms", 
                    key, message.getPayload().getMessageId(), message.getPayload().getTopic(), expired);
        }
        return true;
    }

    /**
     * 清除消息对应的 Redis 缓存（处理失败或重试前调用）。
     *
     * @param message 待清除的消息
     */
    @Override
    public void clearCache(Message<MessageEvent> message) {
        var key = getCacheKey(message);
        GlobalCache.key().removeCache(key);
        if (log.isTraceEnabled()) {
            log.trace("消息缓存已清除。key: {}, messageId: {}", key, message.getPayload().getMessageId());
        }
    }

}
